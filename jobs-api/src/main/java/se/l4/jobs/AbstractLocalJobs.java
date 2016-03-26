package se.l4.jobs;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

import com.google.common.collect.Maps;

/**
 * Abstract implementation of {@link LocalJobs} that helps with runner registration.
 * 
 * @author Andreas Holstenson
 *
 */
public abstract class AbstractLocalJobs
	implements LocalJobs
{
	private final Map<Class<?>, JobRunner<?>> runners;
	
	public AbstractLocalJobs()
	{
		runners = Maps.newHashMap();
	}
	
	@Override
	public void registerRunner(JobRunner<?> runner)
	{
		registerRunner((Class) getDataType(runner.getClass()), runner);
	}
	
	private Class<?> getDataType(Class<?> type)
	{
		Type[] genericInterfaces = type.getGenericInterfaces();
		for(Type t : genericInterfaces)
		{
			if(t instanceof ParameterizedType)
			{
				ParameterizedType pt = (ParameterizedType) t;
				if(pt.getRawType() == JobRunner.class)
				{
					Type[] arguments = pt.getActualTypeArguments();
					return findClass(arguments[0]);
				}
			}
		}
		
		throw new RuntimeException("Could not find data type for " + type);
	}
	
	private Class<?> findClass(Type type)
	{
		if(type instanceof Class)
		{
			return (Class) type;
		}
		else if(type instanceof ParameterizedType)
		{
			return (Class) ((ParameterizedType) type).getRawType();
		}
		
		throw new RuntimeException("Could not determine type for " + type);
	}
	
	@Override
	public <T> void registerRunner(Class<T> dataType, JobRunner<T> runner)
	{
		if(runners.containsKey(dataType))
		{
			throw new IllegalArgumentException(dataType + " is already registered to " + runners.get(dataType));
		}
		
		runners.put(dataType, runner);
	}
	
	protected JobRunner<?> getRunner(Object data)
	{
		Class<?> type = data.getClass();
		while(type != Object.class)
		{
			JobRunner<?> runner = runners.get(type);
			if(runner != null) return runner;
			
			runner = getRunnerFromInterface(type);
			if(runner != null) return runner;
			
			type = type.getSuperclass();
		}
		
		return null;
	}
	
	private JobRunner<?> getRunnerFromInterface(Class<?> type)
	{
		Class<?>[] interfaces = type.getInterfaces();
		for(Class<?> intf : interfaces)
		{
			JobRunner<?> runner = runners.get(intf);
			if(runner != null) return runner;
		}
		
		for(Class<?> intf : interfaces)
		{
			JobRunner<?> runner = getRunnerFromInterface(intf);
			if(runner != null) return runner;
		}
		
		return null;
	}
}
