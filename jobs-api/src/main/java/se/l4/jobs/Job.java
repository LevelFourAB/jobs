package se.l4.jobs;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import reactor.core.publisher.Mono;

/**
 * Representation of a {@link Job} that has been submitted to the queue.
 */
public interface Job<D extends JobData<R>, R>
{
	/**
	 * Get the optional identifier of the job.
	 *
	 * @return
	 */
	Optional<String> getId();

	/**
	 * Get the data of the job.
	 *
	 * @return
	 */
	D getData();

	/**
	 * Get a {@link CompletionStage} that will complete if the job either
	 * completes successfully or fails permanently.
	 *
	 * @return
	 */
	Mono<R> result();

	/**
	 * Attempt to cancel the job. This will cancel the job if it is not
	 * currently running.
	 */
	Mono<Void> cancel();
}
