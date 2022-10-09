# Kafka Schema Evolution Compatibility
This project is to test Kafka schema evolution compatibility. This project has been built along with Confluent platform local. But hopefully will work with the other Kafka environments as well.

## Forward

* Produce messages in topic `t1-a`. 
```
kafka-avro-console-producer --bootstrap-server localhost:9092 --property schema.registry.url=http://localhost:8081 --topic t1-a \
--property value.schema='{"type":"record","namespace":"com.arijit","name":"Record","fields":[{"name":"f1","type":"string"}]}'

{"f1": "value1-a"}
```
* Check the message

```
kafka-avro-console-consumer --bootstrap-server localhost:9092 --from-beginning --topic t1-a --property schema.registry.url=http://localhost:8081

```
* Change the compatibility of the topic to `FORWARD` 

```
$ curl http://localhost:8081/config/t1-a-value

$ curl -X PUT -H "Content-Type: application/vnd.schemaregistry.v1+json" -H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" http://localhost:8081/config/t1-a-value -d '{  "compatibility": "FORWARD"}' 
```

* Check if the upgraded schema is compatible

```
$ jq -sR '.|{schema: .}' src/main/resources/forward/t1-a-value.0.avsc | curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" -H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" http://localhost:8081/compatibility/subjects/t1-a-value/versions/latest -d @- 

```
* Produce message with upgraded schema

```
kafka-avro-console-producer --bootstrap-server localhost:9092 --property schema.registry.url=http://localhost:8081 --topic t1-a \
--property value.schema='{"type":"record","namespace":"com.arijit","name":"Record","fields":[{"name":"f1","type":"string","arg.properties":{"regex":"[a-z]{5,6}"}},{"name":"f2","type":["null","string"],"arg.properties":{"regex":"[a-z]{5,6}"}}]}'

{"f1": "value1-a", "f2":{"string":"value2-a"}}
{"f1": "value1-a", "f2": null}

```
* Compile and Run the kafka consumer

```
mvn -D schemafile=t1-a-value.0.avsc -D schemapath=src/main/resources/forward clean compile package


mvn exec:java -Dexec.mainClass=com.arijit.Consumer -D topic=t1-a

....
{"f1": "value1-a"}

```


## Backward

* Produce messages in topic `t2-a`. 
```
kafka-avro-console-producer --bootstrap-server localhost:9092 --property schema.registry.url=http://localhost:8081 --topic t2-a \
--property value.schema='{"type":"record","namespace":"com.arijit","name":"Record","fields":[{"name":"f1","type":"string"}]}'

{"f1": "value1-a"}
```
* Check the message

```
kafka-avro-console-consumer --bootstrap-server localhost:9092 --from-beginning --topic t2-a --property schema.registry.url=http://localhost:8081

```
* Change the compatibility of the topic to `BACKWARD` 

```
$ curl http://localhost:8081/config/t2-a-value

$ curl -X PUT -H "Content-Type: application/vnd.schemaregistry.v1+json" -H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" http://localhost:8081/config/t2-a-value -d '{  "compatibility": "BACKWARD"}' 
```
* Compile and Run the kafka consumer

```
mvn -D schemafile=t1-a-value.0.avsc -D schemapath=src/main/resources/forward clean compile package


mvn exec:java -Dexec.mainClass=com.arijit.Consumer -D topic=t1-a

....
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}
key = , value = {"f1": "value1-a"}

```
* Produce more messages to check backward comapitibility

```
kafka-avro-console-producer --bootstrap-server localhost:9092 --property schema.registry.url=http://localhost:8081 --topic t2-a \
--property value.schema='{"type":"record","namespace":"com.arijit","name":"Record","fields":[{"name":"f1","type":"string"}]}'

{"f1": "value1-a"}
```

* Check if the upgraded schema is compatible

```
$ jq -sR '.|{schema: .}' src/main/resources/backward/t2-a-value.compatible.avsc | curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" -H "Accept: application/vnd.schemaregistry.v1+json, application/vnd.schemaregistry+json, application/json" http://localhost:8081/compatibility/subjects/t2-a-value/versions/latest -d @- 

```
* Upgrade the consumer with new schema. We can see the messages with default value in field `f2`
```
mvn -D schemafile=t2-a-value.0.avsc -D schemapath=src/main/resources/backward clean compile package


mvn exec:java -Dexec.mainClass=com.arijit.Consumer -D topic=t2-a

....

key = , value = {"f1": "value1-a", "f2": "default"}
key = , value = {"f1": "value1-a", "f2": "default"}
key = , value = {"f1": "value1-a", "f2": "default"}

```


* We can obviosly produce message with upgraded schema

```
kafka-avro-console-producer --bootstrap-server localhost:9092 --property schema.registry.url=http://localhost:8081 --topic t2-a \
--property value.schema='{"type":"record","namespace":"com.arijit","name":"Record","fields":[{"name":"f1","type":"string","arg.properties":{"regex":"[a-z]{5,6}"}},{"name":"f2","type":["null","string"],"arg.properties":{"regex":"[a-z]{5,6}"}}]}'

{"f1": "value1-a", "f2":{"string":"value2-a"}}
{"f1": "value1-a", "f2": null}

```

