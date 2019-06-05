package com.fh.abstracts

import com.fh.SimulationRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Paths

inline fun <reified T> Gson.fromJson(json: String) = this.fromJson<T>(json, object : TypeToken<T>() {}.type)!!

@Suppress("PropertyName")
class Config {
    var BATCH_SIZE: Int = 128
        private set(value) {
            field = value
        }

    var CONCURRENT_CONNECTIONS: Int = 64
        private set(value) {
            field = value
        }

    var NUM_OF_PEOPLE: Int = 100
        private set(value) {
            field = value
        }

    var NUM_OF_PEOPLE_PER_ITERATION: Int = 5
        private set(value) {
            field = value
        }

    var KAFKA_URL: String = "localhost:29092"
        private set(value) {
            field = value
        }

    var SCHEMA_REGISTRY_URL: String = "http://localhost:8081"
        private set(value) {
            field = value
        }

    var SKIP_RATE: Double = 0.0
        private set(value) {
            field = value
        }

    var HEALTHY_RATE: Double =  1.0
        private set(value) {
            field = value
        }

    var MAX_NUM_OF_HUB_RECORD_BATCHES: Int = 100
        private set(value) {
            field = value
        }

    var MAX_NUM_OF_HUB_RECORDS: Int = MAX_NUM_OF_HUB_RECORD_BATCHES * 10
        private set(value) {
            field = value
        }

    var MAX_NUM_OF_SENSOR_RECORDS: Int =  MAX_NUM_OF_HUB_RECORDS * 10
        private set(value) {
            field = value
        }

    var WITH_LOG: Boolean =  true
        private set(value) {
            field = value
        }

    val SIMULATION_RECORDS: Map<String, SimulationRecord>

    val TOPIC = "fun-health-hub-record-batches"

    val genesFactory: GenesFactory = ClusteredGenesFactory()

    init {
        val env = System.getenv()

        if (env.containsKey("BATCH_SIZE")) BATCH_SIZE = env.getValue("BATCH_SIZE").toInt()
        if (env.containsKey("CONCURRENT_CONNECTIONS")) CONCURRENT_CONNECTIONS = env.getValue("CONCURRENT_CONNECTIONS").toInt()
        if (env.containsKey("NUM_OF_PEOPLE")) NUM_OF_PEOPLE = env.getValue("NUM_OF_PEOPLE").toInt()
        if (env.containsKey("NUM_OF_PEOPLE_PER_ITERATION")) NUM_OF_PEOPLE_PER_ITERATION = env.getValue("NUM_OF_PEOPLE_PER_ITERATION").toInt()
        if (env.containsKey("KAFKA_URL")) KAFKA_URL = env.getValue("KAFKA_URL")
        if (env.containsKey("SCHEMA_REGISTRY_URL")) SCHEMA_REGISTRY_URL = env.getValue("SCHEMA_REGISTRY_URL")
        if (env.containsKey("SKIP_RATE")) SKIP_RATE = env.getValue("SKIP_RATE")!!.toDouble()
        if (env.containsKey("HEALTHY_RATE")) HEALTHY_RATE = env.getValue("HEALTHY_RATE")!!.toDouble()
        if (env.containsKey("MAX_NUM_OF_HUB_RECORD_BATCHES")) MAX_NUM_OF_HUB_RECORD_BATCHES = env.getValue("MAX_NUM_OF_HUB_RECORD_BATCHES")!!.toInt()
        if (env.containsKey("MAX_NUM_OF_HUB_RECORDS")) MAX_NUM_OF_HUB_RECORDS = env.getValue("MAX_NUM_OF_HUB_RECORDS")!!.toInt()
        if (env.containsKey("MAX_NUM_OF_SENSOR_RECORDS")) MAX_NUM_OF_SENSOR_RECORDS = env.getValue("MAX_NUM_OF_SENSOR_RECORDS")!!.toInt()
        if (env.containsKey("WITH_LOG")) WITH_LOG = env.getValue("WITH_LOG")!!.toBoolean()

        val json = String(Files.readAllBytes(Paths.get("SimulationRecords.json")))
        SIMULATION_RECORDS = Gson().fromJson(json)
    }
}