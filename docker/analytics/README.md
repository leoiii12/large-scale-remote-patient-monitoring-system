# Init the spark image
```bash
cd /usr/local/Cellar/apache-spark/2.4.0/libexec
docker build -t "asia.gcr.io/jc1804-220407/apache-spark:2.4.0" -f kubernetes/dockerfiles/spark/Dockerfile .
docker push "asia.gcr.io/jc1804-220407/apache-spark:2.4.0"
```

# Init the spark app image and sink app image
```bash
docker build -t "asia.gcr.io/jc1804-220407/fh-analytics-spark-minutely:1.8.14" -f analytics-spark/Dockerfile analytics-spark/
docker push "asia.gcr.io/jc1804-220407/fh-analytics-spark-minutely:1.8.14"

docker build -t "asia.gcr.io/jc1804-220407/fh-analytics-kafka:1.9.4" -f analytics-kstreams/Dockerfile analytics-kstreams/
docker push "asia.gcr.io/jc1804-220407/fh-analytics-kafka:1.9.4"

docker build -t "asia.gcr.io/jc1804-220407/fh-analytics-sink:crate-1.3.4" -f sink/Dockerfile sink/
docker push "asia.gcr.io/jc1804-220407/fh-analytics-sink:crate-1.3.4"

docker build -t "asia.gcr.io/jc1804-220407/fh-analytics-init-kafka:1.0.10" -f init-kafka/Dockerfile init-kafka/
docker push "asia.gcr.io/jc1804-220407/fh-analytics-init-kafka:1.0.10"
```

# Change Log

## fh-analytics-kafka

### 1.9.4

1. Fixed CrateDBPersistor for adwin alerts

### 1.9.2

1. Fixed some bugs

### 1.9.0

1. Added AdwinAlert

### 1.8.14

1. Deprecate SessionWindows https://issues.apache.org/jira/browse/KAFKA-7652?page=com.atlassian.jira.plugin.system.issuetabpanels%3Aall-tabpanel

### 1.8.8

1. Docker Heap Options

### 1.8.7

1. Remote JMX

### 1.8.5

1. Fixed Alerts

### 1.8.4

1. Changed all other apps to use fun-health-sessionized-sensor-records

### 1.8.3

1. Disabled logging for Sessionization

### 1.8.2

1. Fixed immediate renew when alert

### 1.8.1

1. Fixed from and to

### 1.8.0

1. Added SparkAdwinAlerts

### 1.7.17

1. Fixed sessionization and minutely

### 1.7.8

1. Modified terminating rules

### 1.7.8

1. Minutely numOfRecords

### 1.7.8

1. Minutely numOfRecords

### 1.7.7

1. Minutely with sessionized sensor records

### 1.7.6

1. Sessionization writes with specified serdes

### 1.7.5

1. Sessionization writes to fun-health-sessionized-sensor-records

### 1.7.4

1. Sessionization displays values

### 1.7.3

1. Sessionization metrics

### 1.7.2

1. Sessionization state stores

### 1.7.0

1. Sessionization

### 1.6.0-62

1. Fill the patientIds with the existing ids first

### 1.6.0-61

1. Increased the buffer size

### 1.6.0-60

1. Replaced patientIds with ReplayProcessor

### 1.6.0-59

1. Enabled topology optimizations

### 1.6.0-33

1. Added termination messages

### 1.6.0-30

1. Fixed numOfEmpty while the interval is decreased

### 1.6.0-29

1. Increased StreamsConfig.NUM_STREAM_THREADS_CONFIG
2. Extracted initMetricsTimer
3. Increased userId Persistor

### 1.6.0-28

1. Persist patient ids independently

### 1.6.0-25

1. Using getAvroSerde
 
### 1.6.0-24

1. Fixed persistor initialization 

### 1.6.0-23

1. Fixed the initial values

### 1.6.0-22

1. Modified CrateDB timeout

### 1.6.0-21

1. Finalize SpecificAvro usage by using record as the subject name

### 1.6.0-4

1. Fixed getAvro<T>() usage

### 1.6.0-1

1. Suppressed intermediate results of global averages
2. Values other than running will be considered as errors
3. Added global averages grace periods
4. Implemented Minutely
5. Implemented generic getAvro<T>()

### 1.5.1

1. Fixed ThroughputAggregatedRecords, ThroughputAlerts types
2. Fixed SinkGlobalAverages Serdes

### 1.5.0

1. Self-recovery

### 1.4.6

1. Improved structures
2. Fixed initial REBALANCING 

### 1.4.5

1. Implemented SinkAggregatedRecords, SinkAggregatedAlerts, SinkGlobalAverages of CrateDB and Pusher in Kafka Streams
2. Implemented AnalyticsAlerts in KafkaStreams

### 1.4.0

1. Added sink

### 1.3.0

1. fh-analytics-kafka

### 1.2.0

1. Added global averages

### 1.1.14

1. Added exception handlers

### 1.1.5

1. Fixed AdminClient

### 1.1.0

1. Multiple main classes, adding KafkaThroughput

### 1.0.15

1. fun-health-hub-records -> fun-health-sensor-records

### 1.0.14

1. Fixed null byte array

### 1.0.1

1. Fixed Dockerfile path

### 1.0.0

1. Init

## fh-analytics-spark-minutely

### 1.8.14

1. Extracted maxOffsetsPerTrigger and kafka.max.poll.records

### 1.8.13

1. Increased maxOffsetsPerTrigger and kafka.max.poll.records

### 1.8.12

1. Adjusted for small-range units

### 1.8.8

1. Not supported

.option("kafka.enable.auto.commit", true)
.option("kafka.auto.commit.interval.ms", 30 * 1000)

### 1.8.7

1. Adjusted numOfTasks

### 1.8.6

1. Working with the necessary config spark.hadoop.dfs.client.use.datanode.hostname in Kubernetes 1.13

### 1.8.0

1. Added SparkAdwinAlert

### 1.7.10

1. Removed repartition

### 1.7.7

1. Increased micro batch size

### 1.7.5

1. fun-health-hub-records -> fun-health-sensor-records

### 1.7.3

1. Fixed SparkAlert null avroSerializer

### 1.7.2

1. Removed extra encoding

### 1.7.1

1. Fixed null NormalRange

### 1.7.0

1. Extracted ETL to fh-analytics-kafka-etl
2. Renamed fun-health-hub-record to fun-health-hub-records

### 1.6.2

1. Fixed hadoop NameNode folder

### 1.6.1

1. Made env variables HADOOP_NN_URL

### 1.6.0

1. Made env variables KAFKA_URL and SCHEMA_REGISTRY_URL

## fh-analytics-sink

### crate-1.3.4

1. Fixed pool initialization

### crate-1.3.3

1. Improved uuid_tokenizer

### crate-1.3.2

1. Full-text indexing
2. Renamed all value fields

### crate-1.2.5

1. Partitioning and Sharding

### crate-1.2.1

1. Added CRATE_DB_ENABLED and PUSHER_ENABLED

### crate-1.2.0

1. Changed JDBC pooling to com.zaxxer:HikariCP

### crate-1.1.11

1. Increased batch size

### crate-1.1.10

1. Added patient_id clustering

### crate-1.1.9

1. Fixed connection pooling

### crate-1.1.7

1. SQL bulk inserts

### crate-1.1.4

1. Renamed columns

### crate-1.1.3

1. Fixed SQL

### crate-1.1.0

1. Modified TABLE aggregated_records

## fh-analytics-init-kafka

### 1.0.6

1. fun-health-hub-records -> fun-health-sensor-records