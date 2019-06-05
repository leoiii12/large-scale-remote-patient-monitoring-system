package com.fh;

import com.fh.avro.AvroAggregatedRecord;
import com.fh.avro.AvroSensorRecord;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.OutputMode;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.UUID;

import static org.apache.spark.sql.functions.*;

public class SparkMinutely {

    private static final String KAFKA_URL = System.getenv("KAFKA_URL") == null ? "localhost:9092" : System.getenv("KAFKA_URL");
    private static final String SCHEMA_REGISTRY_URL = System.getenv("SCHEMA_REGISTRY_URL") == null ? "http://localhost:8081" : System.getenv("SCHEMA_REGISTRY_URL");
    private static final String HADOOP_NN_URL = System.getenv("HADOOP_NN_URL") == null ? "hadoop-hadoop-hdfs-nn:9000" : System.getenv("HADOOP_NN_URL");

    public static void main(String[] args) {
        String inputTopicName = "fun-health-sensor-records";
        String outputTopicName = "fun-health-aggregated-records";

        String kafkaUrl = KAFKA_URL;
        String schemaRegistryUrl = SCHEMA_REGISTRY_URL;
        String hadoopNnUrl = HADOOP_NN_URL;

        SparkConf conf = new SparkConf();
        conf.set("spark.sql.shuffle.partitions", "50");
        conf.registerKryoClasses(new Class[]{SensorRecord.class, AggregatedRecord.class, Timestamp.class});

        SparkSession ss = SparkSession.builder()
                .appName("fh-analytics-SparkMinutely")
                .master("k8s://https://35.194.192.122")
                .config(conf)
                .getOrCreate();

        Dataset<Row> messages = ss.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaUrl)
                .option("kafka.max.poll.records", 10000)
                .option("subscribe", inputTopicName)
                .option("startingOffsets", "latest")
                .option("maxOffsetsPerTrigger", 500000)
                .load();

        HashMap<String, String> config = new HashMap<>();
        config.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");

        try {
            Dataset<SensorRecord> sensorRecords = messages

                    .map(row -> {
                        if (Variables.schemaRegistryClient == null)
                            Variables.schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 10000);

                        if (Variables.avroDeserializer == null)
                            Variables.avroDeserializer = new KafkaAvroDeserializer(Variables.schemaRegistryClient, config);

                        byte[] patientId = row.getAs(0);

                        AvroSensorRecord avroSensorRecord = (AvroSensorRecord) Variables.avroDeserializer.deserialize(inputTopicName, row.getAs(1));

                        return new SensorRecord(patientId, new Timestamp(avroSensorRecord.getTimestamp()), avroSensorRecord.getUnit(), avroSensorRecord.getValue());
                    }, Encoders.bean(SensorRecord.class));

            Dataset<AggregatedRecord> aggregatedSensorRecords = sensorRecords

                    .withWatermark("Timestamp", "1 minute")

                    // https://jaceklaskowski.gitbooks.io/mastering-spark-sql/spark-sql-performance-tuning-groupBy-aggregation.html
                    .groupBy(col("PatientId"), col("Unit"), window(col("Timestamp"), "1 minute"))

                    .agg(
                            avg("Value").alias("AvgValue"),
                            min("Value").alias("MinValue"),
                            max("Value").alias("MaxValue"),
                            col("window.start").cast("long").alias("Timestamp"),
                            count("Value").alias("Count"))
                    .as(Encoders.bean(AggregatedRecord.class));

            aggregatedSensorRecords

                    .map((MapFunction<AggregatedRecord, byte[]>) ar -> {
                        if (Variables.schemaRegistryClient == null)
                            Variables.schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 10000);

                        if (Variables.avroSerializer == null)
                            Variables.avroSerializer = new KafkaAvroSerializer(Variables.schemaRegistryClient, config);

                        AvroAggregatedRecord aggregatedRecord = new AvroAggregatedRecord(
                                Utils.getByteBufferFromUUID(UUID.randomUUID()),
                                ByteBuffer.wrap(ar.getPatientId()),
                                ar.getTimestamp() * 1000,
                                ar.getUnit(),
                                ar.getAvgValue(),
                                ar.getMinValue(),
                                ar.getMaxValue()
                        );

                        return Variables.avroSerializer.serialize(outputTopicName, aggregatedRecord);
                    }, Encoders.BINARY())

                    .writeStream()
                    .outputMode(OutputMode.Append())

                    .format("kafka")
                    .option("kafka.bootstrap.servers", kafkaUrl)
                    .option("topic", outputTopicName)
                    .option("checkpointLocation", "hdfs://" + hadoopNnUrl + "/SparkMinutely")

                    .start()
                    .awaitTermination();

        } catch (org.apache.spark.sql.streaming.StreamingQueryException e) {
            e.printStackTrace();
        }

        ss.stop();
    }
}