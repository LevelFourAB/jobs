package se.l4.jobs.engine.internal;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import se.l4.jobs.Job;
import se.l4.jobs.JobData;
import se.l4.jobs.engine.QueuedJob;

/**
 * Implementation of {@link Job} as returned by {@link LocalJobsImpl}.
 *
 * @param <D>
 * @param <R>
 */
public class JobImpl<D extends JobData<R>, R>
	implements Job<D, R>
{
	private final LocalJobsImpl jobs;

	private final QueuedJob<D, R> job;
	private final CompletableFuture<R> result;

	public JobImpl(
		LocalJobsImpl jobs,

		QueuedJob<D, R> job,
		CompletableFuture<R> result
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
	public D getData()
	{
		return job.getData();
	}

	@Override
	public CompletableFuture<R> result()
	{
		return result;
	}

	@Override
	public void cancel()
	{
		jobs.cancel(job.getId());
	}
}
