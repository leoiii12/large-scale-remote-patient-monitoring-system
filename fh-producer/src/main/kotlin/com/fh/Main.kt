package com.fh

import com.fh.abstracts.Config
import com.fh.abstracts.GenesFactory
import com.fh.abstracts.PeopleGroup
import com.fh.abstracts.Pools
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

val config = Config()
val pools = Pools(config)

var numOfAddedPeople = AtomicInteger(0)
var numOfGeneratedSensorRecords = AtomicInteger(0)
var numOfSubmittedSensorRecords = AtomicInteger(0)


fun main(args: Array<String>) {
    Program()
}

class Program {

    init {
        val endpoint = Endpoint()
        val scheduledExecutorService = Executors.newScheduledThreadPool(1)

        init(scheduledExecutorService, endpoint, config.genesFactory)
        initStatsWriter(scheduledExecutorService)

        Thread.currentThread().join()
    }

    private fun init(
            scheduledExecutorService: ScheduledExecutorService,
            endpoint: Endpoint,
            genesFactory: GenesFactory
    ) {
        var scheduledFuture: ScheduledFuture<*>? = null

        scheduledFuture = scheduledExecutorService.scheduleAtFixedRate({
            try {
                // A people group will create Hub and their sensors monitoring on the specified number of people
                PeopleGroup(endpoint, genesFactory, scheduledExecutorService).init()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (numOfAddedPeople.updateAndGet { it + config.NUM_OF_PEOPLE_PER_ITERATION } >= config.NUM_OF_PEOPLE) scheduledFuture!!.cancel(true)
        }, 0, 372, TimeUnit.MILLISECONDS)
    }

    private fun initStatsWriter(scheduledExecutorService: ScheduledExecutorService) {
        val logger = LoggerFactory.getLogger(Program::class.java)
        var numOfEmpty = 0

        scheduledExecutorService.scheduleAtFixedRate({
            if (numOfSubmittedSensorRecords.get() == 0) {
                numOfEmpty += 1
            }

            if (numOfEmpty > 60 * 3) {
                logger.error("Terminating because of numOfEmpty > 60 * 3.")
                System.exit(1)
            }

            if (config.WITH_LOG) {
                logger.info("" +
                        "addedPeople=${numOfAddedPeople.get()}, " +
                        "generatedSensorRecords=${numOfGeneratedSensorRecords.get()}, " +
                        "submittedSensorRecords=${numOfSubmittedSensorRecords.get()}, " +
                        "hubRecordArray=${pools.hubRecordArrayPool.taken()} / ${pools.hubRecordArrayPool.createdTotal()}, " +
                        "hubRecordBatch=${pools.hubRecordBatchPool.taken()} / ${pools.hubRecordBatchPool.createdTotal()}, " +
                        "hubRecord=${pools.hubRecordPool.taken()} / ${pools.hubRecordPool.createdTotal()}, " +
                        "sensorRecord=${pools.sensorRecordPool.taken()} / ${pools.sensorRecordPool.createdTotal()}")
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

}
