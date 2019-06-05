package com.fh.throughput

import com.fh.avro.AvroAlert
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
    val props = getProps("fh-throughput-alerts").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecords = AtomicInteger()
    val latestTimestamp = AtomicLong()
    val maxLatency = AtomicLong()

    val builder = StreamsBuilder()

    builder
            .stream<ByteArray?, AvroAlert>("fun-health-alerts")
            .foreach { _, a ->
                numOfRecords.incrementAndGet()

                latestTimestamp.getAndUpdate { t -> if (a.timestamp > t) a.timestamp else t; }
                maxLatency.getAndUpdate { t ->
                    val latency = System.currentTimeMillis() - latestTimestamp.get()
                    return@getAndUpdate if (latency > t) latency else t
                }
            }

    startStreams(builder, props) { streams ->
        fixedRateTimer(name = "metricsTimer", initialDelay = 1000 * 10, period = 1000 * 5) {
            println("${Instant.now()}, " +
                    "state=${streams.state()}, " +
                    "numOfRecords=${numOfRecords.getAndSet(0)}," +
                    "latestTimestamp=$latestTimestamp," +
                    "maxLatency=${maxLatency.getAndSet(0)}")

            if (streams.state() == KafkaStreams.State.NOT_RUNNING) {
                println("Terminating in 5 seconds because of streams.state() == KafkaStreams.State.NOT_RUNNING...")

                this.cancel()

                System.exit(1)
            }
        }
    }
}