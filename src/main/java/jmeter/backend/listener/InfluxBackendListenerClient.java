package jmeter.backend.listener;

import jmeter.backend.listener.outputs.config.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterContextService.ThreadCounts;
import org.apache.jmeter.util.Calculator;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.apache.jorphan.logging.LoggingManager;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.Query;
import org.influxdb.impl.TimeUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Current composite Backend listener writes JMeter metrics both to InfluxDB or ElasticSearch directly.
 * It has been designed as merge of two backend listener implementations:
 * - Elasticsearch Backend listener by Vadim Volkov (https://github.com/vadim-klimov/apache-jmeter-listener-elasticsearch)
 * - InfluxDB Backend listener by NovaTecConsulting (https://github.com/NovaTecConsulting/JMeter-InfluxDB-Writer/releases)
 */
public class InfluxBackendListenerClient extends AbstractBackendListenerClient implements Runnable {

    private static final org.apache.log.Logger LOGGER = LoggingManager.getLoggerForClass(); // Logger
    private static final String KEY_PROJECT_NAME = "projectName";
    private static final String KEY_TEST_TYPE = "testType";
	private static final String KEY_ENV_TYPE = "envType";
    private static final String KEY_BUILD = "buildID";
    private static final String KEY_LG_NAME = "loadGenerator";
    /**
     * +++++++++++++++++++++++++++++++++++++++++++++++++++++
     * +++++++++INFLUXDB Parameter Keys Block Start+++++++++
     * +++++++++++++++++++++++++++++++++++++++++++++++++++++
     */
	private static final String KEY_USE_REGEX_FOR_SAMPLER_LIST = "useRegexForSamplerList";
	private static final String KEY_SAMPLERS_LIST = "samplersList";
	private static final String KEY_CREATE_AGGREGATED_REPORT = "createAggregatedReport";
	private long testStart;
	private int testDuration;

	private static final String SEPARATOR = ";";
	private static final int ONE_MS_IN_NANOSECONDS = 1000000;

	private ScheduledExecutorService scheduler; //Scheduler for periodic metric aggregation.
	private String testType; // Test type.
	private String envType; // Test type.
	private String projectName; // Project name
	private String loadGenerator; // Load Generator name
    private String buildId;
	private String samplersList = ""; // List of samplers to record.
    private String regexForSamplerList;// Regex if samplers are defined through regular expression.
	private Set<String> samplersToFilter; // Set of samplers to record.
	InfluxDBConfig influxDBConfig; // InfluxDB configuration.
	private InfluxDB influxDB; // influxDB client.
	private Random randomNumberGenerator; // Random number generator
	private boolean isInfluxDBPingOk;
    /**
     * -----------------------------------------------------
     * ---------INFLUXDB Parameter Keys Block End---------
     * -----------------------------------------------------
     */


	/**
	 * Processes sampler results.
	 */
	public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext context) {
		for (SampleResult sampleResult : sampleResults) {
			getUserMetrics().add(sampleResult);
			Calculator calc = new Calculator(sampleResult.getSampleLabel());
			calc.addSample(sampleResult);
			double rate = calc.getRate();

			if (Double.compare(rate,Double.MAX_VALUE)==0){
				String abc = "#N/A";
				return;
			}
			String unit = "sec";
			if (rate < 1.0) {
				rate *= 60.0;
				unit = "min";
			}
			if (rate < 1.0) {
				rate *= 60.0;
				unit = "hour";
			}
			String abc = rate + "/" + unit;

			if ((null != regexForSamplerList && sampleResult.getSampleLabel().matches(regexForSamplerList)) || samplersToFilter.contains(sampleResult.getSampleLabel())) {
				Point point = Point.measurement(RequestMeasurement.MEASUREMENT_NAME).time(System.currentTimeMillis() * ONE_MS_IN_NANOSECONDS + getUniqueNumberForTheSamplerThread(), TimeUnit.NANOSECONDS)
						.tag(RequestMeasurement.Tags.REQUEST_NAME, sampleResult.getSampleLabel())
						.addField(RequestMeasurement.Fields.ERROR_COUNT, sampleResult.getErrorCount())
						.tag(RequestMeasurement.Tags.RESPONSE_CODE, sampleResult.getResponseCode())
						.addField(RequestMeasurement.Fields.RESPONSE_BYTES, sampleResult.getBytes())
						.addField(RequestMeasurement.Fields.REQUEST_BYTES, sampleResult.getSentBytes())
						.addField(RequestMeasurement.Fields.CONNECT_TIME, sampleResult.getConnectTime())
						.addField(RequestMeasurement.Fields.THREAD_NAME, sampleResult.getThreadName())
						.addField(RequestMeasurement.Fields.TPS_RATE, abc)
						.tag(KEY_PROJECT_NAME, projectName)
						.tag(KEY_ENV_TYPE, envType)
						.tag(KEY_TEST_TYPE, testType)
                        .tag(KEY_BUILD, buildId)
						.tag(KEY_LG_NAME, loadGenerator)
						.addField(RequestMeasurement.Fields.RESPONSE_TIME, sampleResult.getTime()).build();
				influxDB.write(influxDBConfig.getInfluxDatabase(), influxDBConfig.getInfluxRetentionPolicy(), point);
			}
		}
	}

    @Override
    public Arguments getDefaultParameters() {
        Arguments arguments = new Arguments();
		arguments.addArgument(KEY_PROJECT_NAME, "Test_Project");
		arguments.addArgument(KEY_ENV_TYPE, "null");
        arguments.addArgument(KEY_TEST_TYPE, "null");
        arguments.addArgument(KEY_LG_NAME, "Load_Generator_Name");
        arguments.addArgument(KEY_BUILD, "null");
        arguments.addArgument(InfluxDBConfig.KEY_INFLUX_DB_HOST, "localhost");
//        arguments.addArgument(InfluxDBConfig.KEY_INFLUX_PROTOCOL, "http");
        arguments.addArgument(InfluxDBConfig.KEY_INFLUX_DB_PORT, Integer.toString(InfluxDBConfig.DEFAULT_PORT));
        arguments.addArgument(InfluxDBConfig.KEY_INFLUX_DB_USER, "db_username");
        arguments.addArgument(InfluxDBConfig.KEY_INFLUX_DB_PASSWORD, "");
        arguments.addArgument(InfluxDBConfig.KEY_INFLUX_DB_DATABASE, InfluxDBConfig.DEFAULT_DATABASE);
        arguments.addArgument(InfluxDBConfig.KEY_RETENTION_POLICY, InfluxDBConfig.DEFAULT_RETENTION_POLICY);
        arguments.addArgument(KEY_SAMPLERS_LIST, ".*");
        arguments.addArgument(KEY_USE_REGEX_FOR_SAMPLER_LIST, "true");
        arguments.addArgument(KEY_CREATE_AGGREGATED_REPORT, "true");
//        arguments.addArgument(ElasticSearchConfig.DELIMITER, ElasticSearchConfig.DELIMITER);
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_ELASTICSEARCH_PROTOCOL, "http");
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_ELASTICSEARCH_HOST, null);
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_ELASTICSEARCH_PORT, "9200");
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_ELASTICSEARCH_USER, null);
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_ELASTICSEARCH_PASSWORD, null);
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_ELASTICSEARCH_INDEX, null);
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_ELASTICSEARCH_TYPE, null);
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_TIMEZONE_ID, "GMT");
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_RESULT_EXCLUDED_ATTRIBUTES, null);
//        arguments.addArgument(ElasticSearchConfig.PARAMETER_NAME_ELASTICSEARCH_CONN_SSL_TRUST_ALL_CERTS, "false");
        return arguments;
}

	@Override
	public void setupTest(BackendListenerContext context) throws Exception {
		testType = context.getParameter(KEY_TEST_TYPE, "null");
		envType = context.getParameter(KEY_ENV_TYPE, "null");
		randomNumberGenerator = new Random();
		projectName = context.getParameter(KEY_PROJECT_NAME, "Test_Project");
        loadGenerator = context.getParameter(KEY_LG_NAME, "loadGenerator");
        buildId = context.getParameter(KEY_BUILD, "null");

		setupInfluxClient(context);
		testStart = System.currentTimeMillis();
		influxDB.write(
				influxDBConfig.getInfluxDatabase(),
				influxDBConfig.getInfluxRetentionPolicy(),
				Point.measurement(TestStartEndMeasurement.MEASUREMENT_NAME).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
						.tag(TestStartEndMeasurement.Tags.TYPE, TestStartEndMeasurement.Values.STARTED)
						.tag(KEY_PROJECT_NAME, projectName)
					    .tag(KEY_LG_NAME, loadGenerator)
                        .tag(KEY_BUILD, buildId)
						.tag(KEY_TEST_TYPE, testType)
						.tag(KEY_ENV_TYPE, envType)
						.addField(TestStartEndMeasurement.Fields.duration, "0")
						.build());

		parseSamplers(context);
		scheduler = Executors.newScheduledThreadPool(1);

		scheduler.scheduleAtFixedRate(this, 1, 1, TimeUnit.SECONDS);
	}

	@Override
	public void teardownTest(BackendListenerContext context) throws Exception {
		LOGGER.info("Shutting down scheduler...");
		scheduler.shutdown();
		testDuration = (int)(System.currentTimeMillis() - testStart);

		try {
			influxDB.write(
					influxDBConfig.getInfluxDatabase(),
					influxDBConfig.getInfluxRetentionPolicy(),
					Point.measurement(TestStartEndMeasurement.MEASUREMENT_NAME).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
							.tag(TestStartEndMeasurement.Tags.TYPE, TestStartEndMeasurement.Values.FINISHED)
							.tag(KEY_PROJECT_NAME, projectName)
							.tag(KEY_LG_NAME, loadGenerator)
							.tag(KEY_BUILD, buildId)
							.tag(KEY_TEST_TYPE, testType)
							.tag(KEY_ENV_TYPE, envType)
							.addField(TestStartEndMeasurement.Fields.duration, String.valueOf(testDuration))
							.build());
		} catch (InfluxDBException e) {
			LOGGER.error("Error in tearDown",e);
		}

        if (context.getBooleanParameter(KEY_CREATE_AGGREGATED_REPORT, true)) {
            createAggregatedReport();
        }
		influxDB.disableBatch();
		try {
			scheduler.awaitTermination(30, TimeUnit.SECONDS);
			LOGGER.info("Scheduler has been terminated!");
		} catch (InterruptedException e) {
			LOGGER.error("Error waiting for end of scheduler");
		}

		samplersToFilter.clear();
		super.teardownTest(context);
	}

	/**
	 * Periodically writes virtual users metrics to influxDB.
	 */
	public void run() {
		try {
			ThreadCounts tc = JMeterContextService.getThreadCounts();
			addVirtualUsersMetrics(getUserMetrics().getMinActiveThreads(), getUserMetrics().getMeanActiveThreads(), getUserMetrics().getMaxActiveThreads(), tc.startedThreads, tc.finishedThreads);
		} catch (Exception e) {
			LOGGER.error("Failed writing to InfluxDB", e);
		}
	}

	/**
	 * Setup influxDB client.
	 *
	 * @param context
	 *            {@link BackendListenerContext}.
	 */
	private void setupInfluxClient(BackendListenerContext context) {
		try {
		influxDBConfig = new InfluxDBConfig(context);
		influxDB = InfluxDBFactory.connect(influxDBConfig.getInfluxDBURL(), influxDBConfig.getInfluxUser(), influxDBConfig.getInfluxPassword());
		influxDB.enableBatch(100, 5, TimeUnit.SECONDS);
		createDatabaseIfNotExistent();
		isInfluxDBPingOk = true;
		LOGGER.info("++++++ InfluxDB ping test: Success ++++++");
		} catch (RuntimeException e){
			isInfluxDBPingOk = false;
			LOGGER.error("------InfluxDB ping test: Failed------");
			LOGGER.info(ExceptionUtils.getStackTrace(e));

		}

	}

	/**
	 * Parses list of samplers.
	 *
	 * @param context
	 *            {@link BackendListenerContext}.
	 */
	private void parseSamplers(BackendListenerContext context) {
		samplersList = context.getParameter(KEY_SAMPLERS_LIST, "");
		samplersToFilter = new HashSet<String>();
		if (context.getBooleanParameter(KEY_USE_REGEX_FOR_SAMPLER_LIST, false)) {
			regexForSamplerList = samplersList;
		} else {
			regexForSamplerList = null;
			String[] samplers = samplersList.split(SEPARATOR);
			samplersToFilter = new HashSet<String>();
			for (String samplerName : samplers) {
				samplersToFilter.add(samplerName);
			}
		}
	}

	/**
	 * Write thread metrics.
	 */
	private void addVirtualUsersMetrics(int minActiveThreads, int meanActiveThreads, int maxActiveThreads, int startedThreads, int finishedThreads) {
		Builder builder = Point.measurement(VirtualUsersMeasurement.MEASUREMENT_NAME).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		builder.addField(VirtualUsersMeasurement.Fields.MIN_ACTIVE_THREADS, minActiveThreads);
		builder.addField(VirtualUsersMeasurement.Fields.MAX_ACTIVE_THREADS, maxActiveThreads);
		builder.addField(VirtualUsersMeasurement.Fields.MEAN_ACTIVE_THREADS, meanActiveThreads);
		builder.addField(VirtualUsersMeasurement.Fields.STARTED_THREADS, startedThreads);
		builder.addField(VirtualUsersMeasurement.Fields.FINISHED_THREADS, finishedThreads);
		builder.tag(KEY_PROJECT_NAME, projectName);
		builder.tag(KEY_ENV_TYPE, envType);
		builder.tag(KEY_TEST_TYPE, testType);
        builder.tag(KEY_BUILD, buildId);
		builder.tag(KEY_LG_NAME, loadGenerator);
  		influxDB.write(influxDBConfig.getInfluxDatabase(), influxDBConfig.getInfluxRetentionPolicy(), builder.build());
	}

	/**
	 * Creates the configured database in influxdb if it does not exist yet.
	 */
	private void createDatabaseIfNotExistent() {
		List<String> dbNames = influxDB.describeDatabases();
		if (!dbNames.contains(influxDBConfig.getInfluxDatabase())) {
			influxDB.createDatabase(influxDBConfig.getInfluxDatabase());
		}
	}

	private void createAggregatedReport() {
		 try {
        	String aggregateReportQuery =
                    "SELECT count(" + RequestMeasurement.Fields.RESPONSE_TIME + ") as \"aggregate_report_count\"," +
							"mean(" + RequestMeasurement.Fields.RESPONSE_TIME + ") as \"average\"," +
                            "median(" + RequestMeasurement.Fields.RESPONSE_TIME + ") as \"aggregate_report_median\"," +
                            "min(" + RequestMeasurement.Fields.RESPONSE_TIME + ") as \"aggregate_report_min\"," +
                            "max(" + RequestMeasurement.Fields.RESPONSE_TIME + ") as \"aggregate_report_max\"," +
                            "percentile(" + RequestMeasurement.Fields.RESPONSE_TIME + ",90) as \"aggregate_report_90%_line\"," +
                            "percentile(" + RequestMeasurement.Fields.RESPONSE_TIME + ",95) as \"aggregate_report_95%_line\"," +
                            "percentile(" + RequestMeasurement.Fields.RESPONSE_TIME + ",99) as \"aggregate_report_99%_line\"," +
                            "stddev(" + RequestMeasurement.Fields.RESPONSE_TIME + ") as \"aggregate_report_stddev\"," +
							"(sum("+RequestMeasurement.Fields.ERROR_COUNT+")/count("+RequestMeasurement.Fields.RESPONSE_TIME+"))*100 as \"aggregate_report_error%\","+
							"last(" + RequestMeasurement.Fields.TPS_RATE + ") as \"aggregate_report_rate\" " +
							"INTO \"" + AggregateReportMeasurement.MEASUREMENT_NAME + "\" " +
                            "FROM \"" + RequestMeasurement.MEASUREMENT_NAME + "\"" +
							"WHERE \"projectName\"='"+ projectName +"' AND \"envType\"='"+ envType +"' AND \"loadGenerator\"='"+ loadGenerator +"' AND time > '"+TimeUtil.toInfluxDBTimeFormat(testStart)+"' " +
							"GROUP BY \"" + RequestMeasurement.Tags.REQUEST_NAME + "\"," +
							          "\"" + KEY_BUILD + "\"," +
							          "\"" + KEY_PROJECT_NAME + "\"," +
							          "\"" + KEY_ENV_TYPE + "\"," +
							          "\"" + KEY_TEST_TYPE + "\"," +
							          "\"" + KEY_LG_NAME + "\"";
			//LOGGER.info(aggregateReportQuery);
			Query query = new Query(aggregateReportQuery, influxDBConfig.getInfluxDatabase());
            influxDB.query(query);
            LOGGER.info("Aggregate Report is created");
        }
        catch (InfluxDBException e){
            LOGGER.error("!!! Aggregate Report creation in InfluxDB is Failed !!!", e);
        }
    }
	/**
	 * Try to get a unique number for the sampler thread
	 */
	private int getUniqueNumberForTheSamplerThread() {
		return randomNumberGenerator.nextInt(ONE_MS_IN_NANOSECONDS);
	}
}

