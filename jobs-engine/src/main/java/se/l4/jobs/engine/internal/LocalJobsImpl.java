package se.l4.jobs.engine.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import se.l4.commons.io.Bytes;
import se.l4.commons.serialization.QualifiedName;
import se.l4.commons.serialization.SerializationException;
import se.l4.commons.serialization.Serializer;
import se.l4.commons.serialization.SerializerCollection;
import se.l4.commons.serialization.format.BinaryInput;
import se.l4.commons.serialization.format.BinaryOutput;
import se.l4.commons.serialization.format.Token;
import se.l4.commons.serialization.standard.CompactDynamicSerializer;
import se.l4.commons.types.matching.ClassMatchingHashMap;
import se.l4.jobs.Job;
import se.l4.jobs.JobBuilder;
import se.l4.jobs.JobCancelledException;
import se.l4.jobs.JobData;
import se.l4.jobs.JobException;
import se.l4.jobs.Schedule;
import se.l4.jobs.When;
import se.l4.jobs.engine.Delay;
import se.l4.jobs.engine.JobEncounter;
import se.l4.jobs.engine.JobListener;
import se.l4.jobs.engine.JobRetryException;
import se.l4.jobs.engine.JobRunner;
import se.l4.jobs.engine.LocalJobs;
import se.l4.jobs.engine.backend.BackendJobData;
import se.l4.jobs.engine.backend.JobCancelEvent;
import se.l4.jobs.engine.backend.JobCompleteEvent;
import se.l4.jobs.engine.backend.JobFailureEvent;
import se.l4.jobs.engine.backend.JobRunnerAvailableEvent;
import se.l4.jobs.engine.backend.JobRunnerEvent;
import se.l4.jobs.engine.backend.JobsBackend;

