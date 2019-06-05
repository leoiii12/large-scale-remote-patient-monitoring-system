# Init the spark app image and sink app image
```bash
docker build -t "asia.gcr.io/jc1804-220407/fh-endpoint:1.6.4" .
docker push "asia.gcr.io/jc1804-220407/fh-endpoint:1.6.4"
```

# Change Log

# 1.6.2

1. Added getPatientAdwinAlerts

# 1.5.8

1. Independent patient_ids table

# 1.5.7

1. Full-text search optimization

# 1.5.3

1. Renamed columns according to fh-analytics-sink

# 1.5.1

1. Renamed getRecentPatientIds to getRecentPatients

# 1.5.0

1. Implemented getPatients

# 1.4.2

1. Fixed errors do not return client

# 1.4.0

1. /endpoint/* -> /api/*