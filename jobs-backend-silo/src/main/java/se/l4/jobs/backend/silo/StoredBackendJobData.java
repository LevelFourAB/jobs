package se.l4.jobs.backend.silo;

import java.util.Optional;

import se.l4.commons.io.Bytes;
import se.l4.commons.serialization.AllowAny;
import se.l4.commons.serialization.Expose;
import se.l4.commons.serialization.QualifiedName;
import se.l4.commons.serialization.ReflectionSerializer;
import se.l4.commons.serialization.Use;
import se.l4.jobs.Schedule;
import se.l4.jobs.engine.backend.BackendJobData;

/**
 * Information about a job that has been stored in Silo.
 */
@Use(ReflectionSerializer.class)
public class StoredBackendJobData
	implements BackendJobData
{
	@Expose
	private final long id;

	@Expose
	private final String knownId;

	@Expose
	private final String dataNamespace;

	@Expose
	private final String dataName;

	@Expose
	private final Bytes data;

	@Expose
	private final long firstScheduledTime;

	@Expose
	private final long scheduledTime;

	@Expose
	@AllowAny
	private final Schedule schedule;

	@Expose
	private final int attempt;

	public StoredBackendJobData(
		@Expose("id") long id,
		@Expose("knownId") String knownId,
		@Expose("dataNamespace") String dataNamespace,
		@Expose("dataName") String dataName,
		@Expose("data") Bytes data,
		@Expose("firstScheduledTime") long firstScheduledTime,
		@Expose("scheduledTime") long scheduledTime,
		@Expose("schedule") Schedule schedule,
		@Expose("attempt") int attempt
	)
	{
		this.id = id;
		this.knownId = knownId;
		this.dataNamespace = dataNamespace;
		this.dataName = dataName;
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
	public QualifiedName getDataName()
	{
		return new QualifiedName(dataNamespace, dataName);
	}

	@Override
	public Bytes getData()
	{
		return data;
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

	@Override
	public BackendJobData withId(long id)
	{
		return new StoredBackendJobData(
			id,
			knownId,
			dataNamespace,
			dataName,
			data,
			firstScheduledTime,
			scheduledTime,
			schedule,
			attempt
		);
	}

	@Override
	public StoredBackendJobData withScheduledTime(long time)
	{
		return new StoredBackendJobData(
			id,
			knownId,
			dataNamespace,
			dataName,
			data,
			firstScheduledTime,
			time,
			schedule,
			attempt
		);
	}

	@Override
	public StoredBackendJobData withNextScheduledTime(long time)
	{
		return new StoredBackendJobData(
			id,
			knownId,
			dataNamespace,
			dataName,
			data,
			firstScheduledTime,
			time,
			schedule,
			1
		);
	}

	@Override
	public StoredBackendJobData withRetryAt(long time)
	{
		return new StoredBackendJobData(
			id,
			knownId,
			dataNamespace,
			dataName,
			data,
			firstScheduledTime,
			time,
			schedule,
			attempt + 1
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Optional<StoredBackendJobData> withNextScheduledTime()
	{
		return (Optional<StoredBackendJobData>) BackendJobData.super.withNextScheduledTime();
	}
}
