package com.fh.minutely

import com.fh.*
import com.fh.avro.AvroAggregatedRecord
import com.fh.avro.AvroPatientIdUnitSessionId
import com.fh.avro.AvroSessionizedSensorRecord
import com.fh.avro.AvroSumCountMaxMin
import kotlinx.coroutines.runBlocking
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.*
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

val SHORT_UNITS = hashSetOf("StepCount/Count", "HeartRate/Bpm", "BodyTemperature/C", "Respiratory/Bpm", "ForcedExpiratoryVolume/Litres", "BloodPressure.Systolic/mmHg", "BloodPressure.Diastolic/mmHg")
val LONG_UNITS = hashSetOf("PeakExpiratoryFlowRate/L_min", "BloodGlucose/mmol_L")

fun main(args: Array<String>) {
    val props = getProps("fh-analytics-minutely").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecords = AtomicInteger()

    val builder = StreamsBuilder()

    val avroPatientIdUnitSessionIdSerde = getAvroSerde<AvroPatientIdUnitSessionId>()
    val avroSessionizedSensorRecordSerde = getAvroSerde<AvroSessionizedSensorRecord>()
    val avroSumCountMaxMinSerde = getAvroSerde<AvroSumCountMaxMin>()

    val substreams = builder
            .stream<AvroPatientIdUnitSessionId, AvroSessionizedSensorRecord>("fun-health-sessionized-sensor-records", Consumed.with(avroPatientIdUnitSessionIdSerde, avroSessionizedSensorRecordSerde, { record, _ -> (record.value() as AvroSessionizedSensorRecord).timestamp }, Topology.AutoOffsetReset.LATEST))
            .mapValues { _, value -> value.value }
            .branch(Predicate { key, _ -> SHORT_UNITS.contains(key.unit) }, Predicate { key, _ -> LONG_UNITS.contains(key.unit) }, Predicate { key, value -> true })

    val shortStream = substreams[0]
    val longStream = substreams[1]
    val trueStream = substreams[2]

    trueStream

            .foreach { _, value -> println(value) }

    shortStream

            .groupByKey(Grouped.with(avroPatientIdUnitSessionIdSerde, Serdes.Double()))
            .windowedBy(SessionWindows.with(Duration.ofSeconds(40)).grace(Duration.ofSeconds(40)))
            .aggregate<AvroSumCountMaxMin>(
                    {
                        AvroSumCountMaxMin(0.0, 0, Double.MIN_VALUE, Double.MAX_VALUE, 0)
                    },
                    { _, value, sumCountMaxMin ->
                        sumCountMaxMin.apply {
                            this.sum += value
                            this.count += 1

                            if (value > this.max) this.max = value
                            if (value < this.min) this.min = value
                        }
                    },
                    { aggKey, aggOne, aggTwo ->
                        AvroSumCountMaxMin(aggOne.sum + aggTwo.sum, aggOne.count + aggTwo.count, Math.max(aggOne.max, aggTwo.max), Math.min(aggOne.min, aggTwo.min), 0)
                    },
                    Materialized.with(avroPatientIdUnitSessionIdSerde, avroSumCountMaxMinSerde)
            )
            .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
            .toStream()
            .map { windowedPatientIdUnit, sumCountMaxMin ->
                numOfRecords.incrementAndGet()

                val timestamp = (windowedPatientIdUnit.window().start() + windowedPatientIdUnit.window().end()) / 2
                val avgValue = sumCountMaxMin.sum / sumCountMaxMin.count

                return@map KeyValue<ByteArray?, AvroAggregatedRecord>(
                        null,
                        AvroAggregatedRecord(
                                Utils.getByteBufferFromUUID(UUID.randomUUID()),
                                windowedPatientIdUnit.key().patientId,
                                timestamp,
                                windowedPatientIdUnit.key().unit,
                                avgValue,
                                sumCountMaxMin.min,
                                sumCountMaxMin.max
                        ))
            }
            .to("fun-health-aggregated-records")

    longStream

            .groupByKey(Grouped.with(avroPatientIdUnitSessionIdSerde, Serdes.Double()))
            .windowedBy(SessionWindows.with(Duration.ofSeconds(70)).grace(Duration.ofSeconds(70)))
            .aggregate<AvroSumCountMaxMin>(
                    {
                        AvroSumCountMaxMin(0.0, 0, Double.MIN_VALUE, Double.MAX_VALUE, 0)
                    },
                    { _, value, sumCountMaxMin ->
                        sumCountMaxMin.apply {
                            this.sum += value
                            this.count += 1

                            if (value > this.max) this.max = value
                            if (value < this.min) this.min = value
                        }
                    },
                    { aggKey, aggOne, aggTwo ->
                        AvroSumCountMaxMin(aggOne.sum + aggTwo.sum, aggOne.count + aggTwo.count, Math.max(aggOne.max, aggTwo.max), Math.min(aggOne.min, aggTwo.min), 0)
                    },
                    Materialized.with(avroPatientIdUnitSessionIdSerde, avroSumCountMaxMinSerde)
            )
            .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
            .toStream()
            .map { windowedPatientIdUnit, sumCountMaxMin ->
                numOfRecords.incrementAndGet()

                val timestamp = (windowedPatientIdUnit.window().start() + windowedPatientIdUnit.window().end()) / 2
                val avgValue = sumCountMaxMin.sum / sumCountMaxMin.count

                return@map KeyValue<ByteArray?, AvroAggregatedRecord>(
                        null,
                        AvroAggregatedRecord(
                                Utils.getByteBufferFromUUID(UUID.randomUUID()),
                                windowedPatientIdUnit.key().patientId,
                                timestamp,
                                windowedPatientIdUnit.key().unit,
                                avgValue,
                                sumCountMaxMin.min,
                                sumCountMaxMin.max
                        ))
            }
            .to("fun-health-aggregated-records")

    startStreams(builder, props) { streams: KafkaStreams -> streams.initMetrics(numOfRecords) }
}
