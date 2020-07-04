package se.l4.jobs.engine;

import java.time.Instant;

import se.l4.jobs.JobException;

/**
 * Extension to {@link JobRetryException} used to flag that a job failed, but
 * will be retried. It is recommended to create instances of this exception via
 * {@link JobEncounter}.
 */
public class JobRetryException
	extends JobException
{
	private final Instant when;

	public JobRetryException(
		String msg,
		Instant when
	)
	{
		super(msg);

		this.when = when;
	}

	public JobRetryException(
		String msg,
		Throwable cause,
		Instant when
	)
	{
		super(msg, cause);

		this.when = when;
	}

	/**
	 * Check if an instant for a retry is available.
	 *
	 * @return
	 */
	public boolean willRetry()
	{
		return when != null;
	}

	/**
	 * Get the timestamp at which to retry this job. If this is {@code null}
	 * no retry will be performed.
	 *
	 * @return
	 */
	public Instant getWhen()
	{
		return when;
	}
}
