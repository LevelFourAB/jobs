package se.l4.jobs.engine;

import se.l4.commons.serialization.Expose;
import se.l4.commons.serialization.Named;
import se.l4.commons.serialization.ReflectionSerializer;
import se.l4.commons.serialization.Use;
import se.l4.jobs.JobData;

@Use(ReflectionSerializer.class)
@Named(namespace="test", name="test-data")
public class TestData
	implements JobData
{
	@Expose
	private final String value;

	@Expose
	private final int attempts;

	public TestData(@Expose("value") String value, @Expose("attempts") int attempts)
	{
		this.value = value;
		this.attempts = attempts;
	}

	public String getValue()
	{
		return value;
	}

	public int getAttempts()
	{
		return attempts;
	}
}
