package se.l4.jobs;

import java.util.concurrent.CompletableFuture;


/**
 * Information about a submitted job.
 */
public interface SubmittedJob<T>
{
	CompletableFuture<T> result();
}
