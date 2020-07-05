package se.l4.jobs.engine.limits;

import java.time.Instant;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.l4.jobs.engine.backend.BackendJobData;

/**
 * Interface used to limit the number of jobs that are run at the same time.
 */
public interface JobLimiter
{
	/**
	 * Get the initial capacity.
	 *
	 * @return
	 */
	long getInitialCapacity();

	/**
	 * Get a publisher that will publish updates about how many more jobs that
	 * can be accepted.
	 *
	 * @return
	 */
	Flux<Long> capacity();

	/**
	 * Indicate that a new job is about to start.
	 *
	 * @param data
	 * @return
	 */
	Mono<Void> jobStarted(BackendJobData data);

	/**
	 * Indicate that a job has been completed.
	 *
	 * @param data
	 * @return
	 */
	Mono<Void> jobCompleted(BackendJobData data);

	/**
	 * Indicate that a job has failed.
	 *
	 * @param data
	 * @return
	 */
	Mono<Void> jobFailed(BackendJobData data);

	/**
	 * Indicate that a job has failed but will be retried.
	 *
	 * @param data
	 * @param instant
	 * @return
	 */
	Mono<Void> jobRetry(BackendJobData data, Instant instant);
}
