FROM openjdk:8-jdk-slim as sdk

WORKDIR /home/gradle/project/

COPY ./gradlew ./
COPY ./gradle/wrapper ./gradle/wrapper
COPY ./settings.gradle ./
COPY ./build.gradle ./
COPY ./SimulationRecords.json ./

RUN ./gradlew build --no-daemon

COPY ./src/ ./src/

RUN ./gradlew build --no-daemon

FROM openjdk:10-jre-slim

WORKDIR /app/

COPY --from=sdk /home/gradle/project/build/libs/fh-producer-1.0-SNAPSHOT.jar /app/
COPY --from=sdk /home/gradle/project/SimulationRecords.json /app/

CMD java -Xmx2g -jar fh-producer-1.0-SNAPSHOT.jar