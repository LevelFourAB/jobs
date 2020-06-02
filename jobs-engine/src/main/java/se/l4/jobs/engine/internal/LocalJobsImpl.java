package se.l4.jobs.engine.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.l4.commons.types.matching.ClassMatchingHashMap;
import se.l4.jobs.Job;
import se.l4.jobs.JobBuilder;
import se.l4.jobs.JobData;
import se.l4.jobs.JobException;
import se.l4.jobs.Schedule;
import se.l4.jobs.When;
import se.l4.jobs.engine.Delay;
import se.l4.jobs.engine.JobControl;
import se.l4.jobs.engine.JobEncounter;
import se.l4.jobs.engine.JobListener;
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

	private final int minThreads;
	private final int maxThreads;
	private final int queueSize;

	private final ClassMatchingHashMap<JobData<?>, JobRunner<?, ?>> runners;

	private final LoadingCache<Long, CompletableFuture<Object>> futures;
	private final JobListener[] listeners;

	private ThreadPoolExecutor executor;

	public LocalJobsImpl(
		JobsBackend backend,
		Delay defaultDelay,
		JobListener[] listeners,
		int minThreads,
		int maxThreads,
		int queueSize,
		ClassMatchingHashMap<JobData<?>, JobRunner<?, ?>> runners
	)
	{
		this.backend = backend;
		this.defaultDelay = defaultDelay;
		this.listeners = listeners;

		this.minThreads = minThreads;
		this.maxThreads = maxThreads;
		this.queueSize = queueSize;

		this.runners = runners;
		this.maxAutomaticAttempts = 5;

		this.futures = CacheBuilder.newBuilder()
			.weakValues()
			.build(new CacheLoader<Long, CompletableFuture<Object>>()
			{
				@Override
				public CompletableFuture<Object> load(Long key)
					throws Exception
				{
					return new CompletableFuture<>();
				}
			});
	}

	@Override
	public void start()
	{
		ThreadFactory factory = new ThreadFactoryBuilder()
			.setNameFormat("jobs-executor-%d")
			.build();

		executor = new ThreadPoolExecutor(
			minThreads, maxThreads,
			5l, TimeUnit.MINUTES,
			new LinkedBlockingQueue<Runnable>(queueSize),
			factory,
			new RejectedExecutionHandler()
			{
				@Override
				public void rejectedExecution(Runnable r, ThreadPoolExecutor executor)
				{
					if(executor.isShutdown())
					{
						throw new RejectedExecutionException();
					}

					try
					{
						executor.getQueue().put(r);
					}
					catch(InterruptedException e)
					{
						Thread.currentThread().interrupt();
						throw new RejectedExecutionException();
					}
				}
			}
		);

		backend.start(new JobControl()
		{
			@Override
			public CompletionStage<Object> runJob(QueuedJob<?, ?> job)
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

		executor.shutdownNow();
		try
		{
			executor.awaitTermination(1, TimeUnit.SECONDS);
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public Optional<Job<?, ?>> getViaId(String id)
	{
		Objects.requireNonNull(id, "id must not be null");
		return backend.getViaId(id)
			.map(this::resolveJob);
	}

	void cancel(long id)
	{
		backend.cancel(id);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <R, D extends JobData<R>> JobBuilder<D, R> add(D jobData)
	{
		Objects.requireNonNull(jobData, "jobData must be supplied");

		return new JobBuilder()
		{
			private When when = Schedule.now();
			private Schedule schedule;
			private String knownId;

			@Override
			public JobBuilder withId(String id)
			{
				Objects.requireNonNull(id, "id must not be null");

				this.knownId = id;

				return this;
			}

			@Override
			public JobBuilder withSchedule(When when)
			{
				Objects.requireNonNull(when, "when must be supplied");

				this.when = when;
				return this;
			}

			@Override
			public JobBuilder withSchedule(Schedule schedule)
			{
				Objects.requireNonNull(schedule, "schedule must be supplied");

				this.schedule = schedule;
				return this;
			}

			@Override
			public Job submit()
			{
				if(schedule != null)
				{
					Objects.requireNonNull(knownId, "id must be supplied if a schedule is present");
				}

				OptionalLong timestamp = when.get();
				if(! timestamp.isPresent())
				{
					CompletableFuture<Object> future = new CompletableFuture<>();
					future.completeExceptionally(new JobException("Job is not scheduled for execution"));
					return null;
				}

				long id;
				if(knownId != null)
				{
					Optional<QueuedJob<?, ?>> q = backend.getViaId(knownId);
					if(q.isPresent())
					{
						id = q.get().getId();
					}
					else
					{
						id = backend.nextId();
					}
				}
				else
				{
					id = backend.nextId();
				}

				QueuedJob<?, ?> queuedJob = new QueuedJobImpl<>(
					id,
					knownId,
					jobData,
					timestamp.getAsLong(),
					timestamp.getAsLong(),
					schedule,
					1
				);

				Job job =  resolveJob(queuedJob);

				/*
				 * Ask the backend to accept the job after we've resolved
				 * to avoid a race condition with the CompletableFuture.
				 */
				backend.accept(queuedJob);

				// Trigger the listeners
				for(JobListener listener : listeners)
				{
					listener.jobScheduled(job);
				}

				return job;
			}
		};
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Job<?, ?> resolveJob(QueuedJob<?, ?> q)
	{
		CompletableFuture<Object> future = futures.getUnchecked(q.getId());
		return new JobImpl(this, q, future);
	}

	/**
	 * Queue up the job on an executor and return a {@link CompletableFuture}.
	 * This will block if there are no free threads to run a job.
	 * @param job
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private CompletionStage<Object> executeJob(QueuedJob<?, ?> job)
	{
		CompletableFuture<Object> result = new CompletableFuture<>();
		executor.submit(() -> {
			Optional<JobRunner<?, ?>> runner = runners.getBest(
				(Class) job.getData().getClass()
			);

			if(! runner.isPresent())
			{
				logger.warn("No job runner found for {}", job.getData());
				return;
			}

			JobEncounterImpl<?, ?> encounter = new JobEncounterImpl(job);

			try
			{
				encounter.executeOn(runner.get());

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

	private class JobEncounterImpl<D extends JobData<R>, R>
		implements JobEncounter<D, R>
	{
		private final QueuedJob<D, R> scheduledJob;
		private final Job<?, ?> job;

		private boolean completed;
		private Object completedResult;

		private boolean failed;
		private Throwable failedException;
		private long failedRetryTime;

		public JobEncounterImpl(QueuedJob<D, R> job)
		{
			this.scheduledJob = job;
			this.job = resolveJob(job);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		public void executeOn(JobRunner<?, ?> jobRunner)
			throws Exception
		{
			// Trigger the listeners
			for(JobListener listener : listeners)
			{
				try
				{
					listener.jobStarted(job);
				}
				catch(Throwable t)
				{
					logger.warn("Calling " + listener + " for job start errored", t);
				}
			}

			((JobRunner) jobRunner).run((JobEncounter) this);
		}

		@Override
		public D getData()
		{
			return (D) scheduledJob.getData();
		}

		@Override
		public Instant getFirstScheduled()
		{
			return Instant.ofEpochMilli(scheduledJob.getFirstScheduled());
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

			// Get the delay and retry the job if present
			failAndRetryIn(t, delay.getDelay(getAttempt()).orElse(-1));
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
			OptionalLong timestamp = when.get();
			if(timestamp.isPresent())
			{
				failAndRetryAt(t, timestamp.getAsLong());
			}
			else
			{
				failNoRetry(t);
			}
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
			return scheduledJob.getAttempt();
		}

		public void finish(CompletableFuture<Object> future)
		{
			if(failed)
			{
				if(failedRetryTime < 0)
				{
					logger.warn(
						"Job " + scheduledJob.getData() + " failed, giving up without retrying; " + failedException.getMessage(),
						failedException
					);

					/*
					 * Trigger the listeners to indicate that this is a
					 * permanent failure.
					 */
					for(JobListener listener : listeners)
					{
						try
						{
							listener.jobFailed(job, false);
						}
						catch(Throwable t)
						{
							logger.warn("Calling " + listener + " for failed job errored", t);
						}
					}

					// Fail the call
					future.completeExceptionally(failedException);
				}
				else
				{
					long timeout = failedRetryTime;

					String formattedDelay = formatDelay(System.currentTimeMillis() - failedRetryTime);

					logger.warn(
						"Job " + scheduledJob.getData() + " failed, retrying in " + formattedDelay + "; " + failedException.getMessage(),
						failedException
					);

					/*
					 * Trigger the listeners to indicate that this is a
					 * temporary failure.
					 */
					for(JobListener listener : listeners)
					{
						try
						{
							listener.jobFailed(job, true);
						}
						catch(Throwable t)
						{
							logger.warn("Calling " + listener + " for failed job errored", t);
						}
					}

					// Indicate that this has failed - but that it will be retried
					future.completeExceptionally(new JobRetryException("Job failed, retry in " + formattedDelay, failedException));

					// Queue it up with the new timeout
					backend.accept(new QueuedJobImpl<>(
						scheduledJob.getId(),
						scheduledJob.getKnownId().orElse(null),
						scheduledJob.getData(),
						scheduledJob.getFirstScheduled(),
						timeout,
						scheduledJob.getSchedule().orElse(null),
						scheduledJob.getAttempt() + 1
					));
				}
			}
			else
			{
				logger.info("Job " + scheduledJob.getData() + " completed");

				// Trigger the listeners to indicate job completion
				for(JobListener listener : listeners)
				{
					try
					{
						listener.jobCompleted(job);
					}
					catch(Throwable t)
					{
						logger.warn("Calling " + listener + " for completed job errored", t);
					}
				}

				future.complete(this.completedResult);

				if(scheduledJob.getSchedule().isPresent())
				{
					/*
					 * If there is a schedule active ask it about the next
					 * execution time.
					 */
					OptionalLong nextTime = scheduledJob.getSchedule().get().getNextExecution();
					if(nextTime.isPresent() && nextTime.getAsLong() > System.currentTimeMillis())
					{
						backend.accept(new QueuedJobImpl<>(
							scheduledJob.getId(),
							scheduledJob.getKnownId().orElse(null),
							scheduledJob.getData(),
							scheduledJob.getFirstScheduled(),
							nextTime.getAsLong(),
							scheduledJob.getSchedule().orElse(null),
							1
						));
					}
				}
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
