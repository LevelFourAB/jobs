package se.l4.jobs.engine.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;

import se.l4.commons.types.TypeFinder;
import se.l4.commons.types.Types;
import se.l4.commons.types.matching.ClassMatchingHashMap;
import se.l4.jobs.JobData;
import se.l4.jobs.JobException;
import se.l4.jobs.engine.Delay;
import se.l4.jobs.engine.JobListener;
import se.l4.jobs.engine.JobRunner;
import se.l4.jobs.engine.JobsBackend;
import se.l4.jobs.engine.LocalJobs;
import se.l4.jobs.engine.LocalJobs.Builder;
import se.l4.vibe.Vibe;

/**
 * Implementation of a builder for {@link LocalJobs}.
 */
public class LocalJobsBuilderImpl
	implements LocalJobs.Builder
{
	private final ClassMatchingHashMap<JobData<?>, JobRunner<?, ?>> runners;

	private final List<JobListener> listeners;

	private JobsBackend backend;
	private TypeFinder typeFinder;
	private Delay defaultDelay;

	public LocalJobsBuilderImpl()
	{
		listeners = new ArrayList<>();
		runners = new ClassMatchingHashMap<>();
		defaultDelay = Delay.exponential(1000);
	}

	@Override
	public Builder withBackend(JobsBackend backend)
	{
		Objects.requireNonNull(backend, "backend must not be null");

		this.backend = backend;
		return this;
	}

	@Override
	public Builder withDefaultDelay(Delay delay)
	{
		Objects.requireNonNull(delay, "delays must not be null");

		this.defaultDelay = delay;
		return this;
	}

	@Override
	public Builder withTypeFinder(TypeFinder finder)
	{
		this.typeFinder = finder;
		return this;
	}

	@Override
	public Builder withVibe(Vibe vibe, String... path)
	{
		if(path.length == 0)
		{
			vibe = vibe.scope("jobs");
		}
		else
		{
			vibe = vibe.scope(Joiner.on('/').join(path));
		}

		this.listeners.add(new VibeListener(vibe));

		return this;
	}

	@Override
	public Builder addListener(JobListener listener)
	{
		this.listeners.add(listener);
		return this;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Builder addRunner(JobRunner<?, ?> runner)
	{
		runners.put((Class) getType(runner), runner);
		return this;
	}

	@Override
	public <T extends JobData<?>> Builder addRunner(Class<T> dataType, JobRunner<T, ?> runner)
	{
		runners.put(dataType, runner);
		return this;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public LocalJobs build()
	{
		Objects.requireNonNull(backend, "backend must be specified");

		if(typeFinder != null)
		{
			// Automatically find runners if a TypeFinder is available
			for(JobRunner runner : typeFinder.getSubTypesAsInstances(JobRunner.class))
			{
				addRunner(runner);
			}
		}

		return new LocalJobsImpl(
			backend,
			defaultDelay,
			listeners.toArray(new JobListener[listeners.size()]),
			runners
		);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Class<? extends JobData> getType(JobRunner<?, ?> runner)
	{
		return (Class) Types.reference(runner.getClass())
			.findInterface(JobRunner.class).get()
			.getTypeParameter(0)
			.orElseThrow(() -> new JobException("Could not find type parameter for " + runner.getClass()))
			.getErasedType();
	}
}
