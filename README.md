# Large Scale Remote Patient Monitoring System

# Folder Structure

- fh-cloud, the Helm chart of the whole project, Please see the README
- docker, the src for the projects
  - analytics, the analytical projects (`Apache Spark`, `Apache Kafka`, `Apache Flink`, `Naive`)
  - chart, the displaying web pages (`Angular`)
  - endpoint, the API endpoint for `chart` to get records from `CrateDB`
  - measurer, the measurer to monitor the latency
- fh-producer, the high-performance program to generate high volume of sensor record for testing (`Kotlin`, `Disruptor`)
- infra, the commands to set up Google Cloud
- k8s - Some optional files for setting up k8s