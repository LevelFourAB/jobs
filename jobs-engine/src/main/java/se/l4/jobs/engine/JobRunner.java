package se.l4.jobs.engine;

import se.l4.jobs.JobData;

/**
 * Runner of jobs of a certain type. Runners are registered via {@link LocalJobs} and
 * are invoked when a job with data of their type is found.
 *
 * <p>
 * Runners may use any method in {@link JobEncounter} to fail or complete a job, but may opt
 * not to do so in which case any thrown exception will fail the job and an empty result
 * will be returned on success.
 *
 * @param <In>
 */
public interface JobRunner<In extends JobData>
{
	void run(JobEncounter<In> encounter)
		throws Exception;
}
