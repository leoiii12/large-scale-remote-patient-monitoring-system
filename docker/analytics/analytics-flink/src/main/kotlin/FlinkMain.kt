@file:Suppress("RedundantSamConstructor")

package com.fh.flink

import com.fh.avro.AvroHubRecordBatch
import com.fh.avro.AvroSensorRecord
import com.fh.uuid
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.functions.FilterFunction
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.api.java.tuple.Tuple2
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.*
import kotlin.collections.flatMap
import kotlin.collections.map
import kotlin.collections.set


// --KAFKA_URL "kafka:9092" --CONSUMER_GROUP_ID "0" --SCHEMA_REGISTRY_URL "http://schema-registry:8081"
fun main(args: Array<String>) {
    val env = StreamExecutionEnvironment.getExecutionEnvironment().apply {
        this.config.disableSysoutLogging()
    }
    val parameterTool = ParameterTool.fromArgs(args)

    val config = Properties().apply {
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = parameterTool.getRequired("KAFKA_URL")
        this[ConsumerConfig.GROUP_ID_CONFIG] = parameterTool.getRequired("CONSUMER_GROUP_ID")
    }

    val schemaRegistryUrl = parameterTool.getRequired("SCHEMA_REGISTRY_URL")

    val messageStream = env.addSource(
            FlinkKafkaConsumer011(
                    "fun-health-hub-record-batches",
                    ConfluentRegistryAvroDeserializationSchema.forSpecific(AvroHubRecordBatch::class.java, schemaRegistryUrl),
                    config))

    messageStream
            .flatMap(FlatMapFunction<AvroHubRecordBatch, AvroSensorRecord> { value, out -> value.batch.flatMap { it.records }.map { out.collect(it) } })
            .map(MapFunction<AvroSensorRecord, Long> { sensorRecord -> Date().time - sensorRecord.timestamp })
            .timeWindowAll(Time.seconds(1))
            .aggregate(LatencyCountAggregate())
            .map(MapFunction<Tuple2<Long, Long>, String> { "latency=${it.f0}, count=${it.f1}" })
            .print()

    // Bpm
    messageStream
            .flatMap(FlatMapFunction<AvroHubRecordBatch, SensorRecord> { value, out -> value.batch.flatMap { hubRecord -> hubRecord.records.map { out.collect(SensorRecord(hubRecord.patientId.uuid(), it.unit, it.value, it.timestamp)) } } })
            .filter(FilterFunction<SensorRecord> { it.unit == "Bpm" })
            .filter(FilterFunction<SensorRecord> { it.value > 120 })
            .map(MapFunction<SensorRecord, String> { "latency=${Date().time - it.dateTime}, record=$it" })
            .print()

    env.execute()
}

private data class SensorRecord(val patientId: String, val unit: String, val value: Double, val dateTime: Long)

private class LatencyCountAggregate : AggregateFunction<Long, Tuple2<Long, Long>, Tuple2<Long, Long>> {

    override fun createAccumulator(): Tuple2<Long, Long> {
        val latency = 0L
        val count = 0L

        return Tuple2.of(latency, count)
    }

    override fun add(value: Long, accumulator: Tuple2<Long, Long>): Tuple2<Long, Long> {
        return Tuple2.of(Math.max(value, accumulator.f0), accumulator.f1 + 1)
    }

    override fun getResult(accumulator: Tuple2<Long, Long>): Tuple2<Long, Long> {
        return accumulator
    }

    override fun merge(a: Tuple2<Long, Long>, b: Tuple2<Long, Long>): Tuple2<Long, Long> {
        return Tuple2.of(a.f0 + b.f0, a.f1 + b.f1)
    }

}