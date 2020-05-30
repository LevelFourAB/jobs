package se.l4.jobs;

import java.time.Instant;
import java.util.OptionalLong;

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
	 * Indicate that this should be repeated forever.
	 */
	default Schedule repeat()
	{
		return null;
	}
}
