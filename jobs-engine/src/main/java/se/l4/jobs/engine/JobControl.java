package se.l4.jobs.engine;

import java.util.concurrent.CompletionStage;

/**
 * Control given to instances of {@link JobsBackend} to allow them to control
 * the job flow.
 */
public interface JobControl
{
	/**
	 * Run the given job. This will queue up that the job should be run but
	 * will block until a thread to run the job becomes available. This is done
	 * so that the engine doesn't get overwhelmed with jobs which has a risk of
	 * causing an {@link OutOfMemoryError}.
	 *
	 * @param job
	 * @return
	 *   a future that will complete when the job has finished running
	 */
	CompletionStage<Object> runJob(QueuedJob<?, ?> job);

	/**
	 * Register that the given job has been completed.
	 *
	 * @param id
	 * @param data
	 */
	void completeJob(long id, Object data);

	/**
	 * Register that a job has failed to complete.
	 *
	 * @param id
	 * @param t
	 */
	void failJob(long id, Throwable t);
}
