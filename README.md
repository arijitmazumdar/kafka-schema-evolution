# Kafka Schema Evolution and it's effect on Kafka ecosystem
Schema registry plays a very important and pivotal role in Kafka world where producers can share data with consumers, while the schema is stored in schema registry. Unlike REST world, in Kafka the consumers and producers can upgrade the schema independently within some boundaries. These boundary is called schema evolution compatibility setting. Kafka supports many schema compatibility setting. Details can be found [here](https://docs.confluent.io/platform/current/schema-registry/avro.html#compatibility-types). Though schema compatibility is applicable for producers and consumers, but most of the complexity lies for the consumer. 

![kafka schema Registry](/images/Kafka-schema-flow.png)

Here we will go through will check the behavior of consumer, when schema is upgraded by Producer or by Consumer under different compatibility mode.

## Prerequisite
1. Confluent platform quickstart [ local ](https://docs.confluent.io/platform/current/platform-quickstart.html#prerequisites). 
2. `jq` for json parsing 
3. For managing connectors `kcctl`

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
--property value.schema=$(jq -c . src/main/resources/schema/forward/t1-a-value.0.avsc)

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
jq -sR '.|{schema: .}' src/main/resources/schema/forward/t1-a-value.0.avsc | \
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
--property value.schema=$(jq -c . src/main/resources/schema/forward/t1-a-value.compatible.avsc)

```
* Compile and Run the kafka consumer

```
mvn -D schemafile=t1-a-value.0.avsc \
-D schemapath=src/main/resources/schema/forward \
clean compile package


mvn exec:java -Dexec.mainClass=com.arijit.Consumer -Dexec.args="-t t1-a"

## Alternatingly 
mvn exec:java -Dexec.mainClass=com.arijit.GenericConsumer -Dexec.args="-t t1-a"

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
--property value.schema=$(jq -c . src/main/resources/schema/backward/t2-a-value.0.avsc)

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
-D schemapath=src/main/resources/schema/forward \
clean compile package


mvn exec:java -Dexec.mainClass=com.arijit.Consumer -Dexec.args="-t t1-a"

## Alternatingly 
mvn exec:java -Dexec.mainClass=com.arijit.GenericConsumer -Dexec.args="-t t1-a"

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
--property value.schema=$(jq -c . src/main/resources/schema/backward/t2-a-value.0.avsc)

```

* Check if the upgraded schema is compatible

```
jq -sR '.|{schema: .}' src/main/resources/schema/backward/t2-a-value.compatible.avsc | \
curl -X POST \
-H "Content-Type: application/vnd.schemaregistry.v1+json" \
-H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json"\
 http://localhost:8081/compatibility/subjects/t2-a-value/versions/latest -d @- 

```
* Upgrade the consumer with new schema. We can see the messages with default value in field `f2`
```
mvn -D schemafile=t2-a-value.0.avsc \
 -D schemapath=src/main/resources/schema/backward \
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
--property value.schema=$(jq -c . src/main/resources/schema/backward/t2-a-value.compatible.avsc)

```

## Kafka cli commands
It is not possible to run Kafka cli commands (e.g. kafka-avro-console-consumer, kafka-console-consumer) with schema compatibility mode. It always prints complete messages. One can always pass `--property value.schema` or `--property value.schema.id`, however it ignores these arguments.


## Kafka connect 
Majority of the popular Kafka Sink Connector ignores Schema Compatibility settings, and cannot run with a fixed schema version. Hence any change in the upstream schema may break the Kafka sink connector. In case of topics with `FORWARD` compatibility, Sink connectors may add SMT (Single message Transformation) `whitelist` in the sink connector to protect from the future upgrade of the schema.

E.g., consider a jdbc sink connector, consuming messages from a topic `pageviews` and upserting into a postgressql database. The topic is set at `FORWARD` compatibility. A new mandatory field is added by producer (which is a compatible change) can create a havoc in the connector. So developer should be cautious around this.
### Things in action

We will deploy and manage kafka connectors using a small little tool called `kcctl`

1. Push data using kafka datagen connector
```
kcctl apply -f src/main/resources/connect/pageview-datagen-avro.json 
```

2. Set the schema to FORWARD mode
```
curl -X PUT \
-H "Content-Type: application/vnd.schemaregistry.v1+json" \
-H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" \
http://localhost:8081/config/pageviews-value -d '{  "compatibility": "FORWARD"}'
```

3. deploy postgres sink connector
```
kcctl apply -f src/main/resources/connect/postgress-avro-sink.json
```

4. Push data with upgraded schema using 
```
kafka datagen connector
kcctl apply -f src/main/resources/connect/pageview-datagen-avro.json 
```

5. Check status of the postgres-sink connector failed, as it tried to add a NOT NULL column on database and failed..
```
kcctl describe connector postgres-sink

```
## KSQLDB
KSQLDB behaves in the same line as Kafka Consumer and Producer. Consider the example given earlier, Let's assume we create a `stream` from topic `pageviews` with the following command, while producer is publishing with original version.

Once producer upgrades the schema by adding a new mandatory field (say `page-category`), we will see no impact in the stream `pageviews` as well as downstream stream `pageviews_premium`. 

### Things in action

1. Publish data in `pageviews` topic with original schema
```
kcctl apply -f src/main/resources/connect/pageview-datagen-avro.json 
```
2. Craete a small KSQL pipeline
```
CREATE STREAM pageviews with (KAFKA_TOPIC='pageviews', VALUE_FORMAT='AVRO');


CREATE STREAM pageviews_premium with(KAFKA_TOPIC='pageviews-premium', VALUE_FORMAT='AVRO') as select * from pageviews where USERID='User_6';
```
3. Set the schema to FORWARD mode
```
curl -X PUT \
-H "Content-Type: application/vnd.schemaregistry.v1+json" \
-H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" \
http://localhost:8081/config/pageviews-value -d '{  "compatibility": "FORWARD"}'
```
4. At this point of time the schema for `pageviews-premium` is same as `pageviews` apart from all mandatory fields in `pageviews` have become optional in `pageviews-premium`. Also the schema evolution compatibility is not set at the subject level. Probably it will be better that schema and properties for downstream topics should explicitly set and not defined by KSQLDB.

```
curl -s http://localhost:8081/subjects/pageviews-premium-value/versions/latest/schema  | jq -c .

{"type":"record","name":"KsqlDataSourceSchema","namespace":"io.confluent.ksql.avro_schemas","fields":[{"name":"VIEWTIME","type":["null","long"],"default":null},{"name":"USERID","type":["null","string"],"default":null},{"name":"PAGEID","type":["null","string"],"default":null}],"connect.name":"io.confluent.ksql.avro_schemas.KsqlDataSourceSchema"}

#Evolution setting
curl http://localhost:8081/config/pageviews-premium-value

{"error_code":40408,"message":"Subject 'pageviews-premium-value' does not have subject-level compatibility configured"}
``` 
5. Upgrade `pageviews` topic with new schema 
```
kcctl apply -f src/main/resources/connect/pageview-datagen-avro-upgraded.json 
``` 
6. At this point of time there will no difference between streams `pageviews` and `pageview-premium`. However there are differences in the schema, as `pageviews-value` will have newly added `page-category` field, which `pageview-premium-value` won't have.


## Last few words
Schema compatibility and upgrade topic may look difficult if not confusing to many. Adding spice on the top, the effect of schema upgrade is not exactly same across the ecosystem. Here are some of the thoughts
1. Set the global schema compatibility as `FORWARD`, as majority of the times we see schema are owned by producers.
2. We never allow to change the compatibility setting of a topic, although it is technically feasible
3. There are some usecases (Request-response pattern), where the schema is governed by Consumers. In these cases we can make the schema read-only. So that producers don't insert different schema (though compatible) in the schema registry. This would require a small change of code for the producers.
4. If someone is using Confluent enterprise edition, they can turn on schema validation in Kafka broker. This will ensure that no garbage data is produced to the broker. 
