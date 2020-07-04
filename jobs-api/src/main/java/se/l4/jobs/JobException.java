package se.l4.jobs;

/**
 * Exception used for jobs. This is the exception that will be thrown via
 * the {@link java.util.concurrent.CompletableFuture} returned when a job
 * is submitted via {@link JobBuilder#submit}.
 */
public class JobException
	extends RuntimeException
{
	public JobException(String msg)
	{
		super(msg);
	}

	public JobException(String msg, Throwable cause)
	{
		super(msg, cause);
	}

	public static JobException wrap(Throwable t)
	{
		if(t instanceof JobException)
		{
			return (JobException) t;
		}

		return new JobException(t.getMessage(), t);
	}
}
