package com.fh.sessionization

import com.fh.avro.AvroPatientIdUnit
import com.fh.avro.AvroSensorRecord
import com.fh.avro.AvroSessionAggregator
import com.fh.avro.AvroSumCountMaxMin
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.Transformer
import org.apache.kafka.streams.kstream.TransformerSupplier
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import java.nio.ByteBuffer

class AggregatedRecordTransformerSupplier : TransformerSupplier<ByteArray?, AvroSensorRecord, KeyValue<AvroPatientIdUnit?, AvroSumCountMaxMin>> {

    private val patientIdUnitSessionAggregatorKVStoreName: String
    private val defaultSumCountMaxMin = AvroSumCountMaxMin(0.0, 0, Double.MIN_VALUE, Double.MAX_VALUE, 0)

    constructor(patientIdUnitSessionAggregatorKVStoreName: String) {
        this.patientIdUnitSessionAggregatorKVStoreName = patientIdUnitSessionAggregatorKVStoreName
    }

    override fun get(): Transformer<ByteArray?, AvroSensorRecord, KeyValue<AvroPatientIdUnit?, AvroSumCountMaxMin>> {
        return object : Transformer<ByteArray?, AvroSensorRecord, KeyValue<AvroPatientIdUnit?, AvroSumCountMaxMin>> {

            private var kvStore: KeyValueStore<AvroPatientIdUnit, AvroSessionAggregator?>? = null
            private val id = transformerId.getAndIncrement()

            @Suppress("UNCHECKED_CAST")
            override fun init(context: ProcessorContext) {
                kvStore = context.getStateStore(patientIdUnitSessionAggregatorKVStoreName) as KeyValueStore<AvroPatientIdUnit, AvroSessionAggregator?>
            }

            override fun transform(key: ByteArray?, sensorRecord: AvroSensorRecord): KeyValue<AvroPatientIdUnit?, AvroSumCountMaxMin> {
                if (kvStore == null) {
                    throw NullPointerException("kvStore is null.")
                }

                val patientIdUnit = AvroPatientIdUnit(ByteBuffer.wrap(key), sensorRecord.unit)

                // Init sessionAggregator
                kvStore!!.putIfAbsent(patientIdUnit, AvroSessionAggregator(1, 0, 1, 1, 0.0, 0, Double.MIN_VALUE, Double.MAX_VALUE))

                // Retrieve sessionAggregator
                val sessionAggregator = kvStore!!.get(patientIdUnit)!!

                val hasRenewed = sessionAggregator.run {
                    // Shrink Current
                    this.credits -= 1

                    this.sum += sensorRecord.value
                    this.count += 1
                    this.max = Math.max(this.max, sensorRecord.value)
                    this.min = Math.min(this.min, sensorRecord.value)

                    // Renew
                    if (this.credits <= 0L) {
                        this.sessionId += 1

                        // credits <- initialCredits <- renewingCredits
                        this.initialCredits = this.renewingCredits
                        this.credits = this.initialCredits

                        // Extend Next
                        this.renewingCredits += 1

                        // Adjust Next
                        val maxMin = maxMinCreditsByUnit.getValue(sensorRecord.unit)
                        if (this.renewingCredits > maxMin.max) {
                            this.renewingCredits = maxMin.max
                        }

                        return@run true
                    }

                    return@run false
                }

                val sumCountMaxMin = if (hasRenewed)
                    AvroSumCountMaxMin(sessionAggregator.sum, sessionAggregator.count, sessionAggregator.max, sessionAggregator.min, System.currentTimeMillis())
                else
                    defaultSumCountMaxMin

                if (hasRenewed) {
                    // Init a new Session
                    sessionAggregator.run {
                        this.sum = 0.0
                        this.count = 0
                        this.max = Double.MIN_VALUE
                        this.min = Double.MAX_VALUE
                    }
                }

                kvStore!!.put(patientIdUnit, sessionAggregator)

                return KeyValue(patientIdUnit, sumCountMaxMin)
            }

            override fun close() {
                // Note: The store should NOT be closed manually here via `stateStore.close()`!
                // The Kafka Streams API will automatically close stores when necessary.
            }
        }
    }

}