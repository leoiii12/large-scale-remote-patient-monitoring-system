package com.fh.abstracts

import com.fh.*
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


class PeopleGroup(
        private val endpoint: Endpoint,
        private val genesFactory: GenesFactory,
        private val scheduledExecutorService: ScheduledExecutorService
) {

    private val hubs = ArrayList<Hub>(config.NUM_OF_PEOPLE)
    private val groupedSensors = HashMap<Int, ArrayList<Sensor>>()

    fun init() {
        for (i in 1..config.NUM_OF_PEOPLE_PER_ITERATION) {
            val hub = Hub(endpoint, pools.hubRecordPool).also { hub -> hubs.add(hub) }
            val genes = genesFactory.getGenes()
            val person = Person(genes)

            for (simulationRecord in config.SIMULATION_RECORDS.values) {
                val intervalGroup = groupedSensors.getOrPut(simulationRecord.interval) { ArrayList(config.NUM_OF_PEOPLE) }

                intervalGroup.add(Sensor(hub, person, simulationRecord, pools.sensorRecordPool))
            }
        }

        startHubsAndSensors(hubs, groupedSensors, scheduledExecutorService)
    }

    private fun startHubsAndSensors(
            hubs: ArrayList<Hub>,
            groupedSensors: HashMap<Int, ArrayList<Sensor>>,
            scheduledExecutorService: ScheduledExecutorService) {
        scheduledExecutorService.scheduleAtFixedRate({
            for (hub in hubs) {
                hub.onElapsed()
            }
        }, 0, 500, TimeUnit.MILLISECONDS)

        for ((interval, sensors) in groupedSensors.entries) {
            scheduledExecutorService.scheduleAtFixedRate({
                for (sensor in sensors) {
                    sensor.onElapsed()
                }
            }, 0, (interval * 1000).toLong(), TimeUnit.MILLISECONDS)
        }
    }

}