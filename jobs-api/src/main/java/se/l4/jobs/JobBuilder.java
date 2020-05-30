package se.l4.jobs;

/**
 * Builder for submitting a job.
 */
public interface JobBuilder
{
	/**
	 * Set the identifier of this job. This is required when using a
	 * {@link #schedule(Schedule)} that is repeatable.
	 *
	 * @param id
	 * @return
	 */
	JobBuilder id(String id);

	/**
	 * Set when the job should be run.
	 *
	 * @param when
	 * @return
	 */
	JobBuilder schedule(When when);

	/**
	 * Set when the job should be run.
	 *
	 * @param schedule
	 * @return
	 */
	JobBuilder schedule(Schedule schedule);

	/**
	 * Submit the job.
	 *
	 * @return
	 */
	Job submit();
}
