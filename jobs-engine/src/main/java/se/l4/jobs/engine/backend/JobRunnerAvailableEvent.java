package se.l4.jobs.engine.backend;

import se.l4.commons.serialization.QualifiedName;
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
	private final QualifiedName name;

	public JobRunnerAvailableEvent(QualifiedName name)
	{
		this.name = name;
	}

	public QualifiedName getName()
	{
		return name;
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "{name=" + name + "}";
	}
}
