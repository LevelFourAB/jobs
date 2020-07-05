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
public interface JobEncounter<D extends JobData<?>>
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
	 * Create an exception that will retry this job later based on the default
	 * retry policy. The job may fail permanently if the maximum number of
	 * retries is reached.
	 *
	 * @return
	 */
	JobRetryException retry();

	/**
	 * Create an exception that will retry this job later based on the default
	 * retry policy. The job may fail permanently if the maximum number of
	 * retries is reached.
	 *
	 * @param t
	 * @return
	 */
	JobRetryException retry(Throwable t);

	/**
	 * Create an exception that will retry this job after a certain
	 * {@link Delay}. The job may fail  permanently if the maximum number of
	 * retries according to the delay is reached.
	 *
	 * @param delay
	 * @return
	 */
	JobRetryException retry(Delay delay);

	/**
	 * Create an exception that will retry this job after a certain
	 * {@link Delay}. The job may fail permanently if the maximum number of
	 * retries according to the delay is reached.
	 *
	 * @param delay
	 * @param t
	 * @return
	 */
	JobRetryException retry(Delay delay, Throwable t);

	/**
	 * Create an exception that will retry this job after a minimum wait time.
	 * Will retry indefinitely.
	 *
	 * @param waitTime
	 * @return
	 */
	JobRetryException retry(Duration waitTime);

	/**
	 * Create an exception that will retry this job after a minimum wait time.
	 * Will retry indefinitely.
	 *
	 * @param waitTime
	 * @param t
	 * @return
	 */
	JobRetryException retry(Duration waitTime, Throwable t);

	/**
	 * Create an exception that will retry this job at the given time. May
	 * fail the job permanently if the {@link When} does not return a scheduled
	 * time.
	 *
	 * @param when
	 */
	JobRetryException retry(When when);

	/**
	 * Create an exception that will retry this job at the given time. May
	 * fail the job permanently if the {@link When} does not return a scheduled
	 * time.
	 *
	 * @param when
	 * @param t
	 * @return
	 */
	JobRetryException retry(When when, Throwable t);
}
