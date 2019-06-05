package com.fh.sessionization

import com.fh.avro.AvroAlert
import com.fh.avro.AvroPatientIdUnit
import com.fh.avro.AvroSessionAggregator
import org.apache.kafka.streams.processor.Processor
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.processor.ProcessorSupplier
import org.apache.kafka.streams.state.KeyValueStore
import java.nio.ByteBuffer

class AlertProcessorSupplier : ProcessorSupplier<ByteArray?, AvroAlert> {

    private val stateStoreName: String

    constructor(stateStoreName: String) {
        this.stateStoreName = stateStoreName
    }

    override fun get(): Processor<ByteArray?, AvroAlert> {
        return object : Processor<ByteArray?, AvroAlert> {

            private var kvStore: KeyValueStore<AvroPatientIdUnit, AvroSessionAggregator?>? = null
            private val id = processorId.getAndIncrement()

            @Suppress("UNCHECKED_CAST")
            override fun init(context: ProcessorContext) {
                kvStore = context.getStateStore(stateStoreName) as KeyValueStore<AvroPatientIdUnit, AvroSessionAggregator?>
            }

            override fun process(key: ByteArray?, alert: AvroAlert) {
                if (kvStore == null) {
                    throw NullPointerException("kvStore is null.")
                }

                val patientIdUnit = AvroPatientIdUnit(ByteBuffer.wrap(key), alert.unit)

                // Init sessionAggregator
                kvStore!!.putIfAbsent(patientIdUnit, AvroSessionAggregator(1, 0, 1, 1, 0.0, 0, Double.MIN_VALUE, Double.MAX_VALUE))

                // Retrieve sessionAggregator
                val sessionAggregator = kvStore!!.get(patientIdUnit)!!

                sessionAggregator.run {
                    // Shrink Next
                    this.renewingCredits -= 1

                    // Adjust Next
                    val maxMin = maxMinCreditsByUnit.getValue(alert.unit)
                    if (this.renewingCredits < maxMin.min) {
                        this.renewingCredits = maxMin.min
                    }

                    // Divide Current by 2
                    this.credits = this.credits / 2
                }

                kvStore!!.put(patientIdUnit, sessionAggregator)
            }

            override fun close() {
                // Note: The store should NOT be closed manually here via `stateStore.close()`!
                // The Kafka Streams API will automatically close stores when necessary.
            }

        }
    }
}