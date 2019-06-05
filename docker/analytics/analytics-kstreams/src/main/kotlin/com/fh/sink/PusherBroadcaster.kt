package com.fh.sink

import com.fh.Utils
import com.fh.avro.AvroAdwinAlert
import com.fh.avro.AvroAggregatedRecord
import com.fh.avro.AvroAlert
import com.google.gson.Gson
import com.pusher.rest.Pusher
import com.pusher.rest.data.Result
import io.reactivex.BackpressureOverflowStrategy
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

private const val APP_ID = "633037"
private const val KEY = "075054eb52942fc09220"
private const val SECRET = "505ca8ec76b4c8cc3e03"

private const val EVENT_NEW_RECORDS = "newRecords"
private const val EVENT_NEW_ALERT = "newAlert"

data class GlobalAverage(val unit: String, val value: Double, val timestamp: Long);

class PusherBroadcaster() : Persistor {

    private val gson = Gson()
    private val logger = LoggerFactory.getLogger(PusherBroadcaster::class.java)
    private val pusher = Pusher(APP_ID, KEY, SECRET).apply {
        this.setCluster("ap1")
        this.setEncrypted(true)
    }

    private val subscribedChannels = BehaviorSubject.createDefault<Set<String>>(HashSet())

    inner class ChannelsResult {
        var channels: HashMap<String, Any> = HashMap()
    }

    init {
        Flowable
                .interval(10, TimeUnit.SECONDS)

                // Get the channels
                .flatMap<Result> { _ -> Flowable.fromCallable<Result> { pusher.get("/channels") } }
                .filter { result -> result.status == Result.Status.SUCCESS }
                .map<Set<String>> { result ->
                    val channelsResult = gson.fromJson(result.message, ChannelsResult::class.java)

                    return@map channelsResult.channels.keys
                }
                .filter { Objects.nonNull(it) }

                .onExceptionResumeNext(Flowable.just(HashSet()))

                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())

                .subscribe { patientIds -> this.subscribedChannels.onNext(patientIds) }
    }

    override fun initGlobalAverages(globalAverages: PublishProcessor<GlobalAverage>, numOfRecordsStream: PublishProcessor<Int>) {
        throw NotImplementedError()
    }

    override fun initNewRecords(aggregatedRecords: PublishProcessor<AvroAggregatedRecord>, numOfRecordsStream: PublishProcessor<Int>) {
        aggregatedRecords

                // TODO: Pause
                .onBackpressureBuffer(512L, { logger.error("aggregatedRecords overflow.") }, BackpressureOverflowStrategy.DROP_OLDEST)

                // Trigger only subscribed channels
                .map { Utils.getUUIDFromByteBuffer(it.patientId).toString() }
                .filter { patientId -> this.subscribedChannels.value!!.contains(patientId) }

                // Debounce each patientId
                .groupBy { patientId -> patientId }
                .flatMap { flowable -> flowable.debounce(10, TimeUnit.SECONDS) }

                // Batch multiple patientIds
                .buffer(1, TimeUnit.SECONDS, 10)
                .filter { patientIds -> patientIds.size > 0 }

                // Trigger
                .map { patientIds ->
                    val result = pusher.trigger(patientIds, EVENT_NEW_RECORDS, emptyList<Any>())
                    if (result.status != Result.Status.SUCCESS) {
                        logger.error(result.message)
                    }

                    patientIds.size
                }

                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())

                .buffer(10, TimeUnit.SECONDS)
                .flatMap { sizes -> Flowable.just<Int>(sizes.stream().mapToInt { it.toInt() }.sum()) }
                .filter { size -> size > 0 }

                .subscribe(
                        { size ->
                            numOfRecordsStream.offer(size)
                            logger.info(String.format("# of aggregatedRecords into WebSocket = %d", size))
                        },
                        { error -> logger.error(error.toString()) })
    }

    override fun initNewAlerts(alerts: PublishProcessor<AvroAlert>, numOfRecordsStream: PublishProcessor<Int>) {
        alerts

                // TODO: Pause
                .onBackpressureBuffer(512L, { logger.error("alerts overflow.") }, BackpressureOverflowStrategy.DROP_OLDEST)

                // Trigger only subscribed channels
                .filter { alert -> this.subscribedChannels.value!!.contains(Utils.getUUIDFromByteBuffer(alert.patientId).toString()) }

                // Trigger
                .map { alert ->
                    val id = Utils.getUUIDFromByteBuffer(alert.id).toString()
                    val patientId = Utils.getUUIDFromByteBuffer(alert.patientId).toString()

                    val result = this.pusher.trigger(patientId, EVENT_NEW_ALERT, listOf(id, alert.timestamp, alert.unit, alert.value))
                    if (result.status != Result.Status.SUCCESS) {
                        logger.error(result.message)
                    }

                    return@map patientId
                }

                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())

                .buffer(10, TimeUnit.SECONDS)
                .filter { patientIds -> !patientIds.isEmpty() }

                .subscribe(
                        { patientIds ->
                            numOfRecordsStream.offer(patientIds.size)
                            logger.info(String.format("Broadcasted alerts for [%s].", patientIds.joinToString(", ")))
                        },
                        { error -> logger.error(error.toString()) }
                )
    }

    override fun initNewAdwinAlerts(alerts: PublishProcessor<AvroAdwinAlert>, numOfRecordsStream: PublishProcessor<Int>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}