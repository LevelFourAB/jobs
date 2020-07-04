package se.l4.jobs.engine.backend;

import se.l4.commons.io.Bytes;

public class JobCompleteEvent
	implements JobTrackingEvent
{
	private final long id;
	private final Bytes data;

	public JobCompleteEvent(long id, Bytes data)
	{
		this.id = id;
		this.data = data;
	}

	/**
	 * Get the identifier of the job.
	 *
	 * @return
	 */
	public long getId()
	{
		return id;
	}

	/**
	 * Get the data of the job.
	 */
	public Bytes getData()
	{
		return data;
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "{id=" + id + "}";
	}
}
