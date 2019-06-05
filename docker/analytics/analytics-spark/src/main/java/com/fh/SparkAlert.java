package com.fh;

import com.fh.avro.AvroAlert;
import com.fh.avro.AvroSensorRecord;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.OutputMode;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class SparkAlert {

    private static final String KAFKA_URL = System.getenv("KAFKA_URL") == null ? "localhost:9092" : System.getenv("KAFKA_URL");
    private static final String SCHEMA_REGISTRY_URL = System.getenv("SCHEMA_REGISTRY_URL") == null ? "http://localhost:8081" : System.getenv("SCHEMA_REGISTRY_URL");
    private static final String HADOOP_NN_URL = System.getenv("HADOOP_NN_URL") == null ? "hadoop-hadoop-hdfs-nn:9000" : System.getenv("HADOOP_NN_URL");

    public static void main(String[] args) {
        String inputTopicName = "fun-health-sensor-records";
        String outputTopicName = "fun-health-alerts";

        String kafkaUrl = KAFKA_URL;
        String schemaRegistryUrl = SCHEMA_REGISTRY_URL;
        String hadoopNnUrl = HADOOP_NN_URL;

        SparkSession ss = SparkSession.builder()
                .appName("fh-analytics-SparkAlert")
                .master("k8s://https://35.194.192.122")
                .config("spark.sql.shuffle.partitions", "50")
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

            messages

                    .map((MapFunction<Row, byte[]>) row -> {
                        if (Variables.schemaRegistryClient == null)
                            Variables.schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 10000);

                        if (Variables.avroDeserializer == null)
                            Variables.avroDeserializer = new KafkaAvroDeserializer(Variables.schemaRegistryClient, config);

                        if (Variables.avroSerializer == null)
                            Variables.avroSerializer = new KafkaAvroSerializer(Variables.schemaRegistryClient, config);

                        if (Variables.normalRange == null)
                            Variables.normalRange = new NormalRange();

                        // Parse
                        byte[] patientId = row.getAs(0);
                        AvroSensorRecord avroSensorRecord = (AvroSensorRecord) Variables.avroDeserializer.deserialize(inputTopicName, row.getAs(1));

                        // Skip normal records
                        if (Variables.normalRange.isInRange(avroSensorRecord.getUnit(), avroSensorRecord.getValue())) {
                            return null;
                        }

                        AvroAlert alert = new AvroAlert(
                                Utils.getByteBufferFromUUID(UUID.randomUUID()),
                                ByteBuffer.wrap(patientId),
                                avroSensorRecord.getTimestamp(),
                                avroSensorRecord.getUnit(),
                                avroSensorRecord.getValue()
                        );

                        return Variables.avroSerializer.serialize(outputTopicName, alert);

                    }, Encoders.BINARY())

                    .filter(Objects::nonNull)

                    .writeStream()
                    .outputMode(OutputMode.Append())

                    .format("kafka")
                    .option("kafka.bootstrap.servers", kafkaUrl)
                    .option("topic", outputTopicName)
                    .option("checkpointLocation", "hdfs://" + hadoopNnUrl + "/SparkAlert")

                    .start()
                    .awaitTermination();

        } catch (org.apache.spark.sql.streaming.StreamingQueryException e) {
            e.printStackTrace();
        }

        ss.stop();
    }
}