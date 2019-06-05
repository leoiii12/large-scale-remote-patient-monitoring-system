# ssd
```bash
kubectl create clusterrolebinding cluster-admin-binding --clusterrole cluster-admin --user choimankin@gmail.com
kubectl apply -f local-ssd-provisioner.yaml
kubectl apply -f local-ssd.yaml
```

# cockroachdb
```bash
helm install stable/cockroachdb --values cockroachdb-values.yaml --name cockroachdb

kubectl run -it --rm cockroach-client --image=cockroachdb/cockroach --restart=Never --command -- ./cockroach sql --insecure --host cockroachdb-cockroachdb-public.default
```
```sql
CREATE DATABASE fh;
```

# fh-producer
```bash
kubectl apply -f fh-producer.yaml
```

# monitoring
```bash
kubectl port-forward fh-analytics-spark-minutely 4040
kubectl port-forward fh-analytics-spark-alert 4040
```
```bash
kubectl port-forward cockroachdb-cockroachdb-0 8080
kubectl port-forward cockroachdb-cockroachdb-0 26257
```
```bash
kubectl port-forward --namespace prometheus prometheus-1-grafana-0 3000
```