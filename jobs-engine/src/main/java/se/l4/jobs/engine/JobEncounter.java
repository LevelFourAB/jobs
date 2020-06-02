package se.l4.jobs.engine;

import java.time.Duration;
import java.time.Instant;

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
	 * Get the instant at which this job was first scheduled to run. For
	 * repeating jobs this is the first time the job was scheduled overall.
	 *
	 * <p>
	 * This is useful for failing jobs as an alternative to looking at the
	 * number of attempts run. Example:
	 *
	 * <pre>
	 * int daysAfterFirstSchedule = ChronoUnit.DAYS.between(encounter.getFirstScheduledTime(), ZonedDateTime.now());
	 * if(daysAfterFirstSchedule > 10) {
	 *   // If it's been more than 10 days retrying this job give up
	 *   encounter.failNoRetry(exception);
	 * }
	 * </pre>
	 *
	 * @return
	 */
	Instant getFirstScheduled();

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
