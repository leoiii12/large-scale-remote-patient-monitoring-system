export GOOGLE_APPLICATION_CREDENTIALS="/Users/leochoi/Downloads/jc1804-221807-8f647c45cd09.json"

#
```bash
./node_modules/.bin/tsc
docker build -t "asia.gcr.io/jc1804-220407/fh-measurer:1.1.1" .
docker push "asia.gcr.io/jc1804-220407/fh-measurer:1.1.1"
```

# Change Log

# 1.1.1

1. Renamed getRecentPatientIds to getRecentPatients

# 1.1.0

1. Modified endpoint to api