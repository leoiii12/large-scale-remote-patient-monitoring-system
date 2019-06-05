package com.fh.factories

import com.fh.avro.AvroHubRecordBatch
import org.vibur.objectpool.PoolObjectFactory

class HubRecordBatchFactory : PoolObjectFactory<AvroHubRecordBatch> {

    override fun create(): AvroHubRecordBatch {
        return AvroHubRecordBatch()
    }

    override fun readyToTake(obj: AvroHubRecordBatch?): Boolean {
        return true
    }

    override fun readyToRestore(obj: AvroHubRecordBatch?): Boolean {
        return true
    }

    override fun destroy(obj: AvroHubRecordBatch?) {
    }

}
