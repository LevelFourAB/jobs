package se.l4.jobs.engine;

import reactor.core.publisher.Mono;
import se.l4.jobs.engine.backend.JobsBackend;

/**
 * Test the {@link InMemoryJobsBackend}.
 */
public class InMemoryJobsBackendTest
	extends BackendTest
{
	@Override
	protected Mono<JobsBackend> createBackend()
	{
		return InMemoryJobsBackend.create();
	}
}
