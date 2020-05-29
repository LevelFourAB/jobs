package se.l4.jobs.engine;

import se.l4.jobs.JobData;

/**
 * Information about job that has been queued up to be run.
 *
 * @param <D>
 */
public interface QueuedJob<D extends JobData>
{
	/**
	 * Get the identifier of the job.
	 *
	 * @return
	 */
	long getId();

	/**
	 * Get the data of the job.
	 *
	 * @return
	 */
	D getData();

	/**
	 * Get the UNIX time that this instance represents. If this is -1, this
	 * represent <i>the current time</i>.
	 *
	 * @return
	 *   timestamp in UNIX time
	 */
	long getTimestamp();

	/**
	 * Get the number of attempts to run this job has been made.
	 *
	 * @return
	 */
	int getAttempt();
}
