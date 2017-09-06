package jmeter.backend.listener.outputs.config;

/**
 * Constants (Tag, Field, Measurement) names for the measurement that denotes start and end points of a load test.
 * 
 */
public interface TestStartEndMeasurement {

	String MEASUREMENT_NAME = "testStartEnd"; // Measurement name

	 interface Tags {
		String TYPE = "status"; // Start or End type tag.
	}
	 interface Fields {
	 	String duration = "overall_duration_in_ms";
	}
	 interface Values {
		String FINISHED = "finished";
		String STARTED = "started";
	}
}
