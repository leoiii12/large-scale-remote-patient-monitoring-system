package com.fh.etl

import com.fh.*
import com.fh.avro.AvroHubRecordBatch
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val props = getProps("fh-analytics-etl").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecords = AtomicInteger()

    val builder = StreamsBuilder()

    builder
            .stream<ByteArray?, AvroHubRecordBatch>("fun-health-hub-record-batches")
            .flatMap { _, value ->
                val sensorRecords = value.batch.flatMap { b ->
                    b.records.map { r -> KeyValue(Utils.getBytesFromByteBuffer(b.patientId), r) }
                }

                numOfRecords.addAndGet(sensorRecords.size)

                return@flatMap sensorRecords
            }
            .to("fun-health-sensor-records")

    startStreams(builder, props) { streams: KafkaStreams -> streams.initMetrics(numOfRecords) }
}