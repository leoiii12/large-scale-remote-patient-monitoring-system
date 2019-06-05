package com.fh

import com.conversantmedia.util.concurrent.MultithreadConcurrentQueue
import com.fh.avro.AvroHubRecord
import com.fh.avro.AvroHubRecordBatch
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class Endpoint {

    private val bufferedHubRecords = MultithreadConcurrentQueue<AvroHubRecord>(config.MAX_NUM_OF_HUB_RECORDS)
    private val logger = LoggerFactory.getLogger(Endpoint::class.java)

    init {
        initProducer()
    }

    fun submit(hubRecord: AvroHubRecord) {
        bufferedHubRecords.offer(hubRecord)
    }

    private suspend fun blockUntilTopicExists(topicName: String) {
        while (true) {
            try {
                val adminProps = Properties().also {
                    it[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = config.KAFKA_URL
                }

                val names = AdminClient.create(adminProps).use {
                    it.listTopics().names().get(30, TimeUnit.SECONDS)
                }

                if (names.contains(topicName)) {
                    break;
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            delay(1000);
        }

        delay(1000 * 30)
    }

    private fun createProducer(): Producer<String, AvroHubRecordBatch> {
        val props = Properties()

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.KAFKA_URL)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java.name)
        props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, config.SCHEMA_REGISTRY_URL)

        return KafkaProducer(props)
    }

    private fun initProducer() {
        thread {
            runBlocking {
                blockUntilTopicExists(config.TOPIC);
            }

            var producer = createProducer()
            var numOfErrors = 0

            while (true) {
                val hubRecords = pools.hubRecordArrayPool.take()

                val numOfRemoved = bufferedHubRecords.remove(hubRecords)
                if (numOfRemoved == 0) {
                    pools.hubRecordArrayPool.restore(hubRecords)

                    if (bufferedHubRecords.isEmpty) {
                        Thread.sleep(50)
                    }

                    continue
                }

                var hubRecordBatch: AvroHubRecordBatch? = null

                try {
                    hubRecordBatch = pools.hubRecordBatchPool.take().apply {
                        batch = hubRecords.asList().subList(0, numOfRemoved)
                    }

                    producer.produce(hubRecordBatch)
                } catch (e: Exception) {
                    logger.error(e.message)

                    if (numOfErrors++ >= 100) {
                        producer.close(10, TimeUnit.SECONDS)

                        numOfErrors = 0
                        producer = createProducer()

                        runBlocking {
                            delay(1000 * 10)
                        }
                    }
                }

                recycleObjs(hubRecordBatch!!, hubRecords)
            }
        }
    }

    private fun Producer<String, AvroHubRecordBatch>.produce(hubRecordBatch: AvroHubRecordBatch) {
        send(ProducerRecord<String, AvroHubRecordBatch>(config.TOPIC, hubRecordBatch))
                .get()
                .run {
                    hubRecordBatch.batch.flatMap { hr -> hr.records }.count().also { numOfHubRecords -> numOfSubmittedSensorRecords.updateAndGet { it + numOfHubRecords } }
                }
    }

    private fun recycleObjs(hubRecordBatch: AvroHubRecordBatch, hubRecordArray: Array<AvroHubRecord?>) {
        for (sensorRecord in hubRecordBatch.batch.flatMap { it.records }) {
            pools.sensorRecordPool.restore(sensorRecord)
        }

        for (hubRecord in hubRecordBatch.batch) {
            pools.hubRecordPool.restore(hubRecord.apply { records.clear() })
        }

        pools.hubRecordBatchPool.restore(hubRecordBatch.apply { batch = null })
        pools.hubRecordArrayPool.restore(hubRecordArray)
    }

}