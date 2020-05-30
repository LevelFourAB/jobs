package se.l4.jobs.internal;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.OptionalLong;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import se.l4.commons.serialization.Named;
import se.l4.commons.serialization.Serializer;
import se.l4.commons.serialization.Use;
import se.l4.commons.serialization.format.StreamingInput;
import se.l4.commons.serialization.format.StreamingOutput;
import se.l4.commons.serialization.format.Token;
import se.l4.jobs.When;

/**
 * A {@link When} that is backed by a {@link Cron} instance.
 */
@Use(CronBasedWhen.SerializerImpl.class)
@Named(namespace="jobs:schedule", name="cron-based")
public class CronBasedWhen
	implements When
{
	private static final CronParser PARSER
		= new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));

	private final Cron cron;
	private final ExecutionTime time;

	public CronBasedWhen(Cron cron)
	{
		this.cron = cron;
		time = ExecutionTime.forCron(cron);
	}

	@Override
	public OptionalLong get()
	{
		Optional<ZonedDateTime> nextExecution = time.nextExecution(ZonedDateTime.now());
		if(nextExecution.isPresent())
		{
			return OptionalLong.of(nextExecution.get().toInstant().toEpochMilli());
		}
		else
		{
			return OptionalLong.empty();
		}
	}

	public static CronBasedWhen parse(String cronExpression)
	{
		return new CronBasedWhen(PARSER.parse(cronExpression));
	}

	/**
	 * Custom serializer to write out the cron schedule as a string.
	 */
	public static class SerializerImpl
		implements Serializer<CronBasedWhen>
	{
		@Override
		public CronBasedWhen read(StreamingInput in)
			throws IOException
		{
			in.next(Token.VALUE);
			return parse(in.getString());
		}

		@Override
		public void write(CronBasedWhen object, String name, StreamingOutput out)
			throws IOException
		{
			out.write(name, object.cron.asString());
		}
	}
}
