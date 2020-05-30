package se.l4.jobs.engine.internal;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import se.l4.jobs.Job;
import se.l4.jobs.engine.QueuedJob;

public class JobImpl
	implements Job
{
	private final QueuedJob<?> job;
	private final CompletableFuture<?> result;

	public JobImpl(
		QueuedJob<?> job,
		CompletableFuture<?> result
	)
	{
		this.job = job;
		this.result = result;
	}

	@Override
	public Optional<String> getId()
	{
		return job.getKnownId();
	}

	@Override
	public CompletableFuture<?> result()
	{
		return result;
	}

	@Override
	public void cancel()
	{
		// TODO Auto-generated method stub

	}
}
