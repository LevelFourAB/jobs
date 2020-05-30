package se.l4.jobs.engine;

/**
 * Test the {@link InMemoryJobsBackend}.
 */
public class InMemoryJobsBackendTest
	extends BackendTest
{
	@Override
	protected JobsBackend createBackend()
	{
		return new InMemoryJobsBackend();
	}
}
