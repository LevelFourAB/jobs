package se.l4.jobs.engine.backend;

import se.l4.jobs.engine.JobRunner;

/**
 * Event used to indicate that a certain {@link JobRunner} is available and
 * that it can execute jobs.
 */
public class JobRunnerAvailableEvent
	implements JobRunnerEvent
{
	/**
	 * The id of the runner that is available.
	 */
	private final String id;

	public JobRunnerAvailableEvent(String id)
	{
		this.id = id;
	}

	public String getId()
	{
		return id;
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "{id=" + id + "}";
	}
}
