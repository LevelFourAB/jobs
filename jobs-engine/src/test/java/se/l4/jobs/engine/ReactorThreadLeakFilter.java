package se.l4.jobs.engine;

import com.carrotsearch.randomizedtesting.ThreadFilter;

public class ReactorThreadLeakFilter
	implements ThreadFilter
{

	@Override
	public boolean reject(Thread t)
	{
		return t.getName().startsWith("parallel-");
	}

}
