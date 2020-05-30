package se.l4.jobs.backend.silo;

import org.junit.After;
import org.junit.Before;

import se.l4.jobs.engine.BackendTest;
import se.l4.jobs.engine.JobsBackend;
import se.l4.silo.engine.LocalSilo;
import se.l4.silo.engine.builder.SiloBuilder;

/**
 * Test {@link SiloJobsBackend}.
 */
public class SiloJobsBackendTest
	extends BackendTest
{
	private LocalSilo silo;

	@Override
	protected JobsBackend createBackend()
	{
		return new SiloJobsBackend(silo.structured("jobs:queue"));
	}

	@Before
	public void before()
		throws Exception
	{
		SiloBuilder builder = LocalSilo.open(newTempDir());

		SiloJobsBackend.defineJobEntity(builder, "jobs:queue");

		silo = builder.build();

		super.before();
	}

	@After
	public void after()
		throws Exception
	{
		super.after();

		silo.close();
	}
}
