package se.l4.jobs.engine;

import se.l4.commons.types.TypeFinder;
import se.l4.jobs.JobData;
import se.l4.jobs.Jobs;
import se.l4.jobs.engine.internal.LocalJobsBuilderImpl;

/**
 * Extension of {@link Jobs} that keeps track of available runners and executes
 * jobs. This class should be used as a singleton in your system and can be
 * started and stopped as needed.
 *
 * <pre>
 * LocalJobs jobs = LocalJobs.builder()
 *   .withBackend(new InMemoryJobsBackend())
 *   .withTypeFinder(typeFinderToLocateRunners)
 *   .addRunner(new NotifyUserJobRunner())
 *   .build();
 *
 * // Start accepting jobs
 * jobs.start();
 * </pre>
 */
public interface LocalJobs
	extends Jobs
{
	/**
	 * Start this instance allowing it to accept and run jobs that are
	 * currently in the queue.
	 */
	void start();

	/**
	 * Stop this instance, it will no longer accept any new jobs or run
	 * existing jobs in the queue.
	 */
	void stop();

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
		 * Set the backend used for controlling what jobs are to be run.
		 *
		 * @param backend
		 * @return
		 */
		Builder withBackend(JobsBackend backend);

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
		 * Add a runner of jobs. The type of the data will be determined from
		 * the implemented {@link JobRunner} interface.
		 *
		 * @see #addRunner(Class, JobRunner)
		 * @param runner
		 */
		Builder addRunner(JobRunner<?> runner);

		/**
		 * Add a runner of jobs. This method can be used in cases where
		 * automatic detection in {@link #addRunner(JobRunner)} doesn't work.
		 *
		 * @param dataType
		 * @param runner
		 */
		<T extends JobData> Builder addRunner(Class<T> dataType, JobRunner<T> runner);

		/**
		 * Build the instance of {@link LocalJobs}.
		 *
		 * @return
		 *   instance of {@link LocalJobs}
		 */
		LocalJobs build();
	}
}
