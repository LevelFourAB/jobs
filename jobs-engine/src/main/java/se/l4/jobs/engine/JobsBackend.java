package se.l4.jobs.engine;

import java.util.Optional;

/**
 * Backend that is responsible for accepting and telling the local jobs
 * instance to run certain jobs.
 */
public interface JobsBackend
{
	/**
	 * Generate the next identifier to use for a job.
	 *
	 * @return
	 */
	long nextId();

	/**
	 * Start this backend, allowing the backend to queue jobs for running.
	 *
	 * @param control
	 *   control that can be used to run jobs on the local instance
	 */
	void start(JobControl control);

	/**
	 * Stop this backend. After a backend is stopped it should no longer
	 * queue up jobs.
	 */
	void stop();

	/**
	 * Ask the backend to accept this job. The backend will queue this up and
	 * can later ask for the job to be run.
	 *
	 * @param job
	 */
	void accept(QueuedJob<?> job);

	/**
	 * Cancel a job unless it is currently running.
	 *
	 * @param id
	 */
	void cancel(long id);

	/**
	 * Get a job using the {@link QueuedJob#getKnownId()}.
	 *
	 * @param id
	 *   the known id of the job, never {@code null}
	 * @return
	 *   optional containing the job if found, or empty optional if not found
	 */
	Optional<QueuedJob<?>> getViaId(String id);
}
