package se.l4.jobs.engine.backend;

import java.util.Optional;

import se.l4.commons.io.Bytes;
import se.l4.commons.serialization.QualifiedName;
import se.l4.jobs.Schedule;

/**
 * Information about job that has been queued up to be run.
 *
 * @param <D>
 */
public interface BackendJobData
{
	/**
	 * Get the identifier of the job.
	 *
	 * @return
	 */
	long getId();

	/**
	 * Create a copy of this data with another id.
	 *
	 * @param id
	 * @return
	 */
	BackendJobData withId(long id);

	/**
	 * Get the known identifier for this job.
	 *
	 * @return
	 */
	Optional<String> getKnownId();

	/**
	 * Get the qualified name of the data.
	 *
	 * @return
	 */
	QualifiedName getDataName();

	/**
	 * Get the binary data of the job.
	 *
	 * @return
	 */
	Bytes getData();

	/**
	 * Get the time at which this job was first scheduled to run.
	 *
	 * @return
	 */
	long getFirstScheduled();

	/**
	 * Get the time in milliseconds from the epoch for when this job should be
	 * run.
	 *
	 * @return
	 *   time in milliseconds from the epoch
	 */
	long getScheduledTime();

	/**
	 * Get the schedule for when this job runs.
	 *
	 * @return
	 */
	Optional<Schedule> getSchedule();

	/**
	 * Get the number of attempts to run this job has been made.
	 *
	 * @return
	 */
	int getAttempt();
}
