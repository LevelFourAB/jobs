package se.l4.jobs.internal;

import java.util.OptionalLong;

import se.l4.commons.serialization.AllowAny;
import se.l4.commons.serialization.Expose;
import se.l4.commons.serialization.Named;
import se.l4.commons.serialization.ReflectionSerializer;
import se.l4.commons.serialization.Use;
import se.l4.jobs.Schedule;
import se.l4.jobs.When;

/**
 * Schedule that will repeat as long as there is a new time available from
 * the underlying {@link When}.
 */
@Use(ReflectionSerializer.class)
@Named(namespace="jobs:schedule", name="infinite-schedule")
public class InfiniteSchedule
	implements Schedule
{
	@Expose
	@AllowAny
	private final When when;

	public InfiniteSchedule(@Expose("when") When when)
	{
		this.when = when;
	}

	@Override
	public OptionalLong getNextExecution()
	{
		return when.get();
	}
}
