package se.l4.jobs;

import java.util.concurrent.CompletableFuture;

/**
 * Builder for submitting a job.
 */
public interface JobBuilder
{
	/**
	 * Set when the job should be run.
	 *
	 * @param when
	 * @return
	 */
	JobBuilder schedule(When when);

	/**
	 * Submit the job.
	 *
	 * @return
	 */
	<T> CompletableFuture<T> submit();
}