public class LocalJobsImpl
	implements LocalJobs
{
	private static final Logger logger = LoggerFactory.getLogger(LocalJobs.class);

	private final SerializerCollection serializers;

	private final JobsBackend backend;

	private final Delay defaultDelay;

	private final ClassMatchingHashMap<JobData<?>, JobRunner<?, ?>> runners;

	private final JobListener[] listeners;
	private final CompactDynamicSerializer resultSerializer;

	private final Scheduler jobScheduler;
	private final JobExecutor jobExecutor;

	public LocalJobsImpl(
		SerializerCollection serializers,

		JobsBackend backend,

		Delay defaultDelay,
		JobListener[] listeners,

		int maxThreads,
		ClassMatchingHashMap<JobData<?>, JobRunner<?, ?>> runners
	)
	{
		this.serializers = serializers;

		this.backend = backend;
		this.defaultDelay = defaultDelay;
		this.listeners = listeners;

		this.runners = runners;

		resultSerializer = new CompactDynamicSerializer(serializers);

		jobScheduler = Schedulers.newBoundedElastic(
			maxThreads,
			Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
			"jobs-executor"
		);

		List<QualifiedName> names = Flux.fromIterable(runners.entries())
			.map(entry -> {
				Serializer<?> serializer = serializers.find(entry.getType())
					.orElseThrow(() -> new JobException("Type " + entry.getType() + " is not serializable"));

				return serializers.findName(serializer)
					.orElseThrow(() -> new JobException("Type " + entry.getType() + " has not been given a qualified name"));
			})
			.collectList()
			.block();

		Flux<JobRunnerEvent> runnerEvents = Flux.fromIterable(names)
			.map(qn -> new JobRunnerAvailableEvent(qn));

		// TODO: Create a flux used to signal what runners are available
		jobExecutor = new JobExecutor();
		backend.jobs(runnerEvents)
			.subscribe(jobExecutor);
	}

	@Override
	public Mono<Void> stop()
	{
		return Mono.fromRunnable(jobExecutor::dispose)
			.and(backend.stop())
			.and(Mono.fromRunnable(jobScheduler::dispose));
	}

	@Override
	public Mono<Job<?, ?>> getViaId(String id)
	{
		Objects.requireNonNull(id, "id must not be null");
		return backend.getViaId(id)
			.map(this::resolveJob);
	}

	Mono<Void> cancel(long id)
	{
		return backend.cancel(id);
	}

	@SuppressWarnings("unchecked")
	<R> Mono<R> result(BackendJobData data)
	{
		return (Mono<R>) backend.jobEvents(data.getId())
			.publishOn(jobScheduler)
			.filter(event -> event instanceof JobCompleteEvent || event instanceof JobFailureEvent || event instanceof JobCancelEvent)
			.next()
			.map(event -> {
				if(event instanceof JobCompleteEvent)
				{
					return deserialize(resultSerializer, ((JobCompleteEvent) event).getData());
				}
				else if(event instanceof JobFailureEvent)
				{
					throw ((JobFailureEvent) event).exception;
				}
				else if(event instanceof JobCancelEvent)
				{
					throw new JobCancelledException("Job has been cancelled");
				}

				return null;
			});
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <R, D extends JobData<R>> JobBuilder<D, R> add(D jobData)
	{
		Objects.requireNonNull(jobData, "jobData must be supplied");

		return new JobBuilder<D, R>()
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

			private Mono<BackendJobData> resolveBackendJobData()
			{
				return Mono.fromSupplier(() -> {
					if(schedule != null)
					{
						Objects.requireNonNull(knownId, "id must be supplied if a schedule is present");
					}

					OptionalLong timestamp = when.get();
					if(! timestamp.isPresent())
					{
						// TODO: Return a "rejected" job
						throw new JobException("Job is not scheduled for execution");
					}

					Serializer<D> serializer = serializers.find((Class<D>) jobData.getClass())
						.orElseThrow(() -> new JobException("Could not find serializer for " + jobData.getClass()));

					QualifiedName name = serializers.findName(serializer)
						.orElseThrow(() -> new JobException("Job data must have have a qualified name, if using reflection based serialization annotate with @Named"));

					Bytes data = serialize(serializer, jobData);

					return new QueuedJobImpl(
						0,
						knownId,
						name,
						data,
						timestamp.getAsLong(),
						timestamp.getAsLong(),
						schedule,
						1
					);
				});
			}

			@Override
			public Mono<Job<D, R>> submit()
			{
				return resolveBackendJobData()
					.flatMap(backend::accept)
					.map(data -> (Job<D, R>) resolveJob(data))
					.doOnNext(job -> {
						// Trigger the listeners
						for(JobListener listener : listeners)
						{
							listener.jobScheduled(job);
						}
					});
			}
		};
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <D extends JobData<R>, R> Job<D, R> resolveJob(BackendJobData q)
	{
		Serializer<?> serializer = serializers.find(q.getDataName())
			.orElseThrow(() -> new JobException("Could not find serializer for " + q.getDataName()));

		D data = (D) deserialize(serializer, q.getData());
		return new JobImpl(this, q, data);
	}

	private <D> Bytes serialize(Serializer<D> serializer, D instance)
	{
		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

			// Version tag
			baos.write(1);

			// Serialize the object
			BinaryOutput out = new BinaryOutput(baos);
			if(instance == null && ! (serializer instanceof Serializer.NullHandling))
			{
				out.writeNull("root");
			}
			else
			{
				serializer.write(instance, "root", out);
			}

			out.flush();
			return Bytes.create(baos.toByteArray());
		}
		catch(IOException | SerializationException e)
		{
			throw new JobException("Could not serialize job data; " + e.getMessage(), e);
		}
	}

	private Object deserialize(Serializer<?> serializer, Bytes bytes)
	{
		try(InputStream in = bytes.asInputStream())
		{
			int version = in.read();

			if(version != 1)
			{
				throw new JobException("Could not deserialize job data, unknown version of data: " + version);
			}

			try(BinaryInput binary = new BinaryInput(in))
			{
				if(binary.peek() == Token.NULL && ! (serializer instanceof Serializer.NullHandling))
				{
					return null;
				}

				return serializer.read(binary);
			}
		}
		catch(IOException | SerializationException e)
		{
			throw new JobException("Could not deserialize job data; " + e.getMessage(), e);
		}
	}

	/**
	 * Queue up the job on an executor and return a {@link CompletableFuture}.
	 * This will block if there are no free threads to run a job.
	 * @param job
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Mono<Void> executeJob(BackendJobData job)
	{
		return Mono.defer(() -> {
			JobEncounterImpl<?> encounter = new JobEncounterImpl(job);

			Optional<JobRunner<?, ?>> runner = runners.getBest(
				(Class) encounter.getData().getClass()
			);

			if(! runner.isPresent())
			{
				logger.warn("No job runner found for {}", job.getData());
				return Mono.empty();
			}

			// Trigger the listeners
			for(JobListener listener : listeners)
			{
				try
				{
					listener.jobStarted(encounter.job);
				}
				catch(Throwable t)
				{
					logger.warn("Calling " + listener + " for job start errored", t);
				}
			}

			return runner.get().run((JobEncounter) encounter)
				.flatMap(object -> handleJobComplete(encounter, object))
				.onErrorResume(t -> handleJobError(encounter, (Throwable) t));
		});
	}

	private Mono<Void> handleJobError(JobEncounterImpl<?> encounter, Throwable t)
	{
		BackendJobData backendJob = encounter.data;
		Job<?, ?> job = encounter.job;

		if(t instanceof JobRetryException && ((JobRetryException) t).willRetry())
		{
			Instant when = ((JobRetryException) t).getWhen();

			if(t.getCause() == null)
			{
				logger.warn(
					"Job " + job.getData() + " failed, retrying at " + when
				);
			}
			else
			{
				logger.warn(
					"Job " + job.getData() + " failed, retrying at " + when + "; " + t.getCause().getMessage(),
					t
				);
			}

			/*
			 * Trigger the listeners to indicate that this is a
			 * temporary failure.
			 */
			for(JobListener listener : listeners)
			{
				try
				{
					listener.jobFailed(encounter.job, true);
				}
				catch(Throwable t0)
				{
					logger.warn("Calling " + listener + " for failed job errored", t0);
				}
			}

			// Queue it up with the new timeout
			return backend.retry(backendJob.getId(), when, (JobRetryException) t);
		}
		else
		{
			logger.warn(
				"Job " + job.getData() + " failed, giving up without retrying; " + t.getMessage(),
				t
			);

			/*
			* Trigger the listeners to indicate that this is a
			* permanent failure.
			*/
			for(JobListener listener : listeners)
			{
				try
				{
					listener.jobFailed(encounter.job, false);
				}
				catch(Throwable t0)
				{
					logger.warn("Calling " + listener + " for failed job errored", t0);
				}
			}

			// Report the error to the backend
			return backend.fail(encounter.data.getId(), JobException.wrap(t));
		}
	}

	private Mono<Void> handleJobComplete(JobEncounterImpl<?> encounter, Object result)
	{
		BackendJobData backendJob = encounter.data;
		Job<?, ?> job = encounter.job;

		logger.info("Job " + job.getData() + " completed");

		// Trigger the listeners to indicate job completion
		for(JobListener listener : listeners)
		{
			try
			{
				listener.jobCompleted(encounter.job);
			}
			catch(Throwable t)
			{
				logger.warn("Calling " + listener + " for completed job errored", t);
			}
		}

		Bytes bytes = serialize(resultSerializer, result);
		return backend.complete(backendJob.getId(), bytes);
	}

	private class JobExecutor
		extends BaseSubscriber<BackendJobData>
	{
		@Override
		protected void hookOnSubscribe(Subscription subscription)
		{
			request(1);
		}

		@Override
		protected void hookOnNext(BackendJobData value)
		{
			executeJob(value)
				.subscribeOn(jobScheduler)
				.subscribe();

			request(1);
		}
	}

	private class JobEncounterImpl<D extends JobData<?>>
		implements JobEncounter<D>
	{
		private final BackendJobData data;
		private final Job<?, ?> job;

		public JobEncounterImpl(BackendJobData job)
		{
			this.data = job;
			this.job = resolveJob(job);
		}

		@Override
		public D getData()
		{
			return (D) job.getData();
		}

		@Override
		public Instant getFirstScheduled()
		{
			return Instant.ofEpochMilli(data.getFirstScheduled());
		}

		@Override
		public JobRetryException retry()
		{
			return retry(defaultDelay);
		}

		@Override
		public JobRetryException retry(Throwable t)
		{
			return retry(defaultDelay, t);
		}

		@Override
		public JobRetryException retry(Delay delay)
		{
			return retry(delay, null);
		}

		@Override
		public JobRetryException retry(Delay delay, Throwable t)
		{
			Objects.requireNonNull(delay, "delay can not be null");

			OptionalLong time = delay.getDelay(getAttempt());
			if(time.isPresent())
			{
				return retryIn(t, time.getAsLong());
			}
			else
			{
				return retry(t, null);
			}
		}

		@Override
		public JobRetryException retry(Duration waitTime)
		{
			return retry(waitTime, null);
		}

		@Override
		public JobRetryException retry(Duration waitTime, Throwable t)
		{
			Objects.requireNonNull(waitTime, "waitTime can not be null");

			return retryIn(t, waitTime.toMillis());
		}

		@Override
		public JobRetryException retry(When when)
		{
			return retry(when, null);
		}

		@Override
		public JobRetryException retry(When when, Throwable t)
		{
			Objects.requireNonNull(when, "when can not be null");

			OptionalLong timestamp = when.get();
			if(timestamp.isPresent())
			{
				return retry(t, Instant.ofEpochMilli(timestamp.getAsLong()));
			}
			else
			{
				return retry(t, null);
			}
		}

		private JobRetryException retryIn(Throwable t, long retryDelay)
		{
			if(retryDelay < 0)
			{
				return retry(t, null);
			}
			else
			{
				return retry(t, Instant.ofEpochMilli(System.currentTimeMillis() + retryDelay));
			}
		}

		private JobRetryException retry(Throwable t, Instant instant)
		{
			String message;
			if(instant == null)
			{
				message = "Failing job without retry";
			}
			else
			{
				message = "Retrying job at " + instant;
			}

			if(t != null && t.getMessage() != null)
			{
				message += "; " + t.getMessage();
			}

			return new JobRetryException(
				message,
				t,
				instant
			);
		}

		@Override
		public int getAttempt()
		{
			return data.getAttempt();
		}
	}
}
