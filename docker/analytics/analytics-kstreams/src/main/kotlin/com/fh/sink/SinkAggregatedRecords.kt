package com.fh.sink

import com.fh.*
import com.fh.avro.AvroAggregatedRecord
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val props = getProps("fh-sink-aggregated-records").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecordsStream = PublishProcessor.create<Int>()
    val numOfRecords = AtomicInteger()

    numOfRecordsStream.subscribe { num -> numOfRecords.addAndGet(num) }

    val builder = StreamsBuilder()

    val aggregatedRecords = PublishProcessor.create<AvroAggregatedRecord>()

    if ("true".equals(PUSHER_ENABLED, ignoreCase = true)) {
        PusherBroadcaster().also {
            it.initNewRecords(aggregatedRecords, numOfRecordsStream)
        }
    }
    if ("true".equals(CRATE_DB_ENABLED, ignoreCase = true)) {
        CrateDBPersistor().also {
            it.initNewRecords(aggregatedRecords, numOfRecordsStream)
        }
    }

    builder
            .stream<ByteArray?, AvroAggregatedRecord>("fun-health-aggregated-records")
            .foreach { _, value ->
                aggregatedRecords.offer(value)
            }

    startStreams(builder, props) { streams: KafkaStreams -> streams.initMetrics(numOfRecords) }
}
