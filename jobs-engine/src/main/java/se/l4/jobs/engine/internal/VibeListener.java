package se.l4.jobs.engine.internal;

import se.l4.jobs.Job;
import se.l4.jobs.engine.JobListener;
import se.l4.vibe.Vibe;
import se.l4.vibe.percentile.CombinedProbes;
import se.l4.vibe.percentile.CombinedProbes.CombinedData;
import se.l4.vibe.probes.CountingProbe;
import se.l4.vibe.probes.SampledProbe;

/**
 * Listener that will report metrics into a {@link Vibe} instance.
 */
public class VibeListener
	implements JobListener
{
	private final CountingProbe scheduledProbe;
	private final CountingProbe startedProbe;
	private final CountingProbe completedProbe;
	private final CountingProbe retryProbe;
	private final CountingProbe failedProbe;

	public VibeListener(Vibe vibe)
	{
		scheduledProbe = new CountingProbe();
		startedProbe = new CountingProbe();
		completedProbe = new CountingProbe();
		retryProbe = new CountingProbe();
		failedProbe = new CountingProbe();

		SampledProbe<CombinedData<Long>> probe = CombinedProbes.<Long>builder()
			.add("scheduled", scheduledProbe)
			.add("started", startedProbe)
			.add("completed", completedProbe)
			.add("failed", failedProbe)
			.add("scheduledRetry", retryProbe)
			.create();

		vibe.sample(probe)
			.at("queue")
			.export();
	}

	@Override
	public void jobScheduled(Job job)
	{
		scheduledProbe.increase();
	}

	@Override
	public void jobStarted(Job job)
	{
		startedProbe.increase();
	}

	@Override
	public void jobCompleted(Job job)
	{
		completedProbe.increase();
	}

	@Override
	public void jobFailed(Job job, boolean willRetry)
	{
		if(willRetry)
		{
			retryProbe.increase();
		}
		else
		{
			failedProbe.increase();
		}
	}
}
