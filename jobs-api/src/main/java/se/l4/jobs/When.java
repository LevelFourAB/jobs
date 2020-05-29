package se.l4.jobs;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Marker for when jobs should be run. Essentially wraps a timestamp.
 *
 */
public interface When
{
	/**
	 * Get the UNIX time that this instance represents. If this is
	 * -1, this represent <i>the current time</i>.
	 *
	 * @return
	 */
	long getTimestamp();

	/**
	 * Marker for running a job as soon as possible.
	 *
	 * @return
	 */
	static When now()
	{
		return () -> 1;
	}

	/**
	 * Run the job a the given timestamp in milliseconds.
	 *
	 * @param timestamp
	 * @return
	 */
	static When at(long timestamp)
	{
		return () -> timestamp;
	}

	/**
	 * Run the job at the given instant.
	 *
	 * @param instant
	 * @return
	 */
	static When at(Instant instant)
	{
		return () -> instant.toEpochMilli();
	}

	/**
	 * Run the job at the given date and time.
	 *
	 * @param dt
	 * @return
	 */
	static When at(ZonedDateTime dt)
	{
		return at(dt.toInstant());
	}

	/**
	 * Run the job after the given duration.
	 *
	 * @param duration
	 * @return
	 */
	static When after(Duration duration)
	{
		return at(System.currentTimeMillis() + duration.toMillis());
	}
}