package se.l4.jobs;

/**
 * Extension to {@link JobException} used when a job is cancelled via
 * {@link Job#cancel()}.
 */
public class JobCancelledException
	extends JobException
{
	public JobCancelledException(String msg)
	{
		super(msg);
	}
}
