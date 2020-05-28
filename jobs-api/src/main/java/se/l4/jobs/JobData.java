package se.l4.jobs;

/**
 * Marker interface used for things that can be submitted as jobs.
 *
 * <p>
 * Data classes should be {@link SerializerCollection serializable} and have a
 * {@link Named unique name}. This is to ensure that can be utilized by
 * different implementations of {@link Jobs}, such as implementations that send
 * jobs over the network or persist the job queue.
 */
public interface JobData
{
}
