package se.l4.jobs.engine;

import java.util.concurrent.ThreadPoolExecutor;

import se.l4.commons.types.TypeFinder;
import se.l4.jobs.JobData;
import se.l4.jobs.Jobs;
import se.l4.jobs.engine.internal.LocalJobsBuilderImpl;
import se.l4.vibe.Vibe;

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
		 * will allow the {@link ThreadPoolExecutor} used for running jobs to
		 * keep between 1 and the given number of threads around.
		 *
		 * <p>
		 * If this or {@link #withExecutorThreads(int, int)} isn't used this
		 * will default to the number on cores of the current machine times 2.
		 *
		 * @param threads
		 *   the maximum number of threads to keep around
		 * @return
		 */
		Builder withExecutorThreads(int threads);

		/**
		 * Set the a minimum and maximum number of threads to use for
		 * executing jobs.
		 *
		 * <p>
		 * If this or {@link #withExecutorThreads(int)} isn't used this will
		 * default to the number of cores on the current machine times 2.
		 *
		 * @param minThreads
		 *   the minimum number of threads to keep around
		 * @param maxThreads
		 *   the maximum number of threads to keep around
		 * @return
		 */
		Builder withExecutorThreads(int minThreads, int maxThreads);

		/**
		 * Set the number of jobs that will be kept in the queue of the
		 * {@link ThreadPoolExecutor} used to run jobs.
		 *
		 * <p>
		 * If this isn't specified it will be set to the maximum number of
		 * threads.
		 *
		 * @param queueSize
		 *   the maximum number of jobs to keep in the queue
		 * @return
		 */
		Builder withExecutorQueueSize(int queueSize);

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
		 * Build the instance of {@link LocalJobs}.
		 *
		 * @return
		 *   instance of {@link LocalJobs}
		 */
		LocalJobs build();
	}
}
