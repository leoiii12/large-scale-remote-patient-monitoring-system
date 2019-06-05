package com.fh.sink

import com.fh.CRATE_DB_URL
import com.fh.Utils
import com.fh.avro.AvroAdwinAlert
import com.fh.avro.AvroAggregatedRecord
import com.fh.avro.AvroAlert
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.reactivex.BackpressureOverflowStrategy
import io.reactivex.processors.PublishProcessor
import io.reactivex.processors.ReplayProcessor
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashSet

class CrateDBPersistor() : Persistor {

    private val logger = LoggerFactory.getLogger(CrateDBPersistor::class.java)
    private val config = HikariConfig().also {
        it.driverClassName = "io.crate.client.jdbc.CrateDriver"
        it.jdbcUrl = CRATE_DB_URL
        it.username = "crate"
        it.idleTimeout = 1000 * 30
        it.maxLifetime = 1000 * 60
        it.maximumPoolSize = 50
    }
    private val ds: HikariDataSource = HikariDataSource(config)
    private val patientIds = ReplayProcessor.create<UUID>()

    init {
        runBlocking { initTables(ds) }

        var totalNumOfPatientIds = 0
        val patientIdSet = HashSet<UUID>()

        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val resultSet = stmt.executeQuery("SELECT patient_id FROM doc.patient_ids;")

                while (resultSet.next()) {
                    patientIdSet.add(UUID.fromString(resultSet.getString("patient_id")))
                }
            }
        }

        patientIds
                .distinct(
                        { it },
                        { patientIdSet }
                )
                .parallel(25)
                .runOn(Schedulers.io())
                .map { patientId ->
                    while (true) {
                        try {
                            ds.connection.use { conn ->
                                logger.info(String.format("Took connection %s", conn))

                                conn.prepareStatement("INSERT INTO doc.patient_ids (patient_id, patient_id_ft) VALUES (?, ?) ON CONFLICT DO NOTHING;").use { ps ->
                                    val patientIdStr = patientId.toString()

                                    ps.setString(1, patientIdStr)
                                    ps.setString(2, patientIdStr)

                                    ps.execute()
                                }

                                logger.info(String.format("Closed connection %s", conn))
                            }

                            break
                        } catch (e: Exception) {
                            logger.error(e.toString())
                        }

                        runBlocking {
                            delay(1000)
                        }
                    }

                    return@map patientId
                }
                .sequential()
                .buffer(1, TimeUnit.MINUTES, 1000)
                .filter { patientIds -> patientIds.size > 0 }
                .subscribe { newPatientIds ->
                    totalNumOfPatientIds += newPatientIds.size

                    println("newPatientIds=$newPatientIds, totalNumOfPatientIds=$totalNumOfPatientIds")
                }
    }

    override fun initGlobalAverages(globalAverages: PublishProcessor<GlobalAverage>, numOfRecordsStream: PublishProcessor<Int>) {
        globalAverages

                .onBackpressureBuffer(1000L, { logger.error("globalAverages overflow.") }, BackpressureOverflowStrategy.DROP_OLDEST)

                .buffer(100, TimeUnit.MILLISECONDS, 20)
                .filter { it.size > 0 }

                .parallel(5)
                .runOn(Schedulers.io())

                .map<Int> { records ->
                    var ints = IntArray(0)

                    try {
                        ds.connection.use { conn ->
                            logger.info(String.format("Took connection %s", conn))

                            conn.prepareStatement("INSERT INTO doc.global_averages (ts, u, v) VALUES (?, ?, ?);").use { ps ->
                                for (globalAverage in records) {
                                    ps.clearParameters()

                                    ps.setLong(1, globalAverage.timestamp)
                                    ps.setString(2, globalAverage.unit)
                                    ps.setDouble(3, globalAverage.value)

                                    ps.addBatch()
                                }

                                ints = ps.executeBatch()
                            }

                            logger.info(String.format("Closed connection %s", conn))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    return@map Arrays.stream(ints).sum()
                }

                .sequential()
                .retry { retryCount, error ->
                    error.printStackTrace()

                    if (retryCount > 20) {
                        logger.error("globalAverages has over 20 errors.")

                        return@retry false
                    }

                    return@retry true
                }

                .subscribe(
                        { size ->
                            if (size is Int) {
                                numOfRecordsStream.offer(size)
                                logger.info(String.format("# of alerts into CrateDB = %d", size))
                            } else {
                                logger.error(size.toString())
                            }
                        },
                        { error -> logger.error(error.toString()) })
    }

    override fun initNewRecords(aggregatedRecords: PublishProcessor<AvroAggregatedRecord>, numOfRecordsStream: PublishProcessor<Int>) {
        aggregatedRecords

                // At least 10 times of the buffer size
                .onBackpressureBuffer(10000L, { logger.error("aggregatedRecords overflow.") }, BackpressureOverflowStrategy.DROP_OLDEST)

                // Batch the aggregatedRecords, count controls the batch size
                // Maximize 32768 parameters
                .buffer(2, TimeUnit.SECONDS, 1000)
                .filter { it.size > 0 }

                .parallel(40)
                .runOn(Schedulers.io())

                // Persist to CockroachDB
                .map<Int> { records ->
                    var arSize = records.size

                    val sb = StringBuilder("INSERT INTO doc.aggregated_records (id, patient_id, ts, u, avg_v, min_v, max_v) VALUES (?, ?, ?, ?, ?, ?, ?)")
                    for (i in 1 until arSize) {
                        sb.append(", (?, ?, ?, ?, ?, ?, ?)")
                    }
                    sb.append(";")

                    try {
                        ds.connection.use { conn ->
                            logger.info(String.format("Took connection %s", conn))

                            conn.prepareStatement(sb.toString()).use { ps ->

                                var i = 0
                                for (aggregatedRecord in records) {
                                    val patientId = Utils.getUUIDFromByteBuffer(aggregatedRecord.patientId).also {
                                        patientIds.onNext(it)
                                    }

                                    ps.setString(i + 1, "")
                                    ps.setString(i + 2, patientId.toString())
                                    ps.setLong(i + 3, aggregatedRecord.timestamp!!)
                                    ps.setString(i + 4, aggregatedRecord.unit)
                                    ps.setDouble(i + 5, aggregatedRecord.avgValue!!)
                                    ps.setDouble(i + 6, aggregatedRecord.minValue!!)
                                    ps.setDouble(i + 7, aggregatedRecord.maxValue!!)

                                    i += 7
                                }

                                arSize = ps.executeUpdate()
                            }

                            logger.info(String.format("Closed connection %s", conn))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    return@map arSize
                }

                .sequential()
                .retry { retryCount, error ->
                    error.printStackTrace()

                    if (retryCount > 20) {
                        logger.error("aggregatedRecords has over 20 errors.")

                        return@retry false
                    }

                    return@retry true
                }

                .subscribe(
                        { size ->
                            if (size is Int) {
                                numOfRecordsStream.offer(size)
                                logger.info(String.format("# of aggregatedRecords into CrateDB = %d", size))
                            } else {
                                logger.error(size.toString())
                            }
                        },
                        { error -> logger.error(error.toString()) })
    }

    override fun initNewAlerts(alerts: PublishProcessor<AvroAlert>, numOfRecordsStream: PublishProcessor<Int>) {
        alerts

                .onBackpressureBuffer(1000L, { logger.error("alerts overflow.") }, BackpressureOverflowStrategy.DROP_OLDEST)

                .buffer(100, TimeUnit.MILLISECONDS, 20)
                .filter { it.size > 0 }

                .parallel(10)
                .runOn(Schedulers.io())

                .map<Int> { records ->
                    var ints = IntArray(0)

                    try {
                        ds.connection.use { conn ->
                            logger.info(String.format("Took connection %s", conn))

                            conn.prepareStatement("INSERT INTO alerts (id, patient_id, ts, u, v) VALUES (?, ?, ?, ?, ?)").use { ps ->

                                for (alert in records) {
                                    ps.clearParameters()

                                    val patientId = Utils.getUUIDFromByteBuffer(alert.patientId).also {
                                        patientIds.onNext(it)
                                    }

                                    ps.setString(1, Utils.getUUIDFromByteBuffer(alert.id).toString())
                                    ps.setString(2, patientId.toString())
                                    ps.setLong(3, alert.timestamp!!)
                                    ps.setString(4, alert.unit)
                                    ps.setDouble(5, alert.value!!)
                                    ps.addBatch()
                                }

                                ints = ps.executeBatch()
                            }

                            logger.info(String.format("Closed connection %s", conn))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    return@map Arrays.stream(ints).sum()
                }

                .sequential()
                .retry { retryCount, error ->
                    error.printStackTrace()

                    if (retryCount > 20) {
                        logger.error("alerts has over 20 errors.")

                        return@retry false
                    }

                    return@retry true
                }

                .subscribe(
                        { size ->
                            if (size is Int) {
                                numOfRecordsStream.offer(size)
                                logger.info(String.format("# of alerts into CrateDB = %d", size))
                            } else {
                                logger.error(size.toString())
                            }
                        },
                        { error -> logger.error(error.toString()) })
    }

    override fun initNewAdwinAlerts(adwinAlerts: PublishProcessor<AvroAdwinAlert>, numOfRecordsStream: PublishProcessor<Int>) {

        adwinAlerts

                // At least 10 times of the buffer size
                .onBackpressureBuffer(10000L, { logger.error("adwin_alerts overflow.") }, BackpressureOverflowStrategy.DROP_OLDEST)

                // Batch the aggregatedRecords, count controls the batch size
                // Maximize 32768 parameters
                .buffer(1, TimeUnit.SECONDS, 1000)
                .filter { it.size > 0 }

                .parallel(50)
                .runOn(Schedulers.io())

                // Persist to CockroachDB
                .map<Int> { records ->
                    var arSize = records.size

                    val sb = StringBuilder("INSERT INTO adwin_alerts (id, patient_id, u, f, t) VALUES (?, ?, ?, ?, ?)")
                    for (i in 1 until arSize) {
                        sb.append(", (?, ?, ?, ?, ?)")
                    }
                    sb.append(";")

                    try {
                        ds.connection.use { conn ->
                            logger.info(String.format("Took connection %s", conn))

                            conn.prepareStatement(sb.toString()).use { ps ->
                                var i = 0
                                for (alert in records) {
                                    val patientId = Utils.getUUIDFromByteBuffer(alert.patientId).also {
                                        patientIds.onNext(it)
                                    }

                                    ps.setString(i + 1, Utils.getUUIDFromByteBuffer(alert.id).toString())
                                    ps.setString(i + 2, patientId.toString())
                                    ps.setString(i + 3, alert.unit!!)
                                    ps.setLong(i + 4, alert.from)
                                    ps.setLong(i + 5, alert.to)

                                    i += 5
                                }

                                arSize = ps.executeUpdate()
                            }

                            logger.info(String.format("Closed connection %s", conn))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    return@map arSize
                }

                .sequential()
                .retry { retryCount, error ->
                    error.printStackTrace()

                    if (retryCount > 20) {
                        logger.error("adwinAlerts has over 20 errors.")

                        return@retry false
                    }

                    return@retry true
                }

                .subscribe(
                        { size ->
                            if (size is Int) {
                                numOfRecordsStream.offer(size)
                                logger.info(String.format("# of adwinAlerts into CrateDB = %d", size))
                            } else {
                                logger.error(size.toString())
                            }
                        },
                        { error -> logger.error(error.toString()) })
    }

    private suspend fun initTables(ds: HikariDataSource) {
        while (true) {
            try {
                ds.connection.use { conn ->
                    initUuidAnalyzer(conn)
                    initPatientIdsTable(conn)
                    initGlobalAveragesTable(conn)
                    initAggregatedRecordsTable(conn)
                    initAlertsTable(conn)
                    initAdwinAlertsTable(conn)
                }

                break
            } catch (e: SQLException) {
                e.printStackTrace()
            }

            delay(1000)
        }
    }

    @Throws(SQLException::class)
    private fun initUuidAnalyzer(conn: Connection) {
        executeSql(conn,
                "CREATE ANALYZER uuid_analyzer (\n" +
                        "  TOKENIZER n WITH ( type = ngram, min_gram = 3, max_gram = 8),\n" +
                        "  TOKEN_FILTERS (\n" +
                        "    lowercase\n" +
                        "  )\n" +
                        ");")
    }

    @Throws(SQLException::class)
    private fun initPatientIdsTable(conn: Connection) {
        executeSql(conn, "CREATE TABLE IF NOT EXISTS patient_ids\n" +
                "(\n" +
                "    patient_id STRING PRIMARY KEY,\n" +
                "    patient_id_ft STRING NOT NULL INDEX USING FULLTEXT WITH (analyzer = 'uuid_analyzer')\n" +
                ");")
    }

    @Throws(SQLException::class)
    private fun initGlobalAveragesTable(conn: Connection) {
        executeSql(conn, "CREATE TABLE IF NOT EXISTS global_averages\n" +
                "(\n" +
                "    ts TIMESTAMP NOT NULL,\n" +
                "    u STRING NOT NULL,\n" +
                "    v DOUBLE NOT NULL INDEX OFF,\n" +
                "    ts_day TIMESTAMP GENERATED ALWAYS AS date_trunc('day', ts)\n" +
                ") CLUSTERED BY (u) PARTITIONED BY (ts_day);")
    }

    @Throws(SQLException::class)
    private fun initAggregatedRecordsTable(conn: Connection) {
        executeSql(conn,
                ("CREATE TABLE IF NOT EXISTS aggregated_records\n" +
                        "(\n" +
                        "    id STRING NOT NULL INDEX OFF,\n" +
                        "    patient_id STRING NOT NULL,\n" +
                        "    ts TIMESTAMP NOT NULL,\n" +
                        "    u STRING NOT NULL,\n" +
                        "    avg_v DOUBLE NOT NULL INDEX OFF,\n" +
                        "    min_v DOUBLE NOT NULL INDEX OFF,\n" +
                        "    max_v DOUBLE NOT NULL INDEX OFF,\n" +
                        "    ts_hour TIMESTAMP GENERATED ALWAYS AS date_trunc('hour', ts)\n" +
                        ") CLUSTERED BY (patient_id) PARTITIONED BY (ts_hour);")
        )
    }

    @Throws(SQLException::class)
    private fun initAlertsTable(conn: Connection) {
        executeSql(conn,
                ("CREATE TABLE IF NOT EXISTS alerts\n" +
                        "(\n" +
                        "    id STRING NOT NULL INDEX OFF,\n" +
                        "    patient_id STRING NOT NULL,\n" +
                        "    ts TIMESTAMP NOT NULL,\n" +
                        "    u STRING NOT NULL,\n" +
                        "    v DOUBLE NOT NULL INDEX OFF,\n" +
                        "    ts_day TIMESTAMP GENERATED ALWAYS AS date_trunc('day', ts)\n" +
                        ") CLUSTERED BY (patient_id) PARTITIONED BY (ts_day);")
        )
    }

    @Throws(SQLException::class)
    private fun initAdwinAlertsTable(conn: Connection) {
        executeSql(conn,
                ("CREATE TABLE IF NOT EXISTS adwin_alerts\n" +
                        "(\n" +
                        "    id STRING NOT NULL INDEX OFF,\n" +
                        "    patient_id STRING NOT NULL,\n" +
                        "    u STRING NOT NULL,\n" +
                        "    f TIMESTAMP NOT NULL,\n" +
                        "    t TIMESTAMP NOT NULL,\n" +
                        "    f_hour TIMESTAMP GENERATED ALWAYS AS date_trunc('hour', f)\n" +
                        ") CLUSTERED BY (patient_id) PARTITIONED BY (f_hour);")
        )
    }

    @Throws(SQLException::class)
    private fun executeSql(conn: Connection, @Language("GenericSQL") sql: String) {
        val stmt = conn.createStatement()

        stmt.execute(sql)
        stmt.close()
    }

}