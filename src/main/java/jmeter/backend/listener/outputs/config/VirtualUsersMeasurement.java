package jmeter.backend.listener.outputs.config;

/**
 * Constants (Tag, Field, Measurement) names for the virtual users measurement.
 * 
 *
 */
public interface VirtualUsersMeasurement {

	String MEASUREMENT_NAME = "virtualUsers";

	interface Tags {
	}

	interface Fields {
		String MIN_ACTIVE_THREADS = "minActiveThreads"; //Minimum active threads field.
		String MAX_ACTIVE_THREADS = "maxActiveThreads"; //Maximum active threads field.
		String MEAN_ACTIVE_THREADS = "meanActiveThreads"; //Mean active threads field.
		String STARTED_THREADS = "startedThreads"; //Started threads field.
		String FINISHED_THREADS = "finishedThreads"; //Finished threads field.
	}
}
