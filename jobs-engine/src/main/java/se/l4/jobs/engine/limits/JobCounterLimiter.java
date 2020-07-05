package se.l4.jobs.engine.limits;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.FluxSink.OverflowStrategy;
import reactor.core.publisher.Mono;
import se.l4.jobs.engine.backend.BackendJobData;

public class JobCounterLimiter
	implements JobLimiter
{
	private final ConnectableFlux<Long> capacity;
	private final FluxSink<Long> sink;

	private final int maximumJobs;
	private final AtomicInteger currentJobs;

	public JobCounterLimiter(int maximumJobs)
	{
		DirectProcessor<Long> processor = DirectProcessor.create();
		sink = processor.sink(OverflowStrategy.DROP);
		capacity = processor.publish();
		capacity.connect();

		this.maximumJobs = maximumJobs;
		this.currentJobs = new AtomicInteger();
	}

	@Override
	public long getInitialCapacity()
	{
		return maximumJobs;
	}

	@Override
	public Flux<Long> capacity()
	{
		return capacity;
	}

	@Override
	public Mono<Void> jobStarted(BackendJobData data)
	{
		return Mono.fromRunnable(currentJobs::incrementAndGet);
	}

	@Override
	public Mono<Void> jobCompleted(BackendJobData data)
	{
		return Mono.fromRunnable(() -> {
			int count = currentJobs.decrementAndGet();
			sink.next((long) maximumJobs - count);
		});
	}

	@Override
	public Mono<Void> jobFailed(BackendJobData data)
	{
		return jobCompleted(data);
	}

	@Override
	public Mono<Void> jobRetry(BackendJobData data, Instant instant)
	{
		return jobCompleted(data);
	}
}
