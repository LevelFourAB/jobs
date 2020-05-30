package se.l4.jobs;

/**
 * Builder for submitting a job.
 */
public interface JobBuilder
{
	/**
	 * Set the identifier of this job. This is required when using a
	 * {@link #withSchedule(Schedule)} that is repeatable.
	 *
	 * @param id
	 * @return
	 */
	JobBuilder withId(String id);

	/**
	 * Set when the job should be run.
	 *
	 * @param when
	 * @return
	 */
	JobBuilder withSchedule(When when);

	/**
	 * Set when the job should be run.
	 *
	 * @param schedule
	 * @return
	 */
	JobBuilder withSchedule(Schedule schedule);

	/**
	 * Submit the job.
	 *
	 * @return
	 */
	Job submit();
}
