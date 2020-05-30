package se.l4.jobs;

import java.time.Instant;
import java.util.OptionalLong;

import se.l4.jobs.internal.InfiniteSchedule;

/**
 * When something should occur, similar to {@link Instant} in that it will
 * represent an instant in time. Retrieve instances using the static methods
 * in {@link Schedule}.
 */
public interface When
{
	/**
	 * Get the time in milliseconds from the epoch that this instance
	 * represents.
	 *
	 * @return
	 */
	OptionalLong get();

	/**
	 * Indicate that this should be repeated until this instance either stops
	 * returning a value or if the value returned is in the past.
	 *
	 * @return
	 */
	default Schedule repeat()
	{
		return new InfiniteSchedule(this);
	}
}
