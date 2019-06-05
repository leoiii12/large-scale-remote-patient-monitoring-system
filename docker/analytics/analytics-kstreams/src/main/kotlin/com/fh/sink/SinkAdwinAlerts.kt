package com.fh.sink

import com.fh.*
import com.fh.avro.AvroAdwinAlert
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val props = getProps("fh-sink-adwin-alerts").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecordsStream = PublishProcessor.create<Int>()
    val numOfRecords = AtomicInteger()

    numOfRecordsStream.subscribe { num -> numOfRecords.addAndGet(num) }

    val builder = StreamsBuilder()

    val adwinAlerts = PublishProcessor.create<AvroAdwinAlert>()

    if ("true".equals(PUSHER_ENABLED, ignoreCase = true)) {
        PusherBroadcaster().also {
            it.initNewAdwinAlerts(adwinAlerts, numOfRecordsStream)
        }
    }
    if ("true".equals(CRATE_DB_ENABLED, ignoreCase = true)) {
        CrateDBPersistor().also {
            it.initNewAdwinAlerts(adwinAlerts, numOfRecordsStream)
        }
    }

    builder
            .stream<ByteArray?, AvroAdwinAlert>("fun-health-adwin-alerts")
            .foreach { _, value ->
                adwinAlerts.offer(value)
            }

    startStreams(builder, props) { streams: KafkaStreams -> streams.initMetrics(numOfRecords) }
}
