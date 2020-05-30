package se.l4.jobs.internal;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

/**
 * Utilities used to integrated with cron parsing.
 */
public class JobsCron
{
	private static final CronParser parser
		= new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

	private JobsCron()
	{
	}

	public static Cron parse(String cronExpression)
	{
		return parser.parse(cronExpression);
	}
}
