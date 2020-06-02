package se.l4.jobs.engine;

import se.l4.jobs.Job;

/**
 * Listener for certain job activities.
 */
public interface JobListener
{
	/**
	 * New job has been scheduled to run.
	 *
	 * @param job
	 */
	void jobScheduled(Job<?, ?> job);

	/**
	 * Job has started execution.
	 *
	 * @param job
	 */
	void jobStarted(Job<?, ?> job);

	/**
	 * Job has been completed.
	 *
	 * @param job
	 */
	void jobCompleted(Job<?, ?> job);

	/**
	 * Job failed to execute.
	 *
	 * @param job
	 *   the job that fail
	 * @param willRetry
	 *   if the job will be retried later, if this is {@code false} this was
	 *   the last run of the job
	 */
	void jobFailed(Job<?, ?> job, boolean willRetry);
}
