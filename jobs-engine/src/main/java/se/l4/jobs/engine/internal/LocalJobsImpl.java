package se.l4.jobs.engine.internal;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.l4.commons.types.matching.ClassMatchingHashMap;
import se.l4.jobs.JobBuilder;
import se.l4.jobs.JobData;
import se.l4.jobs.When;
import se.l4.jobs.engine.Delay;
import se.l4.jobs.engine.JobControl;
import se.l4.jobs.engine.JobEncounter;
import se.l4.jobs.engine.JobRetryException;
import se.l4.jobs.engine.JobRunner;
import se.l4.jobs.engine.JobsBackend;
import se.l4.jobs.engine.LocalJobs;
import se.l4.jobs.engine.QueuedJob;

public class LocalJobsImpl
	implements LocalJobs
{
	private static final Logger logger = LoggerFactory.getLogger(LocalJobs.class);

	private final JobsBackend backend;
	private final Delay defaultDelay;
	private final int maxAutomaticAttempts;
	private final ClassMatchingHashMap<JobData, JobRunner<?>> runners;

	private final Cache<Long, CompletableFuture<Object>> futures;

	private ThreadPoolExecutor executor;

	public LocalJobsImpl(
		JobsBackend backend,
		Delay defaultDelay,
		ClassMatchingHashMap<JobData, JobRunner<?>> runners
	)
	{
		this.backend = backend;
		this.defaultDelay = defaultDelay;
		this.runners = runners;
		this.maxAutomaticAttempts = 5;

		this.futures = CacheBuilder.newBuilder()
			.weakValues()
			.build();
	}

	@Override
	public void start()
	{
		ThreadFactory factory = new ThreadFactoryBuilder()
			.setNameFormat("jobs-executor-%d")
			.build();

		executor = new ThreadPoolExecutor(
			8, 8,
			5l, TimeUnit.MINUTES,
			new LinkedBlockingQueue<Runnable>(),
			factory
		);

		backend.start(new JobControl()
		{
			@Override
			public CompletionStage<Object> runJob(QueuedJob<?> job)
			{
				return executeJob(job);
			}

			@Override
			public void completeJob(long id, Object data)
			{
				CompletableFuture<Object> future = futures.getIfPresent(id);
				if(future != null)
				{
					future.complete(data);
				}
			}

			@Override
			public void failJob(long id, Throwable t)
			{
				CompletableFuture<Object> future = futures.getIfPresent(id);
				if(future != null)
				{
					future.completeExceptionally(t);
				}
			}
		});
	}

	@Override
	public void stop()
	{
		backend.stop();

		executor.shutdown();
	}

	@Override
	public JobBuilder add(JobData jobData)
	{
		Objects.requireNonNull(jobData, "jobData must be supplied");

		return new JobBuilder()
		{
			private When when = When.now();

			@Override
			public JobBuilder at(When when)
			{
				Objects.requireNonNull(when, "when must be supplied");

				this.when = when;
				return this;
			}

			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public <T> CompletableFuture<T> submit()
			{
				long id = backend.nextId();

				backend.accept(new QueuedJobImpl<>(
					id,
					jobData,
					when.getTimestamp(),
					1
				));

				CompletableFuture<T> future = new CompletableFuture<>();
				futures.put(id, (CompletableFuture) future);

				return future;
			}
		};
	}

	/**
	 * Queue up the job on an executor and return a {@link CompletableFuture}.
	 * This will block if there are no free threads to run a job.
	 * @param job
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private CompletionStage<Object> executeJob(QueuedJob<? extends JobData> job)
	{
		CompletableFuture<Object> result = new CompletableFuture<>();
		executor.submit(() -> {
			Optional<JobRunner<?>> runner = runners.getBest(
				job.getData().getClass()
			);

			if(! runner.isPresent())
			{
				logger.warn("No job runner found for {}", job.getData());
				return;
			}

			JobEncounterImpl encounter = new JobEncounterImpl<>(job);
			try
			{
				runner.get().run(encounter);

				if(! encounter.failed && ! encounter.completed)
				{
					encounter.complete();
				}
			}
			catch(Throwable t)
			{
				if(! encounter.failed && ! encounter.completed)
				{
					// This is the automatic retry - limit the number of retries
					if(encounter.getAttempt() < maxAutomaticAttempts)
					{
						encounter.fail(t);
					}
					else
					{
						encounter.failNoRetry(t);
					}
				}
			}

			// Finish up the encounter
			encounter.finish(result);
		});

		return result;
	}

	private class JobEncounterImpl<T extends JobData>
		implements JobEncounter<T>
	{
		private final QueuedJob<T> job;

		private boolean completed;
		private Object completedResult;

		private boolean failed;
		private Throwable failedException;
		private long failedRetryTime;

		public JobEncounterImpl(QueuedJob<T> job)
		{
			this.job = job;
		}

		@Override
		public T getData()
		{
			return (T) job.getData();
		}

		@Override
		public void complete()
		{
			complete(null);
		}

		@Override
		public void complete(Object result)
		{
			if(this.completed) return;

			this.completed = true;
			this.completedResult = result;
		}

		@Override
		public void failNoRetry(Throwable t)
		{
			failAndRetryAt(t, -1);
		}

		@Override
		public void fail(Throwable t)
		{
			fail(t, defaultDelay);
		}

		@Override
		public void fail(Throwable t, Delay delay)
		{
			Objects.requireNonNull(delay, "delay can not be null");

			failAndRetryIn(t, delay.getDelay(getAttempt()));
		}

		@Override
		public void fail(Throwable t, Duration waitTime)
		{
			Objects.requireNonNull(waitTime, "waitTime can not be null");

			failAndRetryIn(t, waitTime.toMillis());
		}

		private void failAndRetryIn(Throwable t, long retryDelay)
		{
			failAndRetryAt(t, System.currentTimeMillis() + retryDelay);
		}

		@Override
		public void fail(Throwable t, When when)
		{
			failAndRetryAt(t, when.getTimestamp());
		}

		private void failAndRetryAt(Throwable t, long ms)
		{
			if(this.failed) return;

			this.failed = true;
			this.failedRetryTime = ms;
			this.failedException = t;
		}

		@Override
		public int getAttempt()
		{
			return job.getAttempt();
		}

		public void finish(CompletableFuture<Object> future)
		{
			if(failed)
			{
				if(failedRetryTime < 0)
				{
					logger.warn(
						"Job " + job.getData() + " failed, giving up without retrying; " + failedException.getMessage(),
						failedException
					);

					// Fail the call
					future.completeExceptionally(failedException);
				}
				else
				{
					long timeout = failedRetryTime;

					String formattedDelay = formatDelay(System.currentTimeMillis() - failedRetryTime);

					logger.warn(
						"Job " + job.getData() + " failed, retrying in " + formattedDelay + "; " + failedException.getMessage(),
						failedException
					);

					// Indicate that this has failed - but that it will be retried
					future.completeExceptionally(new JobRetryException("Job failed, retry in " + formattedDelay, failedException));

					// Queue it up with the new timeout
					backend.accept(new QueuedJobImpl<>(
						job.getId(),
						job.getData(),
						timeout,
						job.getAttempt() + 1
					));
				}
			}
			else
			{
				future.complete(this.completedResult);
			}
		}
	}

	private static final String formatDelay(long delay)
	{
		long milliseconds = delay % 1000;
		long t = delay / 1000;

		long seconds = t % 60;
		t /= 60;

		long minutes = t % 60;
		t /= 60;

		long hours = t % 24;
		t /= 24;

		long days = t;

		StringBuilder b = new StringBuilder();
		if(days > 0)
		{
			b.append(days).append('d');
		}

		if(hours > 0 || days > 0)
		{
			if(b.length() > 0) b.append(' ');

			b.append(hours).append('h');
		}

		if(minutes > 0 || hours > 0 || days > 0)
		{
			if(b.length() > 0) b.append(' ');

			b.append(minutes).append('m');
		}

		if(seconds > 0 || minutes > 0 || hours > 0 || days > 0)
		{
			if(b.length() > 0) b.append(' ');

			b.append(seconds).append('s');
		}

		if(milliseconds > 0 || seconds > 0 || minutes > 0 || hours > 0 || days > 0)
		{
			if(b.length() > 0) b.append(' ');

			b.append(milliseconds).append("ms");
		}

		return b.toString();
	}
}
