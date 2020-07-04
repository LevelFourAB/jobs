package se.l4.jobs;

import reactor.core.publisher.Mono;

/**
 * Builder for submitting a job.
 */
public interface JobBuilder<D extends JobData<R>, R>
{
	/**
	 * Set the identifier of this job. This is required when using a
	 * {@link #withSchedule(Schedule)} that is repeatable.
	 *
	 * @param id
	 * @return
	 */
	JobBuilder<D, R> withId(String id);

	/**
	 * Set when the job should be run.
	 *
	 * @param when
	 * @return
	 */
	JobBuilder<D, R> withSchedule(When when);

	/**
	 * Set when the job should be run.
	 *
	 * @param schedule
	 * @return
	 */
	JobBuilder<D, R> withSchedule(Schedule schedule);

	/**
	 * Submit the job.
	 *
	 * @return
	 */
	Mono<Job<D, R>> submit();
}
