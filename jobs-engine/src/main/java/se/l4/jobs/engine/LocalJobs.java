package se.l4.jobs.engine;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import se.l4.commons.types.TypeFinder;
import se.l4.jobs.JobData;
import se.l4.jobs.Jobs;
import se.l4.jobs.engine.backend.JobsBackend;
import se.l4.jobs.engine.internal.LocalJobsBuilderImpl;
import se.l4.jobs.engine.limits.JobLimiter;
import se.l4.vibe.Vibe;

/**
 * Extension of {@link Jobs} that keeps track of available runners and executes
 * jobs. This class should be used as a singleton in your system and can be
 * started and stopped as needed.
 *
 * <pre>
 * Mono<LocalJobs> jobs = LocalJobs.builder()
 *   .withBackend(new InMemoryJobsBackend())
 *   .withTypeFinder(typeFinderToLocateRunners)
 *   .addRunner(new NotifyUserJobRunner())
 *   .start();
 *
 * // Wait for instance to start
 * jobs.block();
 * </pre>
 */
public interface LocalJobs
	extends Jobs
{
	/**
	 * Stop accepting jobs on this instance.
	 *
	 * @return
	 */
	Mono<Void> stop();

	/**
	 * Start building a new instance of {@link LocalJobs}.
	 *
	 * @return
	 *   instance of {@link Builder}
	 */
	static Builder builder()
	{
		return new LocalJobsBuilderImpl();
	}

	/**
	 * Builder for instances of {@link LocalJobs}.
	 */
	interface Builder
	{
		/**
		 * Use a custom limiter to limit the number of jobs that can be active
		 * at once.
		 *
		 * @param limiter
		 * @return
		 */
		Builder withLimiter(JobLimiter limiter);

		/**
		 * Set the backend used for controlling what jobs are to be run.
		 *
		 * @param backend
		 * @return
		 */
		Builder withBackend(Mono<JobsBackend> backend);

		/**
		 * Set a new default delay for jobs executed.
		 *
		 * @param delay
		 * @return
		 */
		Builder withDefaultDelay(Delay delay);

		/**
		 * Set a type finder to use to resolve job runners.
		 *
		 * @param finder
		 *   the finder to use to locate services
		 * @return
		 *   self
		 */
		Builder withTypeFinder(TypeFinder finder);

		/**
		 * Start collecting metrics in the given {@link Vibe} instance.
		 *
		 * @param vibe
		 *   instance to register on
		 * @param path
		 *   the path to register on
		 * @return
		 */
		Builder withVibe(Vibe vibe, String... path);

		/**
		 * Set the maximum number of threads to use for executing jobs. This
		 * will allow the {@link Scheduler} used for running jobs to
		 * keep between 0 and the given number of threads around.
		 *
		 * @param threads
		 *   the maximum number of threads to keep around
		 * @return
		 */
		Builder withExecutorThreads(int threads);

		/**
		 * Add a listener to this instance. The listener will be notified
		 * then jobs are scheduled, completed or failed on the instance.
		 *
		 * @param listener
		 *   listener that should be notified
		 * @return
		 */
		Builder addListener(JobListener listener);

		/**
		 * Add a runner of jobs. The type of the data will be determined from
		 * the implemented {@link JobRunner} interface.
		 *
		 * @see #addRunner(Class, JobRunner)
		 * @param runner
		 */
		Builder addRunner(JobRunner<?, ?> runner);

		/**
		 * Add a runner of jobs. This method can be used in cases where
		 * automatic detection in {@link #addRunner(JobRunner)} doesn't work.
		 *
		 * @param dataType
		 * @param runner
		 */
		<T extends JobData<?>> Builder addRunner(Class<T> dataType, JobRunner<T, ?> runner);

		/**
		 * Build and start the instance of {@link LocalJobs}.
		 *
		 * @return
		 *   instance of {@link LocalJobs}
		 */
		Mono<LocalJobs> start();
	}
}
