package se.l4.jobs;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.OptionalLong;

import com.cronutils.model.Cron;

import se.l4.jobs.internal.CronBasedWhen;
import se.l4.jobs.internal.DelayedWhen;
import se.l4.jobs.internal.SpecificTimeWhen;

/**
 * Schedule that is used to represents when a job is run and how it repeats.
 */
public interface Schedule
{
	/**
	 * Get the next execution of this schedule.
	 *
	 * @return
	 */
	OptionalLong getNextExecution();

	/**
	 * Marker for running a job as soon as possible.
	 *
	 * @return
	 */
	static When now()
	{
		return new SpecificTimeWhen(0);
	}

	/**
	 * Run the job a the given timestamp in milliseconds.
	 *
	 * @param timestamp
	 * @return
	 */
	static When at(long timestamp)
	{
		return new SpecificTimeWhen(timestamp);
	}

	/**
	 * Run the job at the given instant.
	 *
	 * @param instant
	 * @return
	 */
	static When at(Instant instant)
	{
		return at(instant.toEpochMilli());
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
	 * Create a {@link When} that will resolve to the next execution time
	 * represented by the given cron expression. The cron expression here
	 * is in <a href="https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/scheduling/support/CronSequenceGenerator.html">
	 * Spring format</a>.
	 *
	 * <p>
	 * It supports six fields, second, minute, hour, day, month, weekday.
	 *
	 * Examples (via Spring documentation):
	 *
	 * <ul>
	 * 	<li>{@code 0 0 * * * *} = the top of every hour of every day
	 *  <li>{@code *&#47;10 * * * * *} = every ten seconds.
	 *  <li>{@code 0 0 8-10 * * *} = 8, 9 and 10 o'clock of every day.
	 *  <li>{@code 0 0 6,19 * * *} = 6:00 AM and 7:00 PM every day.
	 *  <li>{@code 0 0/30 8-10 * * *} = 8:00, 8:30, 9:00, 9:30, 10:00 and 10:30 every day.
	 *  <li>{@code 0 0 9-17 * * MON-FRI} = on the hour nine-to-five weekdays
	 *  <li>{@code 0 0 0 25 12 ?} = every Christmas Day at midnight
	 * </ul>
	 *
	 * @param expression
	 *   the cron expression to parse
	 * @return
	 */
	static When at(String expression)
	{
		return CronBasedWhen.parse(expression);
	}

	/**
	 * Create a {@link When} that will resolve to the next execution time
	 * described by the given {@link Cron} object.
	 *
	 * @param cron
	 * @return
	 */
	static When at(Cron cron)
	{
		return new CronBasedWhen(cron);
	}

	/**
	 * Run the job after the given duration.
	 *
	 * @param duration
	 * @return
	 */
	static When after(Duration duration)
	{
		return new DelayedWhen(duration.toMillis());
	}
}
