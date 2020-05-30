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
 * A {@link When} that occurs at a specific time.
 */
@Use(SpecificTimeWhen.SerializerImpl.class)
@Named(namespace="jobs:schedule", name="specific-time")
public class SpecificTimeWhen
	implements When
{
	private final long timestamp;

	public SpecificTimeWhen(long timestamp)
	{
		this.timestamp = timestamp;
	}

	@Override
	public OptionalLong get()
	{
		return OptionalLong.of(timestamp);
	}

	public static class SerializerImpl
		implements Serializer<SpecificTimeWhen>
	{
		@Override
		public SpecificTimeWhen read(StreamingInput in)
			throws IOException
		{
			in.next(Token.VALUE);
			return new SpecificTimeWhen(in.getLong());
		}

		@Override
		public void write(SpecificTimeWhen object, String name, StreamingOutput out)
			throws IOException
		{
			out.write(name, object.timestamp);
		}
	}
}
