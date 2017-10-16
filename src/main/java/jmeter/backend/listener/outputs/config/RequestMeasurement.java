package jmeter.backend.listener.outputs.config;

/**
 * Constants (Tag, Field, Measurement) names for the requests measurement.
 * 
 *
 */
public interface RequestMeasurement {

	String MEASUREMENT_NAME = "requestsRaw"; // Measurement name

	 interface Tags {
		String REQUEST_NAME = "requestName"; // Request name tag.
		String RESPONSE_CODE = "responseCode"; // Response code tag.
	}

	 interface Fields {
	 	String TPS_RATE = "tpsRate"; //TPS rate per transaction
	 	String REQUEST_BYTES = "requestBytes"; // Request bytes field.
		String RESPONSE_TIME = "responseTime"; // Response time field.
		String RESPONSE_BYTES = "responseBytes"; // Response bytes field.
		String CONNECT_TIME = "connectTime"; // Connect time field.
		String THREAD_NAME = "threadName"; //Thread name field
		String ERROR_COUNT = "errorCount"; //Error count field.
	}
}