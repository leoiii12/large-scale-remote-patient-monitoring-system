package com.fh;

import com.fh.avro.AvroAdwinAlert;
import com.fh.avro.AvroSensorRecord;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.api.java.function.MapGroupsWithStateFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.GroupState;
import org.apache.spark.sql.streaming.GroupStateTimeout;
import org.apache.spark.sql.streaming.OutputMode;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

public final class SparkAdwinAlert {

    private static final String KAFKA_URL = System.getenv("KAFKA_URL") == null ? "localhost:9092" : System.getenv("KAFKA_URL");
    private static final String SCHEMA_REGISTRY_URL = System.getenv("SCHEMA_REGISTRY_URL") == null ? "http://localhost:8081" : System.getenv("SCHEMA_REGISTRY_URL");
    private static final String HADOOP_NN_URL = System.getenv("HADOOP_NN_URL") == null ? "hadoop-hadoop-hdfs-nn:9000" : System.getenv("HADOOP_NN_URL");
    private static final String NUM_OF_PARTITIONS = (System.getenv("NUM_OF_PARTITIONS") == null ? "20" : System.getenv("NUM_OF_PARTITIONS"));
    private static final Integer MAX_POOL_RECORDS = Integer.parseInt(System.getenv("MAX_POOL_RECORDS") == null ? "50000" : System.getenv("MAX_POOL_RECORDS"));
    private static final Integer MAX_OFFSETS = Integer.parseInt(System.getenv("MAX_OFFSETS") == null ? "500000" : System.getenv("MAX_OFFSETS"));

    public static UUID toUUID(ByteBuffer patientID) {
        patientID.position(0);
        long high = patientID.getLong();
        long low = patientID.getLong();
        return new UUID(high, low);
    }

    public static void main(String[] args) throws Exception {
        String inputTopicName = "fun-health-sensor-records";
        String outputTopicName = "fun-health-adwin-alerts";

        String kafkaUrl = KAFKA_URL;
        String schemaRegistryUrl = SCHEMA_REGISTRY_URL;
        String hadoopNnUrl = HADOOP_NN_URL;

        SparkSession ss = SparkSession.builder()
                .appName("fh-analytics-SparkAdwinAlert")
                .master("k8s://https://35.194.192.122")
                .config("spark.sql.shuffle.partitions", NUM_OF_PARTITIONS)
                .config("spark.hadoop.dfs.client.use.datanode.hostname", "true")
                .getOrCreate();

        Dataset<Row> messages = ss.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaUrl)
                .option("kafka.max.poll.records", MAX_POOL_RECORDS)
                .option("subscribe", inputTopicName)
                .option("startingOffsets", "latest")
                .option("maxOffsetsPerTrigger", MAX_OFFSETS)
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

            MapGroupsWithStateFunction<String, SensorRecord, Adwin, AdwinAlert> stateUpdateFunc =
                    new MapGroupsWithStateFunction<String, SensorRecord, Adwin, AdwinAlert>() {
                        @Override
                        public AdwinAlert call(
                                String sessionId, Iterator<SensorRecord> records, GroupState<Adwin> state) {

                            Adwin adwin;

                            AdwinAlert alert = new AdwinAlert(
                                    "".getBytes(),
                                    0L,
                                    0L,
                                    "",
                                    0.0,
                                    0,
                                    0.0,
                                    false
                            );

                            ArrayList<SensorRecord> arr = new ArrayList<SensorRecord>();

                            while (records.hasNext()) {
                                arr.add(records.next());
                            }

                            if (state.exists()) {
                                adwin = state.get();
                                if (adwin.getWidth() > 100) {
                                    adwin.deleteElement();
                                }
                            } else {
                                if (arr.size() > 0 && arr.get(0) != null)
                                    adwin = new Adwin(0.3, arr.get(0).getUnit());
                                else
                                    return alert;
                            }

                            arr.sort((r1, r2) -> r1.getTimestamp().compareTo(r2.getTimestamp()));
                            for (SensorRecord r : arr) {
                                alert.setPatientId(r.getPatientId());
                                alert.setUnit(r.getUnit());
                                alert.setValue(r.getValue());

                                if (adwin.update(r.getValue(), r.getTimestamp().getTime())) {
                                    alert.setFrom(adwin.startTime());
                                    alert.setTo(adwin.endTime());
                                    alert.setAlert(true);
                                }
                            }

                            alert.setWidth(adwin.getWidth());
                            alert.setSum(adwin.getSum());

                            state.update(adwin);

                            return alert;
                        }
                    };

            Dataset<AdwinAlert> sessionUpdates = sensorRecords
                    .groupByKey(
                            new MapFunction<SensorRecord, String>() {
                                @Override
                                public String call(SensorRecord record) {
                                    return toUUID(ByteBuffer.wrap(record.getPatientId())).toString() + record.getUnit();
                                }
                            }, Encoders.STRING())
                    .mapGroupsWithState(
                            stateUpdateFunc,
                            Encoders.bean(Adwin.class),
                            Encoders.bean(AdwinAlert.class),
                            GroupStateTimeout.ProcessingTimeTimeout())
                    .filter(r -> (r.getAlert()));

            sessionUpdates
                    .map((MapFunction<AdwinAlert, byte[]>) row -> {
                        if (Variables.schemaRegistryClient == null)
                            Variables.schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 10000);

                        if (Variables.avroSerializer == null)
                            Variables.avroSerializer = new KafkaAvroSerializer(Variables.schemaRegistryClient, config);

                        AvroAdwinAlert alert = new AvroAdwinAlert(
                                Utils.getByteBufferFromUUID(UUID.randomUUID()),
                                ByteBuffer.wrap(row.getPatientId()),
                                row.getUnit(),
                                row.getFrom(),
                                row.getTo()
                        );

                        return Variables.avroSerializer.serialize(outputTopicName, alert);
                    }, Encoders.BINARY())

                    .writeStream()
                    .outputMode(OutputMode.Update())

                    .format("kafka")
                    .option("kafka.bootstrap.servers", kafkaUrl)
                    .option("topic", outputTopicName)
                    .option("checkpointLocation", "hdfs://" + hadoopNnUrl + "/SparkAdwinAlert")

                    .start()
                    .awaitTermination();

        } catch (org.apache.spark.sql.streaming.StreamingQueryException e) {
            e.printStackTrace();
        }

        ss.stop();
    }

}