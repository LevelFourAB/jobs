package se.l4.jobs.engine;

import se.l4.jobs.JobException;

/**
 * Extension to {@link JobRetryException} used to flag that a job failed, but
 * will be retried.
 */
public class JobRetryException
	extends JobException
{
	public JobRetryException(String msg, Throwable cause)
	{
		super(msg, cause);
	}

	public JobRetryException(String msg)
	{
		super(msg);
	}
}
