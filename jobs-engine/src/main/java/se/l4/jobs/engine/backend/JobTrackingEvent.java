package se.l4.jobs.engine.backend;

public interface JobTrackingEvent
{
	/**
	 * Get the identifier of the job.
	 *
	 * @return
	 */
	long getId();
}
