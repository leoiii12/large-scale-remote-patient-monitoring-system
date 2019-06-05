FROM openjdk:10-jre-slim

ENV MAIN_CLASS="com.fh.ETLKt"
ENV HEAP_OPTIONS="-Xmx2g -Xms2g"

WORKDIR /app/

COPY /build/libs/analytics-kstreams-1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT java -Dcom.sun.management.jmxremote \
                -Dcom.sun.management.jmxremote.authenticate=false \
                -Dcom.sun.management.jmxremote.ssl=false \
                -Dcom.sun.management.jmxremote.local.only=false \
                -Dcom.sun.management.jmxremote.port=1099 \
                -Dcom.sun.management.jmxremote.rmi.port=1099 \
                -Djava.rmi.server.hostname=127.0.0.1 \
                ${HEAP_OPTIONS} \
                -cp app.jar ${MAIN_CLASS}