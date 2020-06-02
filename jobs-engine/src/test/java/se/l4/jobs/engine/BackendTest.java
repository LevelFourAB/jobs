package se.l4.jobs.engine;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.carrotsearch.randomizedtesting.RandomizedTest;

import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import se.l4.jobs.Job;
import se.l4.jobs.JobCancelledException;
import se.l4.jobs.Schedule;

/**
 * Abstract base class for tests that a backend should pass.
 */
public abstract class BackendTest
	extends RandomizedTest
{
	protected LocalJobs jobs;

	protected abstract JobsBackend createBackend();

	protected LocalJobs createJobs()
	{
		return LocalJobs.builder()
			.withBackend(createBackend())
			.addRunner(new TestDataRunner())
			.withDefaultDelay(attempt -> OptionalLong.of(1))
			.build();
	}

	@Before
	public void before()
		throws Exception
	{
		jobs = createJobs();
		jobs.start();
	}

	@After
	public void after()
		throws Exception
	{
		jobs.stop();
	}

	@Test
	public void test1()
	{
		CompletableFuture<String> future = jobs.add(new TestData("a", 1))
			.submit()
			.result();

		String value = future.join();
		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test2()
	{
		CompletableFuture<String> future = jobs.add(new TestData("a", 2))
			.submit()
			.result();

		String value = future.join();
		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test3()
	{
		CompletableFuture<String> future = jobs.add(new TestData("a", 2))
			.withSchedule(Schedule.after(Duration.ofSeconds(1)))
			.submit()
			.result();

		String value = future.join();
		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test4()
	{
		CompletableFuture<String> future = jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(500)))
			.submit()
			.result();

		String value = future.join();
		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test5()
		throws Exception
	{
		CompletableFuture<String> future = jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(700)))
			.submit()
			.result();

		// Replace the invocation with a new one
		jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(1000)))
			.submit()
			.result();

		// After the original 500 ms we should not be done
		Thread.sleep(500);
		MatcherAssert.assertThat(future.isDone(), is(false));

		String value = future.join();
		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test6()
		throws Exception
	{
		Job job = jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(500)))
			.submit();

		job.cancel();

		try
		{
			job.result().join();

			fail();
		}
		catch(CompletionException e)
		{
			MatcherAssert.assertThat(e.getCause(), instanceOf(JobCancelledException.class));
		}
	}

	@Test
	public void test7()
		throws Exception
	{
		Job job = jobs.add(new TestData("a", 1))
			.withId("knownId")
			.withSchedule(Schedule.after(Duration.ofMillis(10)).repeat())
			.submit();

		// First result
		job.result().join();

		// Second result
		job.result().join();
	}
}
