package se.l4.jobs.engine;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Interface that helps with calculating delays between attempts to run a job.
 */
public interface Delay
{
	/**
	 * Get the delay for the given attempt.
	 */
	OptionalLong getDelay(int attempt);

	/**
	 * Create an exponential delay based on a base in milliseconds. This will
	 * use a default multiplier of `2`, meaning the delay will double at each
	 * attempt.
	 *
	 * <p>
	 * Will calculate the delay using `delay = baseDelay * 2 ^ (attempt-1)`.
	 *
	 * @param baseDelay
	 *   the base delay in milliseconds
	 * @return
	 */
	static Delay exponential(Duration baseDelay)
	{
		return exponential(baseDelay, 2);
	}

	/**
	 * Create an exponential delay based on a base in milliseconds. This will
	 * use a default multiplier of `2`, meaning the delay will double at each
	 * attempt.
	 *
	 * <p>
	 * Will calculate the delay using `delay = baseDelay * 2 ^ (attempt-1)`.
	 *
	 * @param baseDelay
	 *   the base delay in milliseconds
	 * @return
	 */
	static Delay exponential(long baseDelay)
	{
		return exponential(baseDelay, 2);
	}

	/**
	 * Create an exponential delay based on a base time in milliseconds and a
	 * multiplier to apply for each attempt.
	 *
	 * <p>
	 * Will calculate the delay using `delay = baseDelay * multiplier ^ (attempt-1)`.
	 *
	 * @param baseDelay
	 *   the base delay
	 * @param multiplier
	 *   the multiplier to use for the exponential delay, a value of `2`
	 *   would mean that the delay doubles after every attempt
	 * @return
	 */
	static Delay exponential(Duration baseDelay, double multiplier)
	{
		return exponential(baseDelay.toMillis(), multiplier);
	}

	/**
	 * Create an exponential delay based on a base time in milliseconds and a
	 * multiplier to apply for each attempt.
	 *
	 * <p>
	 * Will calculate the delay using `delay = baseDelay * multiplier ^ (attempt-1)`.
	 *
	 * @param baseDelay
	 *   the base delay in milliseconds
	 * @param multiplier
	 *   the multiplier to use for the exponential delay, a value of `2`
	 *   would mean that the delay doubles after every attempt
	 * @return
	 */
	static Delay exponential(long baseDelay, double multiplier)
	{
		return attempt -> OptionalLong.of((long) (baseDelay * Math.pow(multiplier, attempt - 1)));
	}

	/**
	 * Create a delay that limits another {@link Delay} to a maximum value.
	 *
	 * @param source
	 * @param maxDelay
	 */
	static Delay max(Delay source, Duration maxDelay)
	{
		return max(source, maxDelay.toMillis());
	}

	/**
	 * Create a delay that limits another {@link Delay} to a maximum value.
	 *
	 * @param source
	 * @param maxDelay
	 */
	static Delay max(Delay source, long maxDelay)
	{
		return attempt -> {
			OptionalLong delay = source.getDelay(attempt);
			if(! delay.isPresent()) return delay;

			return OptionalLong.of(Math.min(delay.getAsLong(), maxDelay));
		};
	}

	/**
	 * Apply a second of jitter to another delay function.
	 *
	 * @param source
	 *   the source of the main delay
	 * @return
	 */
	static Delay jitter(Delay source)
	{
		return jitter(source, 1000);
	}

	/**
	 * Apply some jitter to another delay function.
	 *
	 * @param source
	 *   the source of the main delay
	 * @param maxJitter
	 *   a jitter to apply
	 * @return
	 */
	static Delay jitter(Delay source, Duration maxJitter)
	{
		return jitter(source, maxJitter.toMillis());
	}

	/**
	 * Apply some jitter to another delay function.
	 *
	 * @param source
	 *   the source of the main delay
	 * @param maxJitter
	 *   a jitter to apply
	 * @return
	 */
	static Delay jitter(Delay source, long maxJitter)
	{
		return attempt -> {
			OptionalLong delay = source.getDelay(attempt);
			if(! delay.isPresent()) return delay;

			return OptionalLong.of(delay.getAsLong() + ThreadLocalRandom.current().nextLong(maxJitter));
		};
	}
}
