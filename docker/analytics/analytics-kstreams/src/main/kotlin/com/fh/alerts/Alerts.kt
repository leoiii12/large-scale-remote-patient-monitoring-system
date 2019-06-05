package com.fh.alerts

import com.fh.*
import com.fh.avro.AvroAlert
import com.fh.avro.AvroSensorRecord
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val props = getProps("fh-analytics-alerts").also { println(it) }
    val normalRange = NormalRange()

    runBlocking {
        waitTopics(props)
    }

    val numOfRecords = AtomicInteger()

    val builder = StreamsBuilder()

    builder
            .stream<ByteArray?, AvroSensorRecord>("fun-health-sensor-records")

            .filter { _, value -> !normalRange.isInRange(value.unit, value.value) }
            .mapValues { readOnlyKey, value ->
                numOfRecords.incrementAndGet()

                return@mapValues AvroAlert(
                        Utils.getByteBufferFromUUID(UUID.randomUUID()),
                        ByteBuffer.wrap(readOnlyKey),
                        value.timestamp,
                        value.unit,
                        value.value)
            }

            .to("fun-health-alerts")

    startStreams(builder, props) { streams: KafkaStreams -> streams.initMetrics(numOfRecords) }
}

