package com.fh.factories

import com.fh.avro.AvroHubRecord
import com.fh.config
import org.vibur.objectpool.PoolObjectFactory

class HubRecordArrayFactory : PoolObjectFactory<Array<AvroHubRecord?>> {

    override fun create(): Array<AvroHubRecord?> {
        return arrayOfNulls(config.BATCH_SIZE)
    }

    override fun readyToTake(obj: Array<AvroHubRecord?>?): Boolean {
        return true
    }

    override fun readyToRestore(obj: Array<AvroHubRecord?>?): Boolean {
        return true
    }

    override fun destroy(obj: Array<AvroHubRecord?>?) {
    }

}
