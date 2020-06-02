package se.l4.jobs;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
	 * Get a {@link CompletionStage} that will complete if the job either
	 * completes successfully or fails permanently.
	 *
	 * @return
	 */
	CompletableFuture<R> result();

	/**
	 * Attempt to cancel the job. This will cancel the job if it is not
	 * currently running.
	 */
	void cancel();
}
