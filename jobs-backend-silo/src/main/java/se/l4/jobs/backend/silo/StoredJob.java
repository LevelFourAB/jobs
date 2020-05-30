package se.l4.jobs.backend.silo;

import se.l4.commons.serialization.AllowAny;
import se.l4.commons.serialization.Expose;
import se.l4.commons.serialization.ReflectionSerializer;
import se.l4.commons.serialization.Use;
import se.l4.jobs.JobData;
import se.l4.jobs.engine.QueuedJob;

/**
 * Information about a job that has been stored in Silo.
 */
@Use(ReflectionSerializer.class)
public class StoredJob
	implements QueuedJob<JobData>
{
	@Expose
	private final long id;

	@Expose
	@AllowAny
	private final Object data;

	@Expose
	private final long scheduledTime;

	@Expose
	private final int attempt;

	public StoredJob(
		@Expose("id") long id,
		@Expose("data") Object data,
		@Expose("scheduledTime") long scheduledTime,
		@Expose("attempt") int attempt
	)
	{
		this.id = id;
		this.data = data;
		this.scheduledTime = scheduledTime;
		this.attempt = attempt;
	}

	@Override
	public long getId()
	{
		return id;
	}

	public JobData getData()
	{
		return (JobData) data;
	}

	public long getScheduledTime()
	{
		return scheduledTime;
	}

	public int getAttempt()
	{
		return attempt;
	}
}
