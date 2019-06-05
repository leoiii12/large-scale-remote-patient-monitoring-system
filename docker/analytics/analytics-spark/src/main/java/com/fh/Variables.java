package com.fh;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.log4j.Logger;

public final class Variables {
    static CachedSchemaRegistryClient schemaRegistryClient = null;
    static KafkaAvroDeserializer avroDeserializer = null;
    static KafkaAvroSerializer avroSerializer = null;
    static NormalRange normalRange = null;
}