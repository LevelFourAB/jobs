package se.l4.jobs.engine.internal;

import java.util.Optional;

import se.l4.commons.io.Bytes;
import se.l4.commons.serialization.QualifiedName;
import se.l4.jobs.JobData;
import se.l4.jobs.Schedule;
import se.l4.jobs.engine.backend.BackendJobData;

/**
 * Implementation of {@link JobData}.
 */
public class QueuedJobImpl
	implements BackendJobData
{
	private final long id;
	private final String knownId;
	private final QualifiedName dataName;
	private final Bytes data;
	private final long firstScheduledTime;
	private final long scheduledTime;
	private final Schedule schedule;
	private final int attempt;

	public QueuedJobImpl(
		long id,
		String knownId,
		QualifiedName dataName,
		Bytes data,
		long firstScheduledTime,
		long scheduledTime,
		Schedule schedule,
		int attempt
	)
	{
		this.id = id;
		this.knownId = knownId;
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
		return dataName;
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
		return new QueuedJobImpl(
			id,
			knownId,
			dataName,
			data,
			firstScheduledTime,
			scheduledTime,
			schedule,
			attempt
		);
	}

	@Override
	public BackendJobData withScheduledTime(long time)
	{
		return new QueuedJobImpl(
			id,
			knownId,
			dataName,
			data,
			firstScheduledTime,
			time,
			schedule,
			attempt
		);
	}

	@Override
	public BackendJobData withNextScheduledTime(long time)
	{
		return new QueuedJobImpl(
			id,
			knownId,
			dataName,
			data,
			firstScheduledTime,
			time,
			schedule,
			1
		);
	}

	@Override
	public BackendJobData withRetryAt(long time)
	{
		return new QueuedJobImpl(
			id,
			knownId,
			dataName,
			data,
			firstScheduledTime,
			time,
			schedule,
			attempt + 1
		);
	}
}
