package se.l4.jobs.engine.internal;

import se.l4.jobs.JobData;
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
	private final T data;
	private final long timestamp;
	private final int attempt;

	public QueuedJobImpl(
		long id,
		T data,
		long timestamp,
		int attempt
	)
	{
		this.id = id;
		this.data = data;
		this.timestamp = timestamp;
		this.attempt = attempt;
	}

	@Override
	public long getId()
	{
		return id;
	}

	@Override
	public T getData()
	{
		return data;
	}

	@Override
	public long getTimestamp()
	{
		return timestamp;
	}

	@Override
	public int getAttempt()
	{
		return attempt;
	}
}
