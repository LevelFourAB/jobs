package se.l4.jobs.engine;

import java.time.Duration;

import se.l4.jobs.JobData;
import se.l4.jobs.When;

/**
 * Representation of a job when a local worker runs it.
 *
 * @param <D>
 */
public interface JobEncounter<D extends JobData<R>, R>
{
	/**
	 * Get the data of the job.
	 *
	 * @return
	 */
	D getData();

	/**
	 * Get the attempt for this job.
	 *
	 * @return
	 */
	int getAttempt();

	/**
	 * Complete this job without a result.
	 *
	 */
	void complete();

	/**
	 * Complete this job.
	 *
	 */
	void complete(R result);

	/**
	 * Fail this job with the given {@link Throwable} and never retry it.
	 *
	 * @param t
	 */
	void failNoRetry(Throwable t);

	/**
	 * Fail this job with the given {@link Throwable} and apply a default
	 * delay before retrying.
	 *
	 * @param t
	 */
	void fail(Throwable t);

	/**
	 * Fail this job with the given {@link Throwable} and apply a delay by
	 * asking a {@link Delay} to calculate it.
	 *
	 * @param t
	 * @param delay
	 */
	void fail(Throwable t, Delay delay);

	/**
	 * Fail this job with the given {@link Throwable} and specify a
	 * minimum time to wait before retrying it.
	 *
	 * @param t
	 * @param waitTime
	 */
	void fail(Throwable t, Duration waitTime);

	/**
	 * Fail this job with the given {@link Throwable} and specify a
	 * time when it should be retried.
	 *
	 * @param t
	 * @param when
	 */
	void fail(Throwable t, When when);
}
