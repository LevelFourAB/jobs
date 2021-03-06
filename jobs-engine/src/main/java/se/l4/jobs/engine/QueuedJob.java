package se.l4.jobs.engine;

import java.util.Optional;

import se.l4.jobs.JobData;
import se.l4.jobs.Schedule;

/**
 * Information about job that has been queued up to be run.
 *
 * @param <D>
 */
public interface QueuedJob<D extends JobData<R>, R>
{
	/**
	 * Get the automatically generated identifier of the job.
	 *
	 * @return
	 */
	long getId();

	/**
	 * Get the known identifier for this job.
	 *
	 * @return
	 */
	Optional<String> getKnownId();

	/**
	 * Get the data of the job.
	 *
	 * @return
	 */
	D getData();

	/**
	 * Get the time at which this job was first scheduled to run.
	 *
	 * @return
	 */
	long getFirstScheduled();

	/**
	 * Get the time in milliseconds from the epoch for when this job should be
	 * run.
	 *
	 * @return
	 *   time in milliseconds from the epoch
	 */
	long getScheduledTime();

	/**
	 * Get the schedule for when this job runs.
	 *
	 * @return
	 */
	Optional<Schedule> getSchedule();

	/**
	 * Get the number of attempts to run this job has been made.
	 *
	 * @return
	 */
	int getAttempt();
}
