package com.fh.factories

import com.fh.avro.AvroSensorRecord
import org.vibur.objectpool.PoolObjectFactory

class SensorRecordFactory : PoolObjectFactory<AvroSensorRecord> {

    override fun create(): AvroSensorRecord {
        return AvroSensorRecord()
    }

    override fun readyToTake(obj: AvroSensorRecord?): Boolean {
        return true
    }

    override fun readyToRestore(obj: AvroSensorRecord?): Boolean {
        return true
    }

    override fun destroy(obj: AvroSensorRecord?) {
    }

}
