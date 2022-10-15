# Kafka Schema Evolution and it's effect on Kafka ecosystem
Schema registry plays a very important and pivotal role in Kafka world where producers can share data with consumers, while the schema is stored in schema registry. Unlike REST world, in Kafka the consumers and producers can upgrade the schema independently within some boundaries. These boundary is called schema evolution compatibility setting. Kafka supports many schema compatibility setting. Details can be found [here](https://docs.confluent.io/platform/current/schema-registry/avro.html#compatibility-types). Though schema compatibility is applicable for producers and consumers, but most of the complexity lies for the consumer. 

![kafka schema Registry](/images/Kafka-schema-flow.png)

Here we will go through will check the behavior of consumer, when schema is upgraded by Producer or by Consumer under different compatibility mode.

## Prerequisite
1. Confluent platform quickstart [ local ](https://docs.confluent.io/platform/current/platform-quickstart.html#prerequisites). 
2. `jq` for json parsing 

## Kafka Consumer and Producer
### Scenario
1. In the first scenario Prodcucer is publishing (kafka-avro-producer) into topic `t1-a` using `t1-a-value.0.avsc`, and consumer (java program) consuming the same schema. The topic has been set to `FORWARD` compatibility mode. The producer upgrades the schema to `t1-a-value.compatible.avsc`, while consumer consumes using the old schema. In the following example it is demonstrated that consumer can consume the message without any issue, infact it is ignorant to the change of the schema.
2. In the first scenario Prodcucer is publishing (kafka-avro-producer) into topic `t2-a` using `t2-a-value.0.avsc`, and consumer (java program) consuming the same schema. The topic has been set to `BACKWARD` compatibility mode. The consumer upgrades the schema to `t2-a-value.compatible.avsc`, while producer produces using the old schema. In the following example it is demonstrated that consumer can consume the message with the new schema any issue, infact to it is ignored to the change of the schema.

### Forward

* Produce messages in topic `t1-a`. 
```
cat data/default-data.txt | \
kafka-avro-console-producer \
--bootstrap-server localhost:9092 \
--property schema.registry.url=http://localhost:8081 --topic t1-a \
--property value.schema=$(jq -c . src/main/resources/forward/t1-a-value.0.avsc)

```
* Check the message

```
kafka-avro-console-consumer \
--bootstrap-server localhost:9092 \
--from-beginning --topic t1-a \
--property schema.registry.url=http://localhost:8081

```
* Change the compatibility of the topic to `FORWARD` 

```
curl http://localhost:8081/config/t1-a-value

curl -X PUT \
-H "Content-Type: application/vnd.schemaregistry.v1+json" \
-H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" \
http://localhost:8081/config/t1-a-value \
-d '{  "compatibility": "FORWARD"}' 
```

* Check if the upgraded schema is compatible

```
jq -sR '.|{schema: .}' src/main/resources/forward/t1-a-value.0.avsc | \
curl -X POST \
-H "Content-Type: application/vnd.schemaregistry.v1+json" \
-H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" \
http://localhost:8081/compatibility/subjects/t1-a-value/versions/latest -d @- 

```
* Produce message with upgraded schema

```
cat data/upgrade-data.txt | \
kafka-avro-console-producer \
--bootstrap-server localhost:9092 \
--property schema.registry.url=http://localhost:8081 --topic t1-a \
--property value.schema=$(jq -c . src/main/resources/forward/t1-a-value.compatible.avsc)

```
* Compile and Run the kafka consumer

```
mvn -D schemafile=t1-a-value.0.avsc \
-D schemapath=src/main/resources/forward \
clean compile package


mvn exec:java -Dexec.mainClass=com.arijit.Consumer -D topic=t1-a

## Alternatingly 
mvn exec:java -Dexec.mainClass=com.arijit.GenericConsumer -D topic=t1-a

....
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}

```


### Backward

* Produce messages in topic `t2-a`. 
```
cat data/default-data.txt | \
kafka-avro-console-producer \
--bootstrap-server localhost:9092 \
--property schema.registry.url=http://localhost:8081 \
--topic t2-a \
--property value.schema=$(jq -c . src/main/resources/backward/t2-a-value.0.avsc)

```
* Check the message

```
kafka-avro-console-consumer \
--bootstrap-server localhost:9092 \
--from-beginning --topic t2-a \
--property schema.registry.url=http://localhost:8081

```
* Change the compatibility of the topic to `BACKWARD` 

```
curl http://localhost:8081/config/t2-a-value

curl -X PUT \
-H "Content-Type: application/vnd.schemaregistry.v1+json" \
-H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" \
http://localhost:8081/config/t2-a-value -d '{  "compatibility": "BACKWARD"}' 
```
* Compile and Run the kafka consumer

```
mvn -D schemafile=t2-a-value.0.avsc \
-D schemapath=src/main/resources/forward \
clean compile package


mvn exec:java -Dexec.mainClass=com.arijit.Consumer -D topic=t1-a

## Alternatingly 
mvn exec:java -Dexec.mainClass=com.arijit.GenericConsumer -D topic=t1-a

....
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}

```
* Produce more messages to check backward comapitibility

```
cat data/default-data.txt | \
kafka-avro-console-producer \
--bootstrap-server localhost:9092 \
--property schema.registry.url=http://localhost:8081 --topic t2-a \
--property value.schema=$(jq -c . src/main/resources/backward/t2-a-value.0.avsc)

```

* Check if the upgraded schema is compatible

```
jq -sR '.|{schema: .}' src/main/resources/backward/t2-a-value.compatible.avsc | \
curl -X POST \
-H "Content-Type: application/vnd.schemaregistry.v1+json" \
-H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json"\
 http://localhost:8081/compatibility/subjects/t2-a-value/versions/latest -d @- 

```
* Upgrade the consumer with new schema. We can see the messages with default value in field `f2`
```
mvn -D schemafile=t2-a-value.0.avsc \
 -D schemapath=src/main/resources/backward \
  clean compile package


mvn exec:java -Dexec.mainClass=com.arijit.Consumer -D topic=t2-a

## Alternatingly 
mvn exec:java -Dexec.mainClass=com.arijit.GenericConsumer -D topic=t2-a
....

key = , value = {"f1": "value1-a", "f2": "default"}
key = , value = {"f1": "value1-a", "f2": "default"}
key = , value = {"f1": "value1-a", "f2": "default"}

```


* We can ofcourse produce message with upgraded schema

```
cat data/upgrade-data-2.txt | \
kafka-avro-console-producer \
--bootstrap-server localhost:9092 \
--property schema.registry.url=http://localhost:8081 --topic t2-a \
--property value.schema=$(jq -c . src/main/resources/backward/t2-a-value.compatible.avsc)

```

## Kafka cli commands
It is not possible to run Kafka cli commands (e.g. kafka-avro-console-consumer, kafka-console-consumer) with schema compatibility mode. It always prints complete messages. One can always pass `--property value.schema` or `--property value.schema.id`, however it ignores these arguments.

## Kafka connect and KSQLDB
Majority of the popular Kafka Sink Connector ignores Schema Compatibility settings, and cannot run with a fixed schema version. Hence any change in the upstream schema may break the Kafka sink connector. In case of topics with `FORWARD` compatibility, Sink connectors may add SMT (Single message Transformation) `whitelist` in the sink connector to protect from the future upgrade of the schema.

E.g., consider a jdbc sink connector, consuming messages from a topic `pageviews` and upserting into a postgressql database. The topic is set at `FORWARD` compatibility. A new mandatory field is added by producer (which is a compatible change) can create a havoc in the connector. So developer should be cautious around this.

KSQLDB also behaves in the same line like Kafka Connect. Consider the example given earlier, Let's assume we create a `stream` from topic `pageviews` with the following command, while producer is publishing with original version.

```
CREATE STREAM pageviews with (KAFKA_TOPIC='pageviews', VALUE_FORMAT='AVRO');
```
Once producer upgrades the schema by adding a new mandatory field (say `page-name`), we will see in the `pageviews` stream the same column has been added, with previous rows as `null` values to the new column. This might be confusing to the new consumers at first as the newly added column is mandatory as far as the schema goes, while the stream contains `null` value. More importantly the developer creating a KSQL pipeline has to be cautious, as the pipeline may fail or have a cascading effect, if she selects all the column from `pageviews` using `*`.
Consider example below.

```
CREATE STREAM pageviews-json as select * from pageviews where page-category="premium" (KAFKA_TOPIC='pageviews-json', VALUE_FORMAT='AVRO');
 ```
Aparently, this cascading effect may seem liitle or even may look beneficial. However consider at the end of all KSQL pipeline the data will emit from Kafka, things may break incase it's a kafka sink connector, as explained a little earlier.
Also another interesting things may happen, if all the kafka topics in the pipeline does not have the same compatibility setting, the pipeline may break because of an compatible change in upstream topic is incompatible in the downstream topic.

A simple way to resolve this kind of issues will be use the column names explicitly, even if all the columns need to be transported to downstream `stream` or `table`.

## Last few words
Schema compatibility and upgrade topic may look difficult if not confusing to many. Adding spice on the top, the effect of schema upgrade is not exactly same across the ecosystem. Here are some of the things we have started following in our current team
1. We never allow to change the compatibility setting of a topic, although it is technically feasible
2. There are some usecases (Request-response pattern), where the schema is governed by Consumers. In these cases we can make the schema read-only. So that producers don't insert different schema (though compatible) in the schema registry. This would require a small change of code for the producers.
3. If someone is using Confluent enterprise edition, they can turn on schema validation in Kafka broker. This will ensure that no garbage data is produced to the broker. 
