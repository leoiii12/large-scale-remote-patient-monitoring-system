package com.fh

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.streams.errors.DeserializationExceptionHandler
import org.apache.kafka.streams.processor.ProcessorContext

public class DescExceptionHandler : DeserializationExceptionHandler {

    override fun configure(configs: MutableMap<String, *>?) {
    }

    override fun handle(context: ProcessorContext?, record: ConsumerRecord<ByteArray, ByteArray>?, exception: Exception?): DeserializationExceptionHandler.DeserializationHandlerResponse {
        if (exception != null) {
            exception.printStackTrace()
        }

        println(record)

        return DeserializationExceptionHandler.DeserializationHandlerResponse.FAIL
    }

}


