package se.l4.jobs;

/**
 * Extension of {@link Jobs} that allows {@link JobRunner}s to be registered.
 * 
 * @author Andreas Holstenson
 *
 */
public interface LocalJobs
	extends Jobs
{
	/**
	 * Register a runner of jobs. The type of the data will be determined
	 * from the implemented {@link JobRunner} interface.
	 * 
	 * @see #registerRunner(Class, JobRunner)
	 * @param runner
	 */
	void registerRunner(JobRunner<?> runner);
	
	/**
	 * Register a runner of jobs. This method can be used in cases where
	 * automatic detection in {@link #registerRunner(JobRunner)} doesn't work.
	 * 
	 * @param dataType
	 * @param runner
	 */
	<T> void registerRunner(Class<T> dataType, JobRunner<T> runner);
}
