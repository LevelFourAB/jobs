package se.l4.jobs.backend.silo;

import java.util.Optional;

import se.l4.commons.serialization.AllowAny;
import se.l4.commons.serialization.Expose;
import se.l4.commons.serialization.ReflectionSerializer;
import se.l4.commons.serialization.Use;
import se.l4.jobs.JobData;
import se.l4.jobs.Schedule;
import se.l4.jobs.engine.QueuedJob;

/**
 * Information about a job that has been stored in Silo.
 */
@Use(ReflectionSerializer.class)
public class StoredJob
	implements QueuedJob<JobData<Object>, Object>
{
	@Expose
	private final long id;

	@Expose
	private final String knownId;

	@Expose
	@AllowAny
	private final Object data;

	@Expose
	private final long firstScheduledTime;

	@Expose
	private final long scheduledTime;

	@Expose
	@AllowAny
	private final Schedule schedule;

	@Expose
	private final int attempt;

	public StoredJob(
		@Expose("id") long id,
		@Expose("knownId") String knownId,
		@Expose("data") Object data,
		@Expose("firstScheduledTime") long firstScheduledTime,
		@Expose("scheduledTime") long scheduledTime,
		@Expose("schedule") Schedule schedule,
		@Expose("attempt") int attempt
	)
	{
		this.id = id;
		this.knownId = knownId;
		this.data = data;
		this.firstScheduledTime = firstScheduledTime;
		this.scheduledTime = scheduledTime;
		this.schedule = schedule;
		this.attempt = attempt;
	}

	@Override
	public long getId()
	{
		return id;
	}

	@Override
	public Optional<String> getKnownId()
	{
		return Optional.ofNullable(knownId);
	}

	@Override
	@SuppressWarnings("unchecked")
	public JobData<Object> getData()
	{
		return (JobData<Object>) data;
	}

	@Override
	public long getFirstScheduled()
	{
		return firstScheduledTime;
	}

	@Override
	public long getScheduledTime()
	{
		return scheduledTime;
	}

	@Override
	public Optional<Schedule> getSchedule()
	{
		return Optional.ofNullable(schedule);
	}

	@Override
	public int getAttempt()
	{
		return attempt;
	}
}
