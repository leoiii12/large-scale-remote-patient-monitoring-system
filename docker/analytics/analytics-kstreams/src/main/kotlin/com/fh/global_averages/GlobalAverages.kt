package com.fh.global_averages

import com.fh.*
import com.fh.avro.AvroSensorRecord
import com.fh.avro.AvroSumCount
import kotlinx.coroutines.runBlocking
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig.unbounded
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val props = getProps("fh-analytics-global-averages").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecords = AtomicInteger()


    val builder = StreamsBuilder()
    val avroSumCountSerde = getAvroSerde<AvroSumCount>()

    builder
            .stream<ByteArray?, AvroSensorRecord>("fun-health-sensor-records", Consumed.with { record, _ -> (record.value() as AvroSensorRecord).timestamp })

            .map { _, value -> KeyValue<String, Double>(value.unit, value.value) }
            .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
            .windowedBy(TimeWindows.of(Duration.ofMinutes(1)).grace(Duration.ofSeconds(30)))
            .aggregate<AvroSumCount>(
                    {
                        AvroSumCount(0.0, 0)
                    },
                    { _, value, aggregate ->
                        aggregate.apply {
                            this.sum += value
                            this.count += 1
                        }
                    },
                    Materialized.with(Serdes.String(), avroSumCountSerde)
            )
            .suppress(Suppressed.untilWindowCloses(unbounded()))

            .toStream()
            .map { key, value ->
                numOfRecords.incrementAndGet()
                return@map KeyValue<String, Double>(key.toString(), value.sum * 1.0 / value.count)
            }

            .to("fun-health-global-averages", Produced.with(Serdes.String(), Serdes.Double()))

    startStreams(builder, props) { streams: KafkaStreams -> streams.initMetrics(numOfRecords, 1000 * 60, 5) }
}
