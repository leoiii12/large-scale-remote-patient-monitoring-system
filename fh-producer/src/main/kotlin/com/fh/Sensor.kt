package com.fh

import com.fh.avro.AvroSensorRecord
import org.slf4j.LoggerFactory
import org.vibur.objectpool.ConcurrentPool
import java.time.Instant

val logger = LoggerFactory.getLogger(Sensor::class.java)!!

class Sensor(
        private val hub: Hub,
        private val person: Person,
        private val simulationRecord: SimulationRecord,
        private val sensorRecordPool: ConcurrentPool<AvroSensorRecord>
) {

    // Initial value at average
    private var _lastValue = person.value((simulationRecord.max + simulationRecord.min) / 2, simulationRecord.max, simulationRecord.min, simulationRecord.rate)

    private fun getSensorRecord(): AvroSensorRecord {
        val sensorRecord = sensorRecordPool.take()

        sensorRecord.timestamp = Instant.now().toEpochMilli()
        sensorRecord.value = person.value(_lastValue, simulationRecord.max, simulationRecord.min, simulationRecord.rate)
        sensorRecord.unit = simulationRecord.unit

        if (sensorRecord.value > simulationRecord.max || sensorRecord.value < simulationRecord.min) {
            logger.info("Abnormal,${hub.hubPatientId},${sensorRecord.unit},${sensorRecord.value}")
        }

        _lastValue = sensorRecord.value

        return sensorRecord
    }

    fun onElapsed() {
        hub.bufferSensorRecord(getSensorRecord())

        numOfGeneratedSensorRecords.updateAndGet { it + 1 }
    }

}