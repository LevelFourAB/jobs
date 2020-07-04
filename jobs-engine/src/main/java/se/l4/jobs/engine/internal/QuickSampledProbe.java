package se.l4.jobs.engine.internal;

import java.util.function.Supplier;

import se.l4.vibe.probes.AbstractSampledProbe;

public class QuickSampledProbe<T>
	extends AbstractSampledProbe<T>
{
	private final Supplier<T> supplier;

	public QuickSampledProbe(Supplier<T> supplier)
	{
		this.supplier = supplier;
	}

	@Override
	public T peek()
	{
		return supplier.get();
	}

	@Override
	protected T sample0()
	{
		return peek();
	}
}
