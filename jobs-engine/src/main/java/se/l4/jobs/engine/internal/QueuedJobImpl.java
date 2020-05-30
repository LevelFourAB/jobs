package se.l4.jobs.engine.internal;

import java.util.Optional;

import se.l4.jobs.JobData;
import se.l4.jobs.Schedule;
import se.l4.jobs.engine.QueuedJob;

/**
 * Implementation of {@link QueuedJob}.
 *
 * @param <T>
 */
public class QueuedJobImpl<T extends JobData>
	implements QueuedJob<T>
{
	private final long id;
	private final String knownId;
	private final T data;
	private final long scheduledTime;
	private final Schedule schedule;
	private final int attempt;

	public QueuedJobImpl(
		long id,
		String knownId,
		T data,
		long scheduledTime,
		Schedule schedule,
		int attempt
	)
	{
		this.id = id;
		this.knownId = knownId;
		this.data = data;
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
	public T getData()
	{
		return data;
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
