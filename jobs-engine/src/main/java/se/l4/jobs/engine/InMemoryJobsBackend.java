package se.l4.jobs.engine;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;

import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.FluxSink.OverflowStrategy;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import se.l4.commons.io.Bytes;
import se.l4.jobs.JobException;
import se.l4.jobs.JobNotFoundException;
import se.l4.jobs.engine.backend.BackendJobData;
import se.l4.jobs.engine.backend.JobCancelEvent;
import se.l4.jobs.engine.backend.JobCompleteEvent;
import se.l4.jobs.engine.backend.JobFailureEvent;
import se.l4.jobs.engine.backend.JobRunnerEvent;
import se.l4.jobs.engine.backend.JobTrackingEvent;
import se.l4.jobs.engine.backend.JobsBackend;

/**
 * Backend that will keeps jobs stored in memory. This type of backend is
 * not persisted so it's useful for smaller services that might not need the
 * overhead of a persisted queue. The memory usage of this type of backend is
 * unbounded, so queueing lots of jobs may cause {@link OutOfMemoryError}s.
 */
public class InMemoryJobsBackend
	implements JobsBackend
{
	private final Scheduler scheduler;

	private final AtomicLong id;
	private final DelayQueue<DelayedJob> queue;
	private final Map<Long, BackendJobData> jobs;

	private final ConnectableFlux<JobTrackingEvent> events;
	private final FluxSink<JobTrackingEvent> eventSink;
	private final Disposable eventDisposable;

	private InMemoryJobsBackend()
	{
		scheduler = Schedulers.newSingle("jobs-queuer");

		id = new AtomicLong(0);
		queue = new DelayQueue<>();
		jobs = new ConcurrentHashMap<>();

		EmitterProcessor<JobTrackingEvent> events = EmitterProcessor.create();
		eventSink = events.sink(OverflowStrategy.DROP);

		this.events = events.publish();
		eventDisposable = this.events.connect();
	}

	public static Mono<JobsBackend> create()
	{
		return Mono.fromCallable(InMemoryJobsBackend::new);
	}

	@Override
	public Mono<Void> stop()
	{
		return Mono.fromRunnable(() -> {
			eventDisposable.dispose();
			scheduler.dispose();
		});
	}

	@Override
	public Flux<BackendJobData> jobs(Publisher<JobRunnerEvent> events)
	{
		return Mono.fromCallable(queue::take)
			.map(delayed -> delayed.job)
			.repeat()
			.subscribeOn(scheduler);
	}

	@Override
	public Mono<BackendJobData> accept(BackendJobData job)
	{
		return Mono.defer(() -> {
			if(job.getId() > 0)
			{
				return Mono.just(job.getId());
			}
			else if(job.getKnownId().isPresent())
			{
				return getViaId(job.getKnownId().get())
					.map(data -> data.getId())
					.defaultIfEmpty(0l);
			}
			else
			{
				return Mono.just(0l);
			}
		})
			.map(id -> id == 0 ? this.id.incrementAndGet() : id)
			.map(value -> {
				BackendJobData withUpdatedId = job.withId(value);
				queue.add(new DelayedJob(withUpdatedId));
				jobs.put(value, withUpdatedId);
				return withUpdatedId;
			});
	}

	@Override
	public Flux<JobTrackingEvent> jobEvents(long id)
	{
		return Flux.defer(() -> {
			if(jobs.containsKey(id))
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
	public Mono<Void> cancel(long id)
	{
		return Mono.fromRunnable(() -> {
			for(DelayedJob q : queue)
			{
				if(q.job.getId() == id)
				{
					queue.remove(q);
					jobs.remove(id);

					eventSink.next(new JobCancelEvent(id));
					return;
				}
			}
		});
	}

	private void rescheduleOrRemove(long id)
	{
		BackendJobData data = jobs.get(id);

		Optional<? extends BackendJobData> nextScheduled = data.withNextScheduledTime();
		if(nextScheduled.isPresent())
		{
			BackendJobData next = nextScheduled.get();
			jobs.put(next.getId(), next);
			queue.add(new DelayedJob(next));
		}
		else
		{
			jobs.remove(id);
		}
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
			BackendJobData next = jobs.get(id).withRetryAt(when.toEpochMilli());
			jobs.put(next.getId(), next);
			queue.add(new DelayedJob(next));

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
			for(DelayedJob q : queue)
			{
				if(q.job.getKnownId().isPresent() && id.equals(q.job.getKnownId().get()))
				{
					return q.job;
				}
			}

			return null;
		});
	}

	private static class DelayedJob
		implements Delayed
	{
		private final BackendJobData job;

		public DelayedJob(BackendJobData job)
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
