# ufw

sudo ufw enable
sudo ufw allow ssh
sudo ufw allow http
sudo ufw allow 8080
sudo ufw default allow routed
sudo ufw allow in on cbr0 && sudo ufw allow out on cbr0

ls /etc/netplan/
sudo nano /etc/netplan/50-cloud-init.yaml
sudo netplan --debug apply

network:
    ethernets:
        eth0:
            dhcp4: no
            addresses: [192.168.1.121/24]
            gateway4: 192.168.1.1
            optional: true
    version: 2

# /var/snap/microk8s/current/args/docker-daemon.json

{
    "insecure-registries": [
        "localhost:32000"
    ],
    "default-ulimits": {
        "nofile": {
            "Name": "nofile",
            "Hard": 1048576,
            "Soft": 1048576
        }
    },
    "iptables": false
}

# /etc/sysctl.conf

map_count should be around 1 per 128 KB of system memory. For example, vm.max_map_count=2097152 on a 256 GB system. http://docs.actian.com/vector/4.2/index.html#page/User/Increase_max_map_count_Kernel_Parameter_(Linux).htm

49409968KB / 128KB = 386,015.375

vm.max_map_count = 386000
fs.file-max = 1048576
vm.swappiness = 5

# /etc/security/limits.conf

* soft nproc 1048576
* hard nproc 1048576
* soft nofile 1048576
* hard nofile 1048576
root soft nproc 1048576
root hard nproc 1048576
root soft nofile 1048576
root hard nofile 1048576

# /etc/pam.d/common-session

session required pam_limits.so

# Enable microk8s plugins

microk8s.enable dashboard dns storage ingress

# Init Google Cloud Registry Credentials and Helm
```bash
kubectl create secret docker-registry gcr-json-key \
--docker-server=asia.gcr.io \
--docker-username=_json_key \
--docker-password="$(cat infra/leo-server-233abdd664d6.json)" \
--docker-email=choimankin@gmail.com

kubectl patch serviceaccount default \
-p '{"imagePullSecrets": [{"name": "gcr-json-key"}]}'

kubectl get serviceaccount default
```

```bash
helm repo update

kubectl -n kube-system create sa tiller
kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller
helm init --service-account tiller
```

```bash
kubectl apply -f infra/gks-init.yaml
kubectl apply -f infra/gks-ssd.yaml
```

# Prometheus + Grafana
```bash
helm install stable/prometheus-operator --name prometheus-operator --values infra/prometheus-operator.yaml --namespace monitoring
helm upgrade prometheus-operator stable/prometheus-operator --values infra/prometheus-operator.yaml --namespace monitoring

```
```bash
kubectl port-forward svc/prometheus-operator-grafana 3000:80 --namespace monitoring
kubectl port-forward svc/prometheus-operator-prometheus 9090 --namespace monitoring

```
```bash
helm del --purge prometheus-operator
kubectl delete customresourcedefinitions prometheuses.monitoring.coreos.com prometheusrules.monitoring.coreos.com servicemonitors.monitoring.coreos.com alertmanagers.monitoring.coreos.com
kubectl delete namespace monitoring
```