package se.l4.jobs.engine.backend;

import se.l4.jobs.JobException;

public class JobFailureEvent
	implements JobTrackingEvent
{
	public final long id;
	public final JobException exception;

	public JobFailureEvent(long id, JobException exception)
	{
		this.id = id;
		this.exception = exception;
	}

	public long getId()
	{
		return id;
	}

	public JobException getException()
	{
		return exception;
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "{id=" + id + ", exception=" + exception +"}";
	}
}
