package com.fh

import com.google.gson.Gson
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import io.confluent.kafka.serializers.subject.RecordNameStrategy
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.delay
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer

val KAFKA_URL = (if (System.getenv("KAFKA_URL") == null) "localhost:9092" else System.getenv("KAFKA_URL"))!!
val SCHEMA_REGISTRY_URL = (if (System.getenv("SCHEMA_REGISTRY_URL") == null) "http://localhost:8081" else System.getenv("SCHEMA_REGISTRY_URL"))!!
val CRATE_DB_ENABLED = (if (System.getenv("CRATE_DB_ENABLED") == null) "true" else System.getenv("CRATE_DB_ENABLED"))!!
val PUSHER_ENABLED = (if (System.getenv("PUSHER_ENABLED") == null) "true" else System.getenv("PUSHER_ENABLED"))!!
val CRATE_DB_URL = (if (System.getenv("CRATE_DB_URL") == null) "crate://localhost:5432/" else System.getenv("CRATE_DB_URL"))!!
val NUM_OF_STREAM_THREADS = (if (System.getenv("NUM_OF_STREAM_THREADS") == null) "4" else System.getenv("NUM_OF_STREAM_THREADS"))!!.toInt()

fun getProps(applicationId: String): Properties {
    val props = Properties().apply {
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = KAFKA_URL

        // Consumer
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"

        // Streams
        this[StreamsConfig.APPLICATION_ID_CONFIG] = applicationId
        this[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.ByteArray()::class.java.name
        this[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = SpecificAvroSerde::class.java
        this[StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] = DescExceptionHandler()::class.java.name
        this[StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG] = ProdExceptionHandler()::class.java.name
        this[StreamsConfig.NUM_STREAM_THREADS_CONFIG] = NUM_OF_STREAM_THREADS
        this[StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG] = 1
        this[StreamsConfig.COMMIT_INTERVAL_MS_CONFIG] = 1000
        this[StreamsConfig.REPLICATION_FACTOR_CONFIG] = 2
        this[StreamsConfig.TOPOLOGY_OPTIMIZATION] = StreamsConfig.OPTIMIZE

        // Avro
        this[AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = SCHEMA_REGISTRY_URL
        this[AbstractKafkaAvroSerDeConfig.KEY_SUBJECT_NAME_STRATEGY] = RecordNameStrategy::class.java.name
        this[AbstractKafkaAvroSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY] = RecordNameStrategy::class.java.name
    }

    return props
}

suspend fun waitTopics(props: Properties) {
    val topics = setOf(
            "fun-health-hub-record-batches",
            "fun-health-sensor-records",
            "fun-health-global-averages",
            "fun-health-aggregated-records",
            "fun-health-alerts"
    )

    while (true) {
        try {
            val adminClient = KafkaAdminClient.create(props)

            val existingTopics = adminClient
                    .listTopics(ListTopicsOptions())
                    .names()
                    .get(30, TimeUnit.SECONDS)

            adminClient.close()

            println(existingTopics)

            val newTopics = topics.minus(existingTopics)
            if (newTopics.isEmpty()) {
                println("All topics exist. This will start in 30 seconds.")
                delay(1000 * 30)

                return
            }
        } catch (e: Exception) {
            println(e.message)
        }

        delay(1000 * 10)
    }
}

inline fun <reified T> getAvroSerde(): SpecificAvroSerde<T> where T : SpecificRecord {
    val serde = SpecificAvroSerde<T>()

    serde.configure(
            mapOf(
                    Pair(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL),
                    Pair(AbstractKafkaAvroSerDeConfig.KEY_SUBJECT_NAME_STRATEGY, RecordNameStrategy::class.java.name),
                    Pair(AbstractKafkaAvroSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY, RecordNameStrategy::class.java.name)
            ),
            false
    )

    return serde
}

val logger = LoggerFactory.getLogger("Utils")!!

fun startStreams(builder: StreamsBuilder, props: Properties, post: ((KafkaStreams) -> Unit)? = null): Unit {
    val streams = KafkaStreams(builder.build(), props)

    streams.setUncaughtExceptionHandler { _, e ->
        e.printStackTrace()

        if (streams.close(Duration.ofSeconds(10))) {
            logger.info("The streams is stopped.")
        } else {
            logger.error("The streams cannot be stopped.")
        }

        System.exit(1)
    }

    streams.start()

    if (post != null) {
        post(streams)
    }

    // Shutdown Hook should not invoke any other System.exit or Runtime.getRuntime().exit.
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Terminating in 20 seconds...")

        if (streams.close(Duration.ofSeconds(20))) {
            logger.info("Closed normally.")
        } else {
            logger.error("Closed abnormally.")
        }
    })
}

fun KafkaStreams.initMetrics(numOfRecords: AtomicInteger, period: Long = 1000 * 10, threshold: Long = 30): Unit {
    var lastNumOfRecords = 0

    println("Metrics timer started...")

    fixedRateTimer(name = "metricsTimer", initialDelay = 1000 * 10, period = period) {
        numOfRecords.getAndSet(0).also {
            println("${Instant.now()}, state=${state()}, numOfRecords=$it, lastNumOfRecords=$lastNumOfRecords")

            lastNumOfRecords = it

            if (state() == KafkaStreams.State.NOT_RUNNING) {
                println("Terminating in 20 seconds because of streams.state() == KafkaStreams.State.NOT_RUNNING...")

                this.cancel()

                System.exit(1)
            }
        }
    }

    val gson = Gson()

    val server = embeddedServer(Netty, 8080) {
        routing {
            get("/metrics") {
                call.respondText(gson.toJson(metrics()), ContentType.Application.Json)
            }
        }
    }

    server.start()
}
