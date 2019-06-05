package com.fh

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.errors.ProductionExceptionHandler

public class ProdExceptionHandler : ProductionExceptionHandler {

    override fun configure(configs: MutableMap<String, *>?) {
    }

    override fun handle(record: ProducerRecord<ByteArray, ByteArray>?, exception: Exception?): ProductionExceptionHandler.ProductionExceptionHandlerResponse {
        if (exception != null) {
            exception.printStackTrace()
        }

        println(record)

        return ProductionExceptionHandler.ProductionExceptionHandlerResponse.FAIL
    }

}