package se.l4.jobs.engine;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import reactor.core.publisher.Mono;

public class TestDataRunner
	implements JobRunner<TestData, String>
{
	@Override
	public Mono<String> run(JobEncounter<TestData> encounter)
	{
		return Mono.delay(Duration.ofMillis(50 + ThreadLocalRandom.current().nextInt(120)))
			.map(delay -> {
				TestData td = encounter.getData();
				if(td.getAttempts() == encounter.getAttempt())
				{
					return td.getValue();
				}
				else
				{
					throw encounter.retry();
				}
			});
	}
}
