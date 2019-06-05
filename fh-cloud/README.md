```bash
helm del --purge fh-cloud
kubectl delete namespace fh-cloud

helm install fh-cloud --name fh-cloud --namespace fh-cloud --debug
helm upgrade fh-cloud ./fh-cloud
```

# kubernetes dashboard
http://localhost:8001/api/v1/namespaces/kube-system/services/https:kubernetes-dashboard:/proxy/#!/overview?namespace=fh-cloud

# Spark
```bash
spark-submit \
   --master k8s://http://127.0.0.1:8001 \
   --deploy-mode cluster \
   --name fh-analytics-spark-adwin-alert \
   --class com.fh.SparkAdwinAlert \
   --conf spark.driver.extraJavaOptions=-Dlog4j.configuration=file:///home/confs/log4j.properties \
   --conf spark.locality.wait=0 \
   --conf spark.executor.instances=6 \
   --conf spark.executor.memory=12g \
   --conf spark.executor.cores=2 \
   --conf spark.executor.extraJavaOptions="-Dlog4j.configuration=file:///home/confs/log4j.properties -XX:+UseG1GC" \
   --conf spark.kubernetes.namespace="fh-cloud" \
   --conf spark.kubernetes.executor.request.cores="2000m" \
   --conf spark.kubernetes.container.image="asia.gcr.io/jc1804-220407/fh-analytics-spark-minutely:1.8.14" \
   --conf spark.kubernetes.driver.pod.name=fh-analytics-spark-adwin-alert \
   --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark \
   --conf spark.kubernetes.driverEnv.KAFKA_URL="fh-cloud-cp-kafka-headless.fh-cloud:9092" \
   --conf spark.kubernetes.driverEnv.SCHEMA_REGISTRY_URL="http://fh-cloud-cp-schema-registry.fh-cloud:8081" \
   --conf spark.kubernetes.driverEnv.HADOOP_NN_URL="fh-cloud-hadoop-hdfs-nn.fh-cloud:9000" \
   --conf spark.kubernetes.driverEnv.NUM_OF_PARTITIONS="48" \
   --conf spark.kubernetes.driverEnv.MAX_POOL_RECORDS="200000" \
   --conf spark.kubernetes.driverEnv.MAX_OFFSETS="4000000" \
   local:///home/jars/app.jar
```
```bash
kubectl delete pod fh-analytics-spark-adwin-alert --namespace fh-cloud
```

# Useful Commands
```bash
kubectl get namespace
kubectl get pod --namespace fh-cloud

kubectl port-forward fh-cloud-crate-0 4200 --namespace fh-cloud
kubectl port-forward fh-cloud-crate-0 5432 --namespace fh-cloud
kubectl port-forward fh-cloud-fh-analytics-minutely-0 8080 --namespace fh-cloud
kubectl port-forward fh-cloud-fh-analytics-minutely-0 1099 --namespace fh-cloud
kubectl port-forward fh-cloud-fh-analytics-sessionization-0 1099 --namespace fh-cloud
kubectl port-forward fh-analytics-spark-adwin-alert 4040 --namespace fh-cloud
kubectl port-forward fh-cloud-hadoop-hdfs-nn-0 50070 --namespace fh-cloud
kubectl port-forward fh-cloud-hadoop-hdfs-dn-0 50075 --namespace fh-cloud

docker run --privileged -it -v /var/run/docker.sock:/var/run/docker.sock jongallant/ubuntu-docker-client 
docker run --net=host --ipc=host --uts=host --pid=host -it --security-opt=seccomp=unconfined --privileged --rm -v /:/host alpine /bin/sh
chroot /host

export POD=fh-cloud-fh-etl-9965f967b-zw2l6 && kubectl delete pod $POD --namespace fh-cloud
export POD=fh-cloud-fh-etl-9965f967b-zw2l6 && kubectl logs $POD --namespace fh-cloud
```

# kafka
```bash
kafka-topics.sh --zookeeper "fh-cloud-zookeeper-headless.fh-cloud:2181" --describe 
kafka-console-consumer.sh --bootstrap-server "fh-cloud-kafka-headless.fh-cloud:9092" --topic fun-health-alerts
kafka-console-consumer.sh --bootstrap-server "fh-cloud-kafka-headless.fh-cloud:9092" --topic fun-health-aggregated-records
kafka-consumer-groups.sh --bootstrap-server "fh-cloud-kafka-headless.fh-cloud:9092" --list
kafka-log-dirs.sh --bootstrap-server "fh-cloud-kafka-headless.fh-cloud:9092" --describe
```