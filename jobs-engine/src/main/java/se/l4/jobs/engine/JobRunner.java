package se.l4.jobs.engine;

import java.util.Optional;
import java.util.OptionalInt;

import se.l4.jobs.JobData;

/**
 * Runner of jobs of a certain type. Runners are registered via {@link LocalJobs} and
 * are invoked when a job with data of their type is found.
 *
 * <p>
 * Runners may use any method in {@link JobEncounter} to fail or complete a job, but may opt
 * not to do so in which case any thrown exception will fail the job and an empty result
 * will be returned on success.
 *
 * @param <In>
 */
public interface JobRunner<In extends JobData>
{
	/**
	 * Run the job described by the encounter. This will be called when the
	 * job is being executed, the simplest implementation will perform the
	 * job and throw an exception on failure. An exception being thrown will
	 * result in the job automatically being retried later.
	 *
	 * <p>
	 * It is possible to control when the job fails using the job control
	 * functions in {@link JobEncounter}.
	 *
	 * <pre>
	 * try {
	 *   // Do something for the job here
	 *
	 *   // Indicate that the job has completed
	 *   encounter.complete(result);
	 * } catch(Throwable t) {
	 *   if(encounter.getAttempt() >= 5) {
	 *     // This is the fifth attempt - lets skip retrying it at this point
	 *     encounter.failNoRetry(t);
	 *   } else {
	 *     // Fail with the custom delay
	 *     encounter.fail(t, customDelay);
	 *   }
	 * }
	 * </pre>
	 *
	 * @param encounter
	 * @throws Exception
	 */
	void run(JobEncounter<In> encounter)
		throws Exception;

	default Optional<Delay> getDelay()
	{
		return Optional.empty();
	}

	default OptionalInt getMaxAttempts()
	{
		return OptionalInt.empty();
	}
}
