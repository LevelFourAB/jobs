package se.l4.jobs.backend.silo;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.reactivestreams.Publisher;

import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.FluxSink.OverflowStrategy;
import reactor.core.publisher.Mono;
import se.l4.commons.id.LongIdGenerator;
import se.l4.commons.id.SimpleLongIdGenerator;
import se.l4.commons.io.Bytes;
import se.l4.jobs.JobException;
import se.l4.jobs.JobNotFoundException;
import se.l4.jobs.engine.JobRetryException;
import se.l4.jobs.engine.backend.BackendJobData;
import se.l4.jobs.engine.backend.JobCancelEvent;
import se.l4.jobs.engine.backend.JobCompleteEvent;
import se.l4.jobs.engine.backend.JobFailureEvent;
import se.l4.jobs.engine.backend.JobRunnerEvent;
import se.l4.jobs.engine.backend.JobTrackingEvent;
import se.l4.jobs.engine.backend.JobsBackend;
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
	private final ObjectEntity<StoredBackendJobData> entity;
	private final LongIdGenerator ids;

	private final ReentrantLock timestampLock;

	private final ScheduledExecutorService executor;

	private ScheduledFuture<?> future;
	private long closestTimestamp;

	private final EmitterProcessor<BackendJobData> jobs;

	private final ConnectableFlux<JobTrackingEvent> events;
	private final FluxSink<JobTrackingEvent> eventSink;
	private final Disposable eventDisposable;

	public SiloJobsBackend(StructuredEntity entity)
	{
		ids = new SimpleLongIdGenerator();

		this.entity = entity.asObject(StoredBackendJobData.class, o -> o.getId());

		jobs = EmitterProcessor.create();

		EmitterProcessor<JobTrackingEvent> events = EmitterProcessor.create();
		eventSink = events.sink(OverflowStrategy.DROP);

		this.events = events.publish();
		eventDisposable = this.events.connect();

		timestampLock = new ReentrantLock();
		executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
			.setNameFormat("jobs-silo-queuer-%d")
			.setDaemon(true)
			.build()
		);

		// Figure out when to next run
		try(FetchResult<StoredBackendJobData> fr = this.entity.query("sortedByTime", IndexQuery.type())
			.field("scheduledTime").sort(true)
			.run())
		{
			Optional<StoredBackendJobData> first = fr.first();
			if(! first.isPresent())
			{
				// If there are no jobs queued do nothing
				return;
			}

			StoredBackendJobData job = first.get();
			scheduleRun(job.getScheduledTime(), true);
		}
	}

	@Override
	public Mono<Void> stop()
	{
		return Mono.fromRunnable(() -> {
			eventDisposable.dispose();

			executor.shutdownNow();
			try
			{
				executor.awaitTermination(10, TimeUnit.SECONDS);
			}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		});
	}

	@Override
	public Flux<BackendJobData> jobs(Publisher<JobRunnerEvent> events)
	{
		return jobs;
	}

	@Override
	public Flux<JobTrackingEvent> jobEvents(long id)
	{
		return Flux.defer(() -> {
			Optional<StoredBackendJobData> current = entity.get(id);
			if(current.isPresent())
			{
				// If the job is still being processed, listen for future events
				return this.events
					.filter(event -> event.getId() == id);
			}
			else
			{
				return Flux.just(new JobFailureEvent(id, new JobNotFoundException("Job has completed, failed or cancelled")));
			}
		});
	}

	@Override
	public Mono<BackendJobData> accept(BackendJobData job)
	{
		return Mono.fromSupplier(() -> {
			if(job.getId() != 0)
			{
				throw new JobException("Jobs with non-zero ids are not supported");
			}

			long id = 0;
			if(job.getKnownId().isPresent())
			{
				try(FetchResult<StoredBackendJobData> fr = entity.query("viaKnownId", IndexQuery.type())
					.field("knownId").isEqualTo(job.getKnownId().get())
					.run())
				{
					Optional<StoredBackendJobData> first =  fr.first();
					if(first.isPresent())
					{
						id = first.get().getId();
					}
				}
			}

			if(id == 0)
			{
				id = ids.next();
			}

			StoredBackendJobData storedJob = new StoredBackendJobData(
				id,
				job.getKnownId().orElse(null),
				job.getDataName().getNamespace(),
				job.getDataName().getName(),
				job.getData(),
				job.getFirstScheduled(),
				job.getScheduledTime(),
				job.getSchedule().orElse(null),
				job.getAttempt()
			);

			entity.store(storedJob);

			scheduleRun(job.getScheduledTime(), false);

			return storedJob;
		});
	}

	private void rescheduleOrRemove(long id)
	{
		StoredBackendJobData data = entity.get(id).get();

		Optional<StoredBackendJobData> nextScheduled = data.withNextScheduledTime();
		if(nextScheduled.isPresent())
		{
			StoredBackendJobData next = nextScheduled.get();
			entity.store(next);
			scheduleRun(next.getScheduledTime(), false);
		}
		else
		{
			entity.deleteViaId(id);
		}
	}

	@Override
	public Mono<Void> cancel(long id)
	{
		return Mono.fromRunnable(() -> {
			entity.deleteViaId(id);
			eventSink.next(new JobCancelEvent(id));
		});
	}

	@Override
	public Mono<Void> complete(long id, Bytes bytes)
	{
		return Mono.fromRunnable(() -> {
			rescheduleOrRemove(id);
			eventSink.next(new JobCompleteEvent(id, bytes));
		});
	}

	@Override
	public Mono<Void> fail(long id, JobException reason)
	{
		return Mono.fromRunnable(() -> {
			rescheduleOrRemove(id);
			eventSink.next(new JobFailureEvent(id, reason));
		});
	}

	@Override
	public Mono<Void> retry(long id, Instant when, JobRetryException reason)
	{
		return Mono.fromRunnable(() -> {
			StoredBackendJobData data = entity.get(id).get();

			StoredBackendJobData next = data.withRetryAt(when.toEpochMilli());
			entity.store(next);
			scheduleRun(next.getScheduledTime(), false);

			// TODO: Events?
		});
	}

	@Override
	public Mono<Void> ping(long id)
	{
		return Mono.empty();
	}

	@Override
	public Mono<BackendJobData> getViaId(String id)
	{
		return Mono.fromSupplier(() -> {
			try(FetchResult<StoredBackendJobData> fr = entity.query("viaKnownId", IndexQuery.type())
				.field("knownId").isEqualTo(id)
				.run())
			{
				return fr.first().orElse(null);
			}
		});
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
			timestamp = Math.max(timestamp, System.currentTimeMillis());

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

			long delay = timestamp - now;

			closestTimestamp = timestamp;
			future = executor.schedule(this::runJobs, Math.max(0, delay), TimeUnit.MILLISECONDS);
		}
		finally
		{
			timestampLock.unlock();
		}
	}

	private void runJobs()
	{
		long lastHandled = 0;
		while(true)
		{
			try(FetchResult<StoredBackendJobData> fr = entity.query("sortedByTime", IndexQuery.type())
				.field("scheduledTime").sort(true)
				.limit(10)
				.run())
			{
				if(fr.isEmpty())
				{
					// No more jobs in the queue, stop the processing
					break;
				}

				for(StoredBackendJobData job : fr)
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
						lastHandled = job.getScheduledTime();

						// Queue the job up for a retry
						StoredBackendJobData autoRetry = job.withScheduledTime(System.currentTimeMillis() + RETRY_DELAY.toMillis());
						entity.store(autoRetry);
						scheduleRun(autoRetry.getScheduledTime(), false);

						// Request the job to be run and wait for the result
						jobs.onNext(job);
					}
				}
			}
		}

		// No more jobs in the queue, skip scheduling a new run
		timestampLock.lock();
		try
		{
			if(lastHandled == 0 || closestTimestamp <= lastHandled)
			{
				future = null;
			}
		}
		finally
		{
			timestampLock.unlock();
		}
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
			.defineField("scheduledTime", "long")
			.defineField("knownId", "string")
			.add("sortedByTime", Index::queryEngine)
				.addSortField("scheduledTime")
				.done()
			.add("viaKnownId", Index::queryEngine)
				.addField("knownId")
				.done();
	}

}
