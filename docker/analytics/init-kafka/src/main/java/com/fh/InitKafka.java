package com.fh;

import org.apache.kafka.clients.admin.*;

import java.util.*;
import java.util.stream.Collectors;

public class InitKafka {

    private static final String KAFKA_URL = System.getenv("KAFKA_URL") == null ? "localhost:9092" : System.getenv("KAFKA_URL");
    private static final String KAFKA_TOPIC = System.getenv("KAFKA_TOPIC") == null ? "fun-health-hub-record-batches,fun-health-sensor-records,fun-health-global-averages,fun-health-aggregated-records,fun-health-alerts" : System.getenv("KAFKA_TOPIC");
    private static final Integer NUM_OF_PARTITIONS = Integer.parseInt((System.getenv("NUM_OF_PARTITIONS") == null ? "8" : System.getenv("NUM_OF_PARTITIONS")));
    private static final Short REPLICATION_FACTORS = Short.parseShort((System.getenv("REPLICATION_FACTORS") == null ? "2" : System.getenv("REPLICATION_FACTORS")));

    public static void main(String[] args) {
        Properties config = new Properties();

        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_URL);

        try {
            AdminClient adminClient = AdminClient.create(config);

            Set<String> topics = adminClient.listTopics().names().get();

            List<NewTopic> newTopics = Arrays.stream(KAFKA_TOPIC.split(","))
                    .filter(s -> !topics.contains(s))
                    .map(topic -> {
                        int numbOfPartitions = NUM_OF_PARTITIONS;
                        short replicationFactors = REPLICATION_FACTORS;
                        return new NewTopic(topic, numbOfPartitions, replicationFactors);
                    })
                    .collect(Collectors.toList());

            // No more topics can be created
            if (newTopics.isEmpty()) {
                Map<String, TopicDescription> topicDescriptionMap = adminClient.describeTopics(Arrays.stream(KAFKA_TOPIC.split(",")).collect(Collectors.toList())).all().get();

                topicDescriptionMap.forEach((s, topicDescription) -> {
                    System.out.println(s);
                    System.out.println(topicDescription.toString());
                });

                System.exit(0);
            }

            CreateTopicsResult createTopicsResult = adminClient.createTopics(newTopics);

            createTopicsResult.all().get();

            adminClient.close();
        } catch (Exception e) {
            e.printStackTrace();

            System.exit(1);
        }
    }

}
