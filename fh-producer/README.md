# FH Producer

## Usage
## PLEASE FIRST START THE FH CLOUD

### FH Cloud
```bash
git clone https://bitbucket.org/fun-health/fh-cloud.git
cd fh-cloud/docker
docker-compose build
docker-compose up
``` 

### Simulation.Producer

#### Configs

```
export BATCH_SIZE=128
export CONCURRENT_CONNECTIONS=64
export NUM_OF_PEOPLE=1000000
export NUM_OF_PEOPLE_PER_ITERATION=250
export KAFKA_URL="192.168.50.10:9092,192.168.50.9:9092"
export SCHEMA_REGISTRY_URL="http://192.168.50.10:8081"
export SKIP_RATE=0.0
```

#### Build and run
```bash
./gradlew jar
java -jar build/libs/fh-producer-1.0-SNAPSHOT.jar
``` 

```bash
./gradlew -Dhttp.proxyHost=proxy.cse.cuhk.edu.hk -Dhttp.proxyPort=8000 -Dhttps.proxyHost=proxy.cse.cuhk.edu.hk -Dhttps.proxyPort=8000 jar
java -jar build/libs/fh-producer-1.0-SNAPSHOT.jar
```

```bash
docker build -t "asia.gcr.io/jc1804-220407/fh-producer:1.2.10" .
docker push "asia.gcr.io/jc1804-220407/fh-producer:1.2.10"
```

# Change Log

## 1.2.9

1. Only consecutive errors will reconfigure the producer

## 1.2.8

1. Don't destroy

## 1.2.3

1. Wait the servers ready

## 1.2.0

1. Renamed fun-health-hub-record-batch to fun-health-hub-record-batches

## 1.1.0

1. Block until the connection can be established

