package com.fh.throughput

import com.fh.avro.AvroAggregatedRecord
import com.fh.getProps
import com.fh.startStreams
import com.fh.waitTopics
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.fixedRateTimer

fun main(args: Array<String>) {
    val props = getProps("fh-throughput-aggregated-records").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecords = AtomicInteger()
    val latestTimestamp = AtomicLong()

    val builder = StreamsBuilder()

    builder
            .stream<ByteArray?, AvroAggregatedRecord>("fun-health-aggregated-records")
            .foreach { _, ar ->
                numOfRecords.incrementAndGet()
                latestTimestamp.getAndUpdate { t -> if (ar.timestamp > t) ar.timestamp else t; }
            }

    startStreams(builder, props) { streams ->
        fixedRateTimer(name = "metricsTimer", initialDelay = 1000 * 10, period = 1000 * 5) {
            println("${Instant.now()}, state=${streams.state()}, numOfRecords=${numOfRecords.getAndSet(0)}, latestTimestamp=$latestTimestamp, latestLatency=${System.currentTimeMillis() - latestTimestamp.get()}")

            if (streams.state() == KafkaStreams.State.NOT_RUNNING) {
                println("Terminating in 5 seconds because of streams.state() == KafkaStreams.State.NOT_RUNNING...")

                this.cancel()

                System.exit(1)
            }
        }
    }
}