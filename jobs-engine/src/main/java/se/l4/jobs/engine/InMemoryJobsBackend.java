package se.l4.jobs.engine;

import java.util.Optional;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Backend that will keeps jobs stored in memory. This type of backend is
 * not persisted so it's useful for smaller services that might not need the
 * overhead of a persisted queue. The memory usage of this type of backend is
 * unbounded, so queueing lots of jobs may cause {@link OutOfMemoryError}s.
 */
public class InMemoryJobsBackend
	implements JobsBackend
{
	private final AtomicLong id;

	private final DelayQueue<DelayedJob> queue;
	private Thread queueThread;

	public InMemoryJobsBackend()
	{
		queue = new DelayQueue<>();
		id = new AtomicLong(0);
	}

	@Override
	public void start(JobControl control)
	{
		queueThread = new Thread(() -> queueJobs(control), "jobs-queuer");
		queueThread.start();
	}

	@Override
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
	}

	@Override
	public long nextId()
	{
		return id.incrementAndGet();
	}

	@Override
	public void accept(QueuedJob<?> job)
	{
		queue.add(new DelayedJob(job));
	}

	@Override
	public Optional<QueuedJob<?>> getViaId(String id)
	{
		for(DelayedJob q : queue)
		{
			if(q.job.getKnownId().isPresent() && id.equals(q.job.getKnownId().get()))
			{
				return Optional.of(q.job);
			}
		}

		return Optional.empty();
	}

	private void queueJobs(JobControl control)
	{
		while(! Thread.currentThread().isInterrupted())
		{
			try
			{
				DelayedJob queuedJob  = queue.take();
				long id = queuedJob.job.getId();
				control.runJob(queuedJob.job)
					.whenComplete((value, e) -> {
						if(e == null)
						{
							// If not completed with an exception register as completed
							control.completeJob(id, value);
						}
						else
						{
							if(! (e instanceof JobRetryException))
							{
								/*
								 * For everything that isn't a retry report it
								 * back to the control.
								 */
								control.failJob(id, e);
							}
						}
					});
			}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	private static class DelayedJob
		implements Delayed
	{
		private final QueuedJob<?> job;

		public DelayedJob(QueuedJob<?> job)
		{
			this.job = job;
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
			return unit.convert(job.getScheduledTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		}
	}
}
