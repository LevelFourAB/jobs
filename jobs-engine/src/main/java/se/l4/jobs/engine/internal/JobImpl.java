package se.l4.jobs.engine.internal;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import se.l4.jobs.Job;
import se.l4.jobs.engine.QueuedJob;

public class JobImpl
	implements Job
{
	private final LocalJobsImpl jobs;

	private final QueuedJob<?> job;
	private final CompletableFuture<?> result;

	public JobImpl(
		LocalJobsImpl jobs,

		QueuedJob<?> job,
		CompletableFuture<?> result
	)
	{
		this.jobs = jobs;

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
		jobs.cancel(job.getId());
	}
}
