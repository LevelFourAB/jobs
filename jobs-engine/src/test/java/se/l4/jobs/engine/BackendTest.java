package se.l4.jobs.engine;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.OptionalLong;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;

import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import reactor.core.publisher.Mono;
import se.l4.jobs.Job;
import se.l4.jobs.JobNotFoundException;
import se.l4.jobs.Schedule;
import se.l4.jobs.engine.backend.JobsBackend;

/**
 * Abstract base class for tests that a backend should pass.
 */
@ThreadLeakFilters(filters={ ReactorThreadLeakFilter.class })
public abstract class BackendTest
{
	protected LocalJobs jobs;

	protected abstract Mono<JobsBackend> createBackend();

	protected LocalJobs createJobs()
	{
		return LocalJobs.builder()
			.withBackend(createBackend())
			.addRunner(new TestDataRunner())
			.withDefaultDelay(attempt -> OptionalLong.of(1))
			.start()
			.block();
	}

	@Before
	public void before()
		throws Exception
	{
		jobs = createJobs();
	}

	@After
	public void after()
		throws Exception
	{
		if(jobs != null)
		{
			jobs.stop().block();
		}
	}

	@Test
	public void test1()
	{
		String value = jobs.add(new TestData("a", 1))
			.submit()
			.flatMap(Job::result)
			.block(Duration.ofSeconds(1));

		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test2()
	{
		String value = jobs.add(new TestData("a", 2))
			.submit()
			.flatMap(Job::result)
			.block(Duration.ofSeconds(1));

		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test3()
	{
		String value = jobs.add(new TestData("a", 2))
			.withSchedule(Schedule.after(Duration.ofSeconds(1)))
			.submit()
			.flatMap(Job::result)
			.block(Duration.ofSeconds(2));

		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test4()
	{
		String value = jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(500)))
			.submit()
			.flatMap(Job::result)
			.block(Duration.ofSeconds(1));

		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test5()
		throws Exception
	{
		Job<TestData, String> job = jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(700)))
			.submit()
			.block(Duration.ofSeconds(1));

		// Replace the invocation with a new one
		jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(1000)))
			.submit()
			.block(Duration.ofSeconds(1));

		// After the original 500 ms we should not be done
		Thread.sleep(500);
		//MatcherAssert.assertThat(future.isDone(), is(false));

		String value = job.result().block(Duration.ofSeconds(1));
		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test6()
	{
		Job<TestData, String> job = jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(500)))
			.submit()
			.block(Duration.ofSeconds(1));

		job.cancel().block(Duration.ofSeconds(1));

		try
		{
			job.result().block(Duration.ofSeconds(1));

			fail();
		}
		catch(JobNotFoundException e)
		{
			// Do nothing, a JobNotFoundException is expected
		}
	}

	@Test
	public void test7()
	{
		Job<TestData, String> job = jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(10)).repeat())
			.submit()
			.block(Duration.ofSeconds(1));

		// First result
		job.result().block(Duration.ofSeconds(1));

		// Second result
		job.result().block(Duration.ofSeconds(1));
	}

	@Test
	public void test8()
	{
		for(int i=0; i<50; i++) {
			jobs.add(new TestData("a", 1))
				.submit()
				.subscribe();
		}

		String value = jobs.add(new TestData("a", 1))
			.submit()
			.flatMap(Job::result)
			.block(Duration.ofSeconds(1));

		MatcherAssert.assertThat(value, is("a"));

		/*
		 * As this test doesn't wait for all invocations stop the jobs instance
		 * to prevent thread leaks.
		 */
		jobs.stop().block();
	}
}
