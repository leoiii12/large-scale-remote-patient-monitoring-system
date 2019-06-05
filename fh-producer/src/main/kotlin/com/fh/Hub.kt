package com.fh

import com.conversantmedia.util.concurrent.MultithreadConcurrentQueue
import com.fh.avro.AvroHubRecord
import com.fh.avro.AvroSensorRecord
import org.slf4j.LoggerFactory
import org.vibur.objectpool.ConcurrentPool
import java.nio.ByteBuffer
import java.util.*


class Hub(
        private val endpoint: Endpoint,
        private val hubRecordPool: ConcurrentPool<AvroHubRecord>,
        private val skipRate: Double = config.SKIP_RATE
) {

    val hubPatientId = UUID.randomUUID()!!

    private val bufferedSensorRecords = MultithreadConcurrentQueue<AvroSensorRecord>(20)
    private val tempSensorRecordArray = arrayOfNulls<AvroSensorRecord>(20)
    private val tempSensorRecordList = tempSensorRecordArray.asList()

    fun bufferSensorRecord(sensorRecord: AvroSensorRecord) {
        bufferedSensorRecords.offer(sensorRecord)
    }

    fun onElapsed() {
        val numOfRecords = bufferedSensorRecords.remove(tempSensorRecordArray)

        if (Random().nextDouble() < skipRate) {
            return
        }

        if (numOfRecords == 0) {
            return
        } else if (numOfRecords > 10) {
            LoggerFactory.getLogger(Hub::class.java).warn(numOfRecords.toString())
        }

        val hubRecord = hubRecordPool.take().apply {
            records.addAll(tempSensorRecordList.subList(0, numOfRecords))

            val byteBuffer = ByteBuffer.wrap(ByteArray(16))
            byteBuffer.position(0)
            byteBuffer.putLong(hubPatientId.mostSignificantBits)
            byteBuffer.putLong(hubPatientId.leastSignificantBits)
            byteBuffer.position(0)
            patientId = byteBuffer
        }

        endpoint.submit(hubRecord)
    }

}