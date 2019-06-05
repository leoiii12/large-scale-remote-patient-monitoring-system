package com.fh.sink

import com.fh.*
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.runBlocking
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

fun main(args: Array<String>) {
    val props = getProps("fh-sink-global-averages").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecordsStream = PublishProcessor.create<Int>()
    val numOfRecords = AtomicInteger()

    numOfRecordsStream.subscribe { num -> numOfRecords.addAndGet(num) }

    val builder = StreamsBuilder()

    val globalAverages = PublishProcessor.create<GlobalAverage>()

    if ("true".equals(CRATE_DB_ENABLED, ignoreCase = true)) {
        CrateDBPersistor().also {
            it.initGlobalAverages(globalAverages, numOfRecordsStream)
        }
    }

    val pattern = Pattern.compile("\\[(.*?)@(.*?)/(.*?)]$")

    builder
            .stream<String, Double>("fun-health-global-averages", Consumed.with(Serdes.String(), Serdes.Double()))
            .foreach { key, value ->
                val matcher = pattern.matcher(key)
                if (matcher.matches() == false) {
                    return@foreach;
                }

                val unit = matcher.group(1)
                val start = matcher.group(2)
                val end = matcher.group(3)

                println("unit=$unit, start=$start, end=$end")

                globalAverages.offer(GlobalAverage(unit, value, start.toLong()))
            }

    startStreams(builder, props) { streams: KafkaStreams -> streams.initMetrics(numOfRecords) }
}
