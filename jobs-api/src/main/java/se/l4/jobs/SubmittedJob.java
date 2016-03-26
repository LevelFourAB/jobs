package se.l4.jobs;

import java.util.concurrent.CompletableFuture;


/**
 * Information about a submitted job.
 * 
 * @author Andreas Holstenson
 *
 */
public interface SubmittedJob<T>
{
	CompletableFuture<T> result();
}
