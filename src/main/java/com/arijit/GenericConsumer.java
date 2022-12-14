package com.arijit;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import org.apache.avro.generic.GenericRecord;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.log4j.Logger;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;

public class GenericConsumer {

    private static final Properties props = new Properties();
    static Logger log = Logger.getLogger(Consumer.class.getName());

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws IOException {

        final Option propertiesFile = new Option("p", "property-file",
                true, "Properties file to connect to kafka broker");
        final Option topic = new Option("t", "topic",
                true, "Topic name to consume data");

        final Options options = new Options();
        options.addOption(propertiesFile);
        options.addOption(topic);

        // create the parser
        final CommandLineParser parser = new DefaultParser();
        String configFile = null;
        String topicName = null;
        try {
            // parse the command line arguments
            final CommandLine line = parser.parse(options, args);
            configFile = line.getOptionValue("p");
            topicName = line.getOptionValue("t");
        } catch (final ParseException exp) {
            // oops, something went wrong
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
            System.exit(1);
        }

        if (configFile == null) {
            // Backwards compatibility, assume localhost
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        } else {
            // Load properties from a local configuration file
            // Create the configuration file (e.g. at '$HOME/.confluent/java.config') with
            // configuration parameters
            // to connect to your Kafka cluster, which can be on your local host, Confluent
            // Cloud, or any other cluster.
            // Documentation at
            // https://docs.confluent.io/platform/current/tutorials/examples/clients/docs/java.html
            configFile = args[0];
            if (!Files.exists(Paths.get(configFile))) {
                throw new IOException(configFile + " not found.");
            } else {
                try (InputStream inputStream = new FileInputStream(configFile)) {
                    props.load(inputStream);
                }
            }
        }

        props.put(ConsumerConfig.GROUP_ID_CONFIG, "t1-a-consumer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        final Consumer<String, GenericRecord> consumer = new KafkaConsumer<String, GenericRecord>(props);
        consumer.subscribe(Arrays.asList(topicName));

        try {
            while (true) {
                final ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofSeconds(1));
                for (final ConsumerRecord<String, GenericRecord> record : records) {
                    System.out.printf("offset = %d, key = %s, value = %s \n", record.offset(), record.key(),
                            record.value());
                }
            }
        } finally {
            consumer.close();
        }
    }

}
