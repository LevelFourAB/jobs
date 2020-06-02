package se.l4.jobs.engine;

import se.l4.jobs.JobException;

public class TestDataRunner
	implements JobRunner<TestData, String>
{
	@Override
	public void run(JobEncounter<TestData, String> encounter)
		throws Exception
	{
		TestData td = encounter.getData();
		if(td.getAttempts() == encounter.getAttempt())
		{
			encounter.complete(td.getValue());
		}
		else
		{
			encounter.fail(new JobException("Needs to be retried later"));
		}
	}
}
