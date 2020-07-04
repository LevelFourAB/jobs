package se.l4.jobs.engine.backend;

public class JobCancelEvent
	implements JobTrackingEvent
{
	public final long id;

	public JobCancelEvent(long id)
	{
		this.id = id;
	}

	public long getId()
	{
		return id;
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "{id=" + id + "}";
	}
}
