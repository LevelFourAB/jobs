package se.l4.jobs.backend.silo;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.l4.commons.id.LongIdGenerator;
import se.l4.commons.id.SimpleLongIdGenerator;
import se.l4.jobs.JobCancelledException;
import se.l4.jobs.engine.JobControl;
import se.l4.jobs.engine.JobRetryException;
import se.l4.jobs.engine.JobsBackend;
import se.l4.jobs.engine.QueuedJob;
import se.l4.silo.FetchResult;
import se.l4.silo.Silo;
import se.l4.silo.engine.Index;
import se.l4.silo.engine.builder.SiloBuilder;
import se.l4.silo.engine.builder.StructuredEntityBuilder;
import se.l4.silo.query.IndexQuery;
import se.l4.silo.structured.ObjectEntity;
import se.l4.silo.structured.StructuredEntity;

/**
 * This backend will store jobs in a {@link Silo} entity. This allows for
 * persisting jobs within a single process.
 *
 * <p>
 * To use this backend define an entity either using
 * {@link #defineJobEntity(SiloBuilder, String)} or {@link #defineJobEntity(StructuredEntityBuilder)}.
 * This entity should then be provided to the constructor after being fetched
 * using {@link Silo#structured(String)}.
 *
 * <p>
 * This works by queuing up a task for the closest job. When the tasks runs
 * all of the jobs that have past their {@link QueuedJob#getScheduledTime() timestamp}
 * will be run. After this is done the task will be queued up again.
 */
public class SiloJobsBackend
	implements JobsBackend
{
	private static final Logger log = LoggerFactory.getLogger(SiloJobsBackend.class);

	private final ObjectEntity<StoredJob> entity;
	private final LongIdGenerator ids;

	private final ReentrantLock timestampLock;

	private JobControl control;
	private ScheduledExecutorService executor;

	private ScheduledFuture<?> future;
	private long closestTimestamp;

	public SiloJobsBackend(StructuredEntity entity)
	{
		ids = new SimpleLongIdGenerator();

		this.entity = entity.asObject(StoredJob.class, o -> o.getId());

		timestampLock = new ReentrantLock();
	}

	@Override
	public long nextId()
	{
		return ids.next();
	}

	@Override
	public void start(JobControl control)
	{
		timestampLock.lock();
		try
		{
			this.control = control;

			executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
				.setNameFormat("jobs-silo-queuer-%d")
				.setDaemon(true)
				.build()
			);
		}
		finally
		{
			timestampLock.unlock();
		}

		// Figure out when to next run
		try(FetchResult<StoredJob> fr = entity.query("sortedByTime", IndexQuery.type())
			.field("timestamp").sort(true)
			.run())
		{
			Optional<StoredJob> first = fr.first();
			if(! first.isPresent())
			{
				// If there are no jobs queued, schedule check in 5 minutes
				scheduleRun(System.currentTimeMillis() + 300000, false);
				return;
			}

			StoredJob job = first.get();
			scheduleRun(job.getScheduledTime(), true);
		}
	}

	@Override
	public void stop()
	{
		executor.shutdownNow();
		try
		{
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void accept(QueuedJob<?, ?> job)
	{
		StoredJob storedJob = new StoredJob(
			job.getId(),
			job.getKnownId().orElse(null),
			job.getData(),
			job.getFirstScheduled(),
			job.getScheduledTime(),
			job.getSchedule().orElse(null),
			job.getAttempt()
		);

		entity.store(storedJob);

		scheduleRun(job.getScheduledTime(), false);
	}

	@Override
	public void cancel(long id)
	{
		entity.deleteViaId(id);
		control.failJob(id, new JobCancelledException("Job was cancelled"));
	}

	@Override
	public Optional<QueuedJob<?, ?>> getViaId(String id)
	{
		try(FetchResult<StoredJob> fr = entity.query("viaKnownId", IndexQuery.type())
			.field("knownId").isEqualTo(id)
			.run())
		{
			return Optional.ofNullable(fr.first().orElse(null));
		}
	}

	/**
	 * Schedule a run at the given timestamp.
	 *
	 * @param timestamp
	 */
	private void scheduleRun(long timestamp, boolean ignoreClosest)
	{
		timestampLock.lock();
		try
		{
			long now = System.currentTimeMillis();
			long delay = Math.max(0, timestamp - now);
			timestamp = now + delay;

			if(future != null)
			{
				if(! ignoreClosest && timestamp > closestTimestamp)
				{
					/*
					 * The given timestamp is after the next scheduled
					 * invocation, keep the existing invocation.
					 */
					return;
				}

				// Cancel the current future
				future.cancel(false);
			}

			log.debug("Scheduling running of jobs in {} ms", delay);

			closestTimestamp = now + delay;
			future = executor.schedule(() -> runJobs(control), delay, TimeUnit.MILLISECONDS);
		}
		finally
		{
			timestampLock.unlock();
		}
	}

	private void runJobs(JobControl control)
	{
		timestampLock.lock();
		try
		{
			long now = System.currentTimeMillis();
			if(closestTimestamp <= now)
			{
				future = null;
				closestTimestamp = now;
			}
		}
		finally
		{
			timestampLock.unlock();
		}

		while(true)
		{
			try(FetchResult<StoredJob> fr = entity.query("sortedByTime", IndexQuery.type())
				.field("timestamp").sort(true)
				.limit(10)
				.run())
			{
				log.debug("Checking {} jobs if they should be run", fr.getSize());

				if(fr.isEmpty())
				{
					// No more jobs in the queue, stop the processing
					break;
				}

				for(StoredJob job : fr)
				{
					if(Thread.currentThread().isInterrupted())
					{
						// The thread has been interrupted indicating we are shutting down
						return;
					}

					if(job.getScheduledTime() > System.currentTimeMillis())
					{
						// This should not be run now, schedule another run for later
						scheduleRun(job.getScheduledTime(), true);
						return;
					}
					else
					{
						// TODO: This should auto-schedule the job for later just in case

						// Delete the job
						entity.deleteViaId(job.getId());

						// Request the job to be run and wait for the result
						long id = job.getId();
						control.runJob(job)
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
				}
			}
		}

		// No more jobs in the queue, attempt to schedule a full run in 5 minutes
		scheduleRun(System.currentTimeMillis() + 300000, false);
	}

	public static SiloBuilder defineJobEntity(SiloBuilder builder, String name)
	{
		StructuredEntityBuilder<SiloBuilder> structuredBuilder = builder.addEntity(name)
			.asStructured();

		defineJobEntity(structuredBuilder);

		return structuredBuilder.done();
	}

	public static <T> StructuredEntityBuilder<T> defineJobEntity(StructuredEntityBuilder<T> builder)
	{
		return builder
			.defineField("timestamp", "long")
			.defineField("knownId", "string")
			.add("sortedByTime", Index::queryEngine)
				.addSortField("timestamp")
				.done()
			.add("viaKnownId", Index::queryEngine)
				.addField("knownId")
				.done();
	}

}
