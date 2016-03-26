package se.l4.jobs;

/**
 * Representation of a job when a local worker runs it.
 * 
 * @author Andreas Holstenson
 *
 * @param <T>
 */
public interface Job<T>
{
	/**
	 * Get the data of the job.
	 * 
	 * @return
	 */
	T getData();
	
	/**
	 * Complete this job without a result.
	 * 
	 */
	void complete();
	
	/**
	 * Complete this job.
	 * 
	 */
	void complete(Object result);
	
	/**
	 * Fail this job with the given {@link Throwable} and never retry it.
	 * 
	 * @param t
	 */
	void failNoRetry(Throwable t);
	
	/**
	 * Fail this job with the given {@link Throwable}.
	 * 
	 * @param t
	 */
	void fail(Throwable t);
	
	/**
	 * Fail this job with the given {@link Throwable} and specify a
	 * minimum time to wait before retrying it.
	 * 
	 * @param t
	 * @param waitTimeInMs
	 */
	void fail(Throwable t, long waitTimeInMs);
	
	/**
	 * Get the attempt for this job.
	 * 
	 * @return
	 */
	int getAttempt();
	
	/**
	 * Get if this job will not be retried if it fails this time.
	 * 
	 * @return
	 */
	boolean isLastTry();
}
