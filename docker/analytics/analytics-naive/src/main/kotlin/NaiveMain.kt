package com.fh.naive

import com.conversantmedia.util.concurrent.MultithreadConcurrentQueue
import com.fh.avro.AvroHubRecordBatch
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.io.File
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread

data class LatencyRecord(val date: Date, val latency: Long) {
}

private fun createConsumer(): Consumer<String, AvroHubRecordBatch> {
    val props = Properties()

    val kafkaUrl = System.getenv("KAFKA_URL") ?: "kafka:9092"
    val groupId = System.getenv("GROUP_ID") ?: "00000000-0000-0000-0000-000000000000"
    val schemaRegistry = System.getenv("SCHEMA_REGISTRY_URL") ?: "http://schema-registry:8081"

    println(kafkaUrl)
    println(groupId)
    println(schemaRegistry)

    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer::class.java.name)
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
    props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistry)

    return KafkaConsumer<String, AvroHubRecordBatch>(props)
}

fun main(args: Array<String>) {
    val queue = MultithreadConcurrentQueue<LatencyRecord>(1000)


    for (i in 1..4) {
        startConsuming(queue)
    }

    thread {
        startWriting(queue)
    }

    Thread.currentThread().join();
}

fun startConsuming(queue: MultithreadConcurrentQueue<LatencyRecord>) {
    thread {
        val consumer = createConsumer().also { consumer -> consumer.subscribe(listOf("fun-health-hub-record-batches")) }

        while (true) {
            val consumerRecords = consumer.poll(Duration.ofSeconds(1))
            if (consumerRecords.count() <= 0) continue

            val earliestSensorRecord = consumerRecords
                    .flatMap { cr -> cr.value().batch }
                    .flatMap { hr -> hr.records }
                    .minBy { sr -> sr.timestamp }

            earliestSensorRecord?.run {
                val earliestDate = Date(earliestSensorRecord.timestamp)

                val currentDate = Date()
                val latency = currentDate.time - earliestDate.time

                queue.offer(LatencyRecord(currentDate, latency))
            }
        }

    }
}

fun startWriting(queue: MultithreadConcurrentQueue<LatencyRecord>) {
    val file = File("logs/log.txt")

    file.parentFile.mkdirs()
    file.createNewFile()

    file.bufferedWriter().use { out ->
        val records = arrayOfNulls<LatencyRecord>(100)

        while (true) {
            if (queue.isEmpty) {
                Thread.sleep(30)
            }

            val numOfRecords = queue.remove(records)

            val latencyRecord = records.asList().subList(0, numOfRecords).maxBy { r -> r!!.latency }

            out.write("$latencyRecord\n")
        }
    }
}
