package se.l4.jobs.engine.backend;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.l4.commons.io.Bytes;
import se.l4.jobs.JobException;
import se.l4.jobs.JobNotFoundException;
import se.l4.jobs.engine.JobRetryException;

/**
 * Backend that is responsible for accepting new jobs, providing the jobs that
 * should be executed by the local instance and to propagate events.
 */
public interface JobsBackend
{
	/**
	 * Get a {@link Mono} that will stop this backend.
	 */
	Mono<Void> stop();

	/**
	 * Get a {@link Publisher} that contains jobs that should be run. The
	 * publisher should publish items when this local node should run them.
	 *
	 * <p>
	 * @{link LocalJobs} will only subscribe once to this publisher and will
	 * use back pressure to signal when it is ready for more work.
	 *
	 * @param events
	 *   publisher that publishes what runners are available
	 * @return
	 *   producer of jobs to be run
	 */
	Flux<BackendJobData> jobs(Publisher<JobRunnerEvent> events);

	/**
	 * Ask the backend to accept this job. The backend will queue this up and
	 * can later ask for the job to be run.
	 *
	 * @param job
	 */
	Mono<BackendJobData> accept(BackendJobData job);

	/**
	 * Get a {@link Publisher} that publishes events for the given job. The
	 * publisher should publish instances of {@link JobTrackingEvent} for the
	 * job.
	 *
	 * <p>
	 * The returned publisher must:
	 *
	 * <ul>
	 *   <li>If job is not queued or running: Publish a {@link JobFailureEvent} with a {@link JobNotFoundException}
	 *   <li>If job completes: Publish a {@link JobCompleteEvent}
	 *   <li>If job fails: Publish a {@link JobFailureEvent}
	 *   <li>If job cancels: Publish a {@link JobCancelEvent}
	 * </ul>
	 *
	 * @param events
	 *   publisher that publishes changes in subscriptions
	 * @return
	 */
	Flux<JobTrackingEvent> jobEvents(long id);

	/**
	 * Cancel a job unless it is currently running. This should remove the job
	 * from the queue and publish a {@link JobCancelEvent} as described for
	 * {@link #jobEvents(long)}.
	 *
	 * @param id
	 */
	Mono<Void> cancel(long id);

	/**
	 * Indicate that a job has completed successfully.
	 *
	 * @param id
	 * @param bytes
	 * @return
	 */
	Mono<Void> complete(long id, Bytes bytes);

	/**
	 * Indicate that a job has failed.
	 *
	 * @param id
	 * @param reason
	 * @return
	 */
	Mono<Void> fail(long id, JobException reason);

	/**
	 * Indicate that a job is going to be retried later.
	 *
	 * @param id
	 * @param reason
	 * @return
	 */
	Mono<Void> retry(long id, JobRetryException reason);

	/**
	 * Get a job using the {@link BackendJobData#getKnownId()}.
	 *
	 * @param id
	 *   the known id of the job, never {@code null}
	 * @return
	 *   optional containing the job if found, or empty optional if not found
	 */
	Mono<BackendJobData> getViaId(String id);
}
