This project is a fork of https://github.com/NovaTecConsulting/JMeter-InfluxDB-Writer project

This JMeter Plugin allows to write load test data on-the-fly to influxDB and to generate Aggregate Report data as separate InfluxDB measurement.

General changes in comparison to original project:

- some tags/fields/measurements are renamed
- InfluxDB tags "testType, buildId, loadGenerator" are added for better results analysis, especially being integrated into CI/CD process 
- Compatability with JMeter 2.13 is provided by using deprecated org.apache.jorphan.logging.LoggingManager
- sampleResult.getLatency() added
- Initial ping of InfluxDB host before data streaming
- InfluxDBException support
- Aggregate Report data generation as a separate InfluxDB measurement - it's useful for builds' test results comparisson.
