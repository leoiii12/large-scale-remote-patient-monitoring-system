package com.fh.sessionization

import com.fh.*
import com.fh.avro.*
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.state.Stores
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

data class MaxMin(val max: Long, val min: Long)

val maxMinCreditsByUnit = mapOf(
        Pair("HeartRate/Bpm", MaxMin(20, 1)),
        Pair("StepCount/Count", MaxMin(40, 1)),
        Pair("BodyTemperature/C", MaxMin(20, 1)),
        Pair("BloodPressure.Systolic/mmHg", MaxMin(6, 1)),
        Pair("BloodPressure.Diastolic/mmHg", MaxMin(6, 1)),
        Pair("Respiratory/Bpm", MaxMin(10, 1)),
        Pair("BloodGlucose/mmol_L", MaxMin(1, 1)),
        Pair("PeakExpiratoryFlowRate/L_min", MaxMin(5, 1)),
        Pair("ForcedExpiratoryVolume/Litres", MaxMin(40, 1))
)

internal val processorId = AtomicInteger(0)
internal val transformerId = AtomicInteger(0)

fun main(args: Array<String>) {
    val props = getProps("fh-analytics-sessionization").also { println(it) }

    runBlocking {
        waitTopics(props)
    }

    val numOfRecords = AtomicInteger()

    val patientIdUnitSerde = getAvroSerde<AvroPatientIdUnitSessionId>()
    val sessionAggregatorSerde = getAvroSerde<AvroSessionAggregator>()

    // Init the state store
    val patientIdUnitSessionAggregatorKVStoreBuilder = Stores
            .keyValueStoreBuilder(Stores.persistentKeyValueStore("PatientIdUnit-SessionAggregator"), patientIdUnitSerde, sessionAggregatorSerde)
            .withCachingEnabled()

    val builder = StreamsBuilder()

    builder
            .addStateStore(patientIdUnitSessionAggregatorKVStoreBuilder)

    builder
            .stream<ByteArray?, AvroAlert>("fun-health-alerts")
            .mapValues { _, value ->
                numOfRecords.incrementAndGet()

                return@mapValues value
            }
            .process(AlertProcessorSupplier(patientIdUnitSessionAggregatorKVStoreBuilder.name()), patientIdUnitSessionAggregatorKVStoreBuilder.name())

    builder
            .stream<ByteArray?, AvroSensorRecord>("fun-health-sensor-records")
            .transform(AggregatedRecordTransformerSupplier(patientIdUnitSessionAggregatorKVStoreBuilder.name()), patientIdUnitSessionAggregatorKVStoreBuilder.name())
            .filter { _, value -> value!!.count > 0 }
            .map { key, value ->
                if (key == null) throw NullPointerException(key)
                if (value == null) throw NullPointerException(value)

                numOfRecords.incrementAndGet()

                return@map KeyValue<ByteArray?, AvroAggregatedRecord>(
                        null,
                        AvroAggregatedRecord(
                                Utils.getByteBufferFromUUID(UUID.randomUUID()),
                                key.patientId,
                                value.timestamp,
                                key.unit,
                                value.sum / value.count,
                                value.min,
                                value.max
                        )
                )
            }
            .to("fun-health-aggregated-records")

    startStreams(builder, props) { streams: KafkaStreams -> streams.initMetrics(numOfRecords) }
}
