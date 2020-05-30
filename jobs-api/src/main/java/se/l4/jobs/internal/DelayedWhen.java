package se.l4.jobs.internal;

import java.io.IOException;
import java.util.OptionalLong;

import se.l4.commons.serialization.Named;
import se.l4.commons.serialization.Serializer;
import se.l4.commons.serialization.Use;
import se.l4.commons.serialization.format.StreamingInput;
import se.l4.commons.serialization.format.StreamingOutput;
import se.l4.commons.serialization.format.Token;
import se.l4.jobs.When;

/**
 * A {@link When} that occurs at a delay from the current time.
 */
@Use(DelayedWhen.SerializerImpl.class)
@Named(namespace="jobs:schedule", name="delayed")
public class DelayedWhen
	implements When
{
	private final long delay;

	public DelayedWhen(long delay)
	{
		this.delay = delay;
	}

	@Override
	public OptionalLong get()
	{
		return OptionalLong.of(System.currentTimeMillis() + delay);
	}

	public static class SerializerImpl
		implements Serializer<DelayedWhen>
	{
		@Override
		public DelayedWhen read(StreamingInput in)
			throws IOException
		{
			in.next(Token.VALUE);
			return new DelayedWhen(in.getLong());
		}

		@Override
		public void write(DelayedWhen object, String name, StreamingOutput out)
			throws IOException
		{
			out.write(name, object.delay);
		}
	}
}
