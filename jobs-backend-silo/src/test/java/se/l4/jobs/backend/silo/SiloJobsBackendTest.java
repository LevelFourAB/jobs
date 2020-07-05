package se.l4.jobs.backend.silo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import reactor.core.publisher.Mono;
import se.l4.jobs.engine.BackendTest;
import se.l4.jobs.engine.backend.JobsBackend;
import se.l4.silo.engine.LocalSilo;
import se.l4.silo.engine.builder.SiloBuilder;

/**
 * Test {@link SiloJobsBackend}.
 */
public class SiloJobsBackendTest
	extends BackendTest
{
	private LocalSilo silo;

	@Rule
	public TemporaryFolder folder = TemporaryFolder.builder().assureDeletion().build();

	@Override
	protected Mono<JobsBackend> createBackend()
	{
		return Mono.just(new SiloJobsBackend(silo.structured("jobs:queue")));
	}

	@Before
	public void before()
		throws Exception
	{
		SiloBuilder builder = LocalSilo.open(folder.newFolder());

		SiloJobsBackend.defineJobEntity(builder, "jobs:queue");

		silo = builder.build();

		super.before();
	}

	@After
	public void after()
		throws Exception
	{
		super.after();

		if(silo != null)
		{
			silo.close();
		}
	}

	@Override
	@Test
	public void test1() {
		// TODO Auto-generated method stub
		super.test1();
	}

	@Override
	@Test
	public void test8() {
		// TODO Auto-generated method stub
		super.test8();
	}
}
