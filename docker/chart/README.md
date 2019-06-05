# Init the spark app image and sink app image
```bash
docker build -t "asia.gcr.io/jc1804-220407/fh-chart:1.3.4" .
docker push "asia.gcr.io/jc1804-220407/fh-chart:1.3.4"
```

# Change Log

# 1.3.4

1. Fixed AdwinAlert sensitivity

# 1.3.3

1. Added latestTimestamp for each records for query performance

# 1.3.1

1. Extended to 3 hours aggregated records
2. Expanded the panel size

# 1.3.0

1. Implemented AdwinAlert

# 1.2.8

1. Removed timestamp shifting

# 1.2.7

1. Implemented with chart.js
2. Renamed columns according to fh-analytics-sink

# 1.2.4

1. Implemented searching

# 1.2.3

1. Refactored

# 1.2.2

1. Fixed getPatientAlerts timestamp renamed to ts

# 1.2.0

1. Config map for src/assets/env.json
