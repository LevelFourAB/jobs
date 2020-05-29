package se.l4.jobs;

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
	JobBuilder add(JobData jobData);
}
