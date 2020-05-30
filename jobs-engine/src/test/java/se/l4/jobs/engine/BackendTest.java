package se.l4.jobs.engine;

import static org.hamcrest.CoreMatchers.is;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.carrotsearch.randomizedtesting.RandomizedTest;

import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import se.l4.jobs.When;

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
			.withDefaultDelay(attempt -> 1)
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
			.submit();

		String value = future.join();
		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test2()
	{
		CompletableFuture<String> future = jobs.add(new TestData("a", 2))
			.submit();

		String value = future.join();
		MatcherAssert.assertThat(value, is("a"));
	}

	@Test
	public void test3()
	{
		CompletableFuture<String> future = jobs.add(new TestData("a", 2))
			.schedule(When.after(Duration.ofSeconds(1)))
			.submit();

		String value = future.join();
		MatcherAssert.assertThat(value, is("a"));
	}
}
