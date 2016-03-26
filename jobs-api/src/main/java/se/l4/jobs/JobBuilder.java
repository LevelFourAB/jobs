package se.l4.jobs;

import se.l4.jobs.Jobs.When;

public interface JobBuilder
{
	/**
	 * Set when the job should be run.
	 * 
	 * @param when
	 * @return
	 */
	JobBuilder delay(When when);
	
	/**
	 * Set that the job can return a result.
	 * 
	 * @return
	 */
	JobBuilder withResult();
	
	/**
	 * Submit the job.
	 * 
	 * @return
	 */
	<T> SubmittedJob<T> submit();
}
