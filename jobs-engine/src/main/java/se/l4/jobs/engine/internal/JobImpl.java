package se.l4.jobs.engine.internal;

import java.util.Optional;

import reactor.core.publisher.Mono;
import se.l4.jobs.Job;
import se.l4.jobs.JobData;
import se.l4.jobs.engine.backend.BackendJobData;

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

	private final BackendJobData job;
	private final D data;

	public JobImpl(
		LocalJobsImpl jobs,

		BackendJobData job,
		D data
	)
	{
		this.jobs = jobs;

		this.job = job;
		this.data = data;
	}

	@Override
	public Optional<String> getId()
	{
		return job.getKnownId();
	}

	@Override
	public D getData()
	{
		return data;
	}

	@Override
	public Mono<R> result()
	{
		return jobs.result(job);
	}

	@Override
	public Mono<Void> cancel()
	{
		return jobs.cancel(job.getId());
	}
}
