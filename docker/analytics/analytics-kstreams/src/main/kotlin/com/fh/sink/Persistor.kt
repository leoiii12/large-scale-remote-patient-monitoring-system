package com.fh.sink

import com.fh.avro.AvroAdwinAlert
import com.fh.avro.AvroAggregatedRecord
import com.fh.avro.AvroAlert
import io.reactivex.processors.PublishProcessor

interface Persistor {
    fun initGlobalAverages(globalAverages: PublishProcessor<GlobalAverage>, numOfRecordsStream: PublishProcessor<Int>)
    fun initNewRecords(aggregatedRecords: PublishProcessor<AvroAggregatedRecord>, numOfRecordsStream: PublishProcessor<Int>)
    fun initNewAlerts(alerts: PublishProcessor<AvroAlert>, numOfRecordsStream: PublishProcessor<Int>)
    fun initNewAdwinAlerts(alerts: PublishProcessor<AvroAdwinAlert>, numOfRecordsStream: PublishProcessor<Int>)
}