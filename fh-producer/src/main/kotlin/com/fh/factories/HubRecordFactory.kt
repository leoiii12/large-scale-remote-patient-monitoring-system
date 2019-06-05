package com.fh.factories

import com.fh.avro.AvroHubRecord
import org.vibur.objectpool.PoolObjectFactory

class HubRecordFactory : PoolObjectFactory<AvroHubRecord> {

    override fun create(): AvroHubRecord {
        return AvroHubRecord(null, ArrayList(20))
    }

    override fun readyToTake(obj: AvroHubRecord?): Boolean {
        return true
    }

    override fun readyToRestore(obj: AvroHubRecord?): Boolean {
        return true
    }

    override fun destroy(obj: AvroHubRecord?) {
    }

}
