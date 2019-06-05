package com.fh.abstracts

import com.fh.avro.AvroHubRecord
import com.fh.avro.AvroHubRecordBatch
import com.fh.avro.AvroSensorRecord
import com.fh.factories.HubRecordArrayFactory
import com.fh.factories.HubRecordBatchFactory
import com.fh.factories.HubRecordFactory
import com.fh.factories.SensorRecordFactory
import org.vibur.objectpool.ConcurrentPool
import org.vibur.objectpool.util.MultithreadConcurrentQueueCollection

class Pools(config: Config) {
    val hubRecordArrayPool = ConcurrentPool<Array<AvroHubRecord?>>(
            MultithreadConcurrentQueueCollection<Array<AvroHubRecord?>>(config.MAX_NUM_OF_HUB_RECORD_BATCHES),
            HubRecordArrayFactory(),
            config.MAX_NUM_OF_HUB_RECORD_BATCHES / 2,
            config.MAX_NUM_OF_HUB_RECORD_BATCHES,
            false
    )

    val hubRecordBatchPool = ConcurrentPool<AvroHubRecordBatch>(
            MultithreadConcurrentQueueCollection<AvroHubRecordBatch>(config.CONCURRENT_CONNECTIONS),
            HubRecordBatchFactory(),
            config.CONCURRENT_CONNECTIONS / 2,
            config.CONCURRENT_CONNECTIONS,
            false
    )

    val hubRecordPool = ConcurrentPool<AvroHubRecord>(
            MultithreadConcurrentQueueCollection<AvroHubRecord>(config.MAX_NUM_OF_HUB_RECORDS),
            HubRecordFactory(),
            config.MAX_NUM_OF_HUB_RECORDS / 2,
            config.MAX_NUM_OF_HUB_RECORDS,
            false
    )

    val sensorRecordPool = ConcurrentPool<AvroSensorRecord>(
            MultithreadConcurrentQueueCollection<AvroSensorRecord>(config.MAX_NUM_OF_SENSOR_RECORDS),
            SensorRecordFactory(),
            config.MAX_NUM_OF_SENSOR_RECORDS / 2,
            config.MAX_NUM_OF_SENSOR_RECORDS,
            false
    )
}