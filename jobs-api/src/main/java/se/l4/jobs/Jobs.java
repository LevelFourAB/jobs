package se.l4.jobs;

import java.util.Optional;

/**
 * Jobs interface, for submitting and scheduling jobs.
 */
public interface Jobs
{
	/**
	 * Add a job that should be run.
	 *
	 * @param jobData
	 * @return
	 */
	<R, D extends JobData<R>> JobBuilder<D, R> add(D jobData);

	/**
	 * Get a job that is currently scheduled for execution via the
	 * {@link JobBuilder#withId(String) identifier} used to submit it.
	 *
	 * @param id
	 *   the identifier of the job
	 * @return
	 *   optional containing the job, or empty optional if job is not scheduled
	 */
	Optional<Job<?, ?>> getViaId(String id);
}
