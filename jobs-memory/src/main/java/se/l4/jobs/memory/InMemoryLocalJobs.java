package se.l4.jobs.memory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.l4.jobs.AbstractLocalJobs;
import se.l4.jobs.Job;
import se.l4.jobs.JobBuilder;
import se.l4.jobs.JobRunner;
import se.l4.jobs.Jobs;
import se.l4.jobs.LocalJobs;
import se.l4.jobs.SubmittedJob;

/**
 * Implementation of {@link LocalJobs} that keeps everything in memory. Instances
 * of this type needs to be {@link #start() started} and can be {@link #stop() stopped}.
 */
public class InMemoryLocalJobs
	extends AbstractLocalJobs
{
	private static final int MAX_ATTEMPTS = 5;
	private static final Logger logger = LoggerFactory.getLogger(InMemoryLocalJobs.class);

	private final DelayQueue<SubmittedJobImpl> queue;

	private ThreadPoolExecutor executor;
	private Thread queueThread;

	public InMemoryLocalJobs()
	{
		queue = new DelayQueue<>();
	}

	public void start()
	{
		executor = new ThreadPoolExecutor(8, 8, 5l, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
		queueThread = new Thread(this::queueJobs, "Job Queuer");
		queueThread.start();
	}

	public void stop()
	{
		try
		{
			queueThread.interrupt();
			queueThread.join();
		}
		catch(InterruptedException e)
		{
			// Ignore this interruption
			Thread.currentThread().interrupt();
		}

		executor.shutdown();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void queueJobs()
	{
		while(! Thread.currentThread().isInterrupted())
		{
			try
			{
				SubmittedJobImpl submittedJob  = queue.take();
				executor.submit(() -> {
					JobRunner runner = getRunner(submittedJob.data);
					if(runner == null)
					{
						logger.warn("No job runner found for {}", submittedJob.data);
						return;
					}

					JobImpl job = new JobImpl(submittedJob);
					try
					{
						runner.run(job);

						job.complete();
					}
					catch(Throwable t)
					{
						job.fail(t);
					}
				});
			}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public JobBuilder add(Object jobData)
	{
		Objects.requireNonNull(jobData, "Job data must be supplied");

		return new JobBuilder()
		{
			private When when = Jobs.now();
			private CompletableFuture<?> future;

			@Override
			public JobBuilder delay(When when)
			{
				Objects.requireNonNull(when, "When to run must be supplied");

				this.when = when;
				return this;
			}

			@Override
			public JobBuilder withResult()
			{
				future = new CompletableFuture<>();
				return this;
			}

			@Override
			public <T> SubmittedJob<T> submit()
			{
				return queueJob(jobData, when, future);
			}
		};
	}
	private SubmittedJobImpl queueJob(Object jobData, When whenToRun, CompletableFuture<?> resultFuture)
	{
		SubmittedJobImpl job = new SubmittedJobImpl(
			jobData,
			whenToRun.getTimestamp() == -1  ? System.currentTimeMillis() : whenToRun.getTimestamp(),
			0,
			resultFuture
		);

		queue.put(job);

		return job;
	}

	@Override
	public String toString()
	{
		return "Job Queue";
	}

	private static class SubmittedJobImpl
		implements SubmittedJob, Delayed
	{
		private Object data;
		private long whenToRun;
		private int attempt;
		private CompletableFuture future;

		public SubmittedJobImpl(Object data, long whenToRun, int attempt, CompletableFuture<?> future)
		{
			this.data = data;
			this.whenToRun = whenToRun;
			this.attempt = attempt;
			this.future = future;
		}

		@Override
		public CompletableFuture result()
		{
			return future;
		}

		@Override
		public int compareTo(Delayed o)
		{
			if(o == this) return 0;

			return Long.compare(
				getDelay(TimeUnit.NANOSECONDS),
				o.getDelay(TimeUnit.NANOSECONDS)
			);
		}

		@Override
		public long getDelay(TimeUnit unit)
		{
			return unit.convert(whenToRun - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		}
	}

	private class JobImpl<T>
		implements Job<T>
	{
		private final SubmittedJobImpl submitted;

		private boolean completed;
		private boolean failed;

		public JobImpl(SubmittedJobImpl submitted)
		{
			this.submitted = submitted;
		}

		@SuppressWarnings("unchecked")
		@Override
		public T getData()
		{
			return (T) submitted.data;
		}

		@Override
		public void complete()
		{
			complete(null);
		}

		@Override
		public void complete(Object result)
		{
			if(! this.completed) return;

			this.completed = true;
			if(submitted.future != null)
			{
				submitted.future.complete(result);
			}
		}

		@Override
		public void failNoRetry(Throwable t)
		{
			this.failed = true;
			if(submitted.future != null)
			{
				logger.error("Job " + submitted.data + " failed; " + t.getMessage(), t);
				submitted.future.completeExceptionally(t);
			}
		}

		@Override
		public void fail(Throwable t)
		{
			fail(t, 1000 * Math.max(1, ThreadLocalRandom.current().nextInt(1 << submitted.attempt)));
		}

		@Override
		public void fail(Throwable t, long retryDelay)
		{
			if(this.failed) return;

			this.failed = true;

			if(submitted.attempt >= MAX_ATTEMPTS)
			{
				logger.warn("Giving up, too many failures for " + submitted.data + "; " + t.getMessage(), t);
				if(submitted.future != null)
				{
					submitted.future.completeExceptionally(new RuntimeException("Job did not complete in " + MAX_ATTEMPTS + " retries"));
				}
			}
			else
			{
				logger.warn("Job " + submitted.data + " failed, retrying in " + retryDelay + " ms; " + t.getMessage(), t);
				long time = System.currentTimeMillis() + retryDelay;
				queue.put(new SubmittedJobImpl(submitted.data, time, submitted.attempt + 1, submitted.future));
			}
		}

		@Override
		public int getAttempt()
		{
			return submitted.attempt;
		}

		@Override
		public boolean isLastTry()
		{
			return submitted.attempt >= MAX_ATTEMPTS;
		}
	}
}
