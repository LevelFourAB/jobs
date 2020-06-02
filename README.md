# Jobs

Job runner for Java with support for delays, retries and different backends.

```java
LocalJobs jobs = LocalJobs.builder()
  .setBackend(new InMemoryJobsBackend())
  .addRunner(new SendReportRunner())
  .build();

// Submit a job that will run now
jobs.add(new SendReport("example@example.org"))
  .submit();

// Submit a job that will run later
jobs.add(new SendReport("example@example.org"))
  .withSchedule(Schedule.after(Duration.ofMinutes(20)))
  .submit();
```

## Scheduling via cron and repeating jobs

One off jobs can be scheduled via a cron expression:

```java
// Submit a job that will run at 10 am
jobs.add(new SendReport("example@example.org"))
  .withSchedule(Schedule.at("0 0 10 * * *"))
  .submit();
```

It's also possible to schedule jobs that will repeat:

```java
// Submit a job that will run at 10 am and repeat forever
jobs.add(new SendReport("example@example.org"))
  .withId("report-sender")
  .withSchedule(
    Schedule.at("0 0 10 * * *").repeat()
  )
  .submit();
```

Scheduling another job with the same id will replace the existing job. It's 
possible to cancel a previously submitted repeating job by fetching it and
calling `cancel`.

```java
Optional<Job<?, ?>> job = jobs.getViaId("report-sender");

if(job.isPresent()) {
  // Cancel the job if it is still scheduled
  job.get().cancel();
}
```

## Job data and runners

In this library jobs are represented by data classes that have a job runner
associated with them.

A data object should be a simple class and in most cases should be serializable:

```java
import se.l4.commons.serialization.Expose;
import se.l4.commons.serialization.ReflectionSerializer;
import se.l4.commons.serialization.Use;

@Use(ReflectionSerializer.class)
@Named(namespace="reports", name="send-report-job")
public class SendReport extends JobData<Void> {
  @Expose
  private final String email;

  public SendReport(@Expose("email") String email) {
    this.email = email;
  }

  public String getEmail() {
    return email;
  }
}
```

Such a class then has a runner associated with it:

```java
public class SendReportRunner implements JobRunner<SendReport, Void> {
  public void run(JobEncounter<SendReport, Void> encounter) {
    // Code that performs the job goes here    
  }
}
```

Runners are registered when `LocalJobs` are built either manually by calling
`addRunner` or using a `TypeFinder` for automatic registration.

## Returning results from jobs

To return a result from a job set the type a job will return in it's data
class:

```java
public class JobDataWithResult implements JobData<String> {
  ...
}
```

A runner can then use `complete` in `JobEncounter` to send back a result:

```java
public class JobDataWithResultRunner implements JobRunner<JobDataWithResult, String> {
  public run(JobEncounter<JobDataWithResult, String> encounter) {
    String result = ...;
      
    // Indicate that the job has completed
    encounter.complete(result);
  }
}
```

When submitting this job you can listen for the result:

```java
// Submit the job
Job<JobDataWithResult, String> job = jobs.add(new JobDataWithResult(...))
  .submit();

/* 
 * Job.result can be used to retrieve a future that will resolve when the
 * job completes or fails completely.
 */
CompletableFuture<String> future = job.result();

// Use any method on the future, such as join to wait for the result
String result = future.join();
```

## Managing failures and retries

In many cases it's important to have control over how a job is retried if it
fails. The default behavior is to retry a job up to 5 times, delaying the
run with 1 second for the first retry and then doubling the time.

Control over this behavior is done via the methods `fail` and `failNoRetry`:

```java
/* 
 * Delay for 1 minute after first attempt and then double at each failed
 * attempt.
 */
private final Delay customDelay = Delay.exponential(Duration.ofMinutes(1));

public run(JobEncounter<Data, Void> encounter) {
  try {
    // Perform the job here
  
    // Indicate that the job has completed
    encounter.complete();
  } catch(Throwable t) {
    if(encounter.getAttempt() >= 10) {
      /*
       * This is the tenth attempt - lets skip retrying it at this point.
       * The next delay would be 512 minutes (~8.5 hours) and this attempt was
       * delayed by 256 minutes (~4.2 hours) from the previous attempt.
       */
      encounter.failNoRetry(t);
    } else {
      // Fail and retry this job later, delaying the run by customDelay
      encounter.fail(t, customDelay);
    }
  }
}
```

`Delay` supports combining as well, such as limiting clamping to a maximum 
value, applying a jitter or limiting the number of attempts:

```java
/*
 * Clamp to a maximum delay - when otherDelay starts to return values longer
 * than a day this will return one day forever.
 */
Delay clamped = Delay.clampMax(otherDelay, Duration.ofDays(1));

/*
 * Apply a random jitter to spread out retries a bit.
 */
Delay jittered = Delay.jitter(otherDelay);
Delay jittered = Delay.jitter(otherDelay, Duration.ofSeconds(1));

/*
 * Limit the number of attempts of a job (alternative to getAttempt() >= N).
 */
Delay limited = Delay.limitAttempts(otherDelay, 10);
```

It's also possible to take full control and use a specific sequence of delays,
this delay will use the specified durations between retries and then give up
after the sixth attempt of the job:

```java
Delay sequence = Delay.sequence(
	Duration.ofMinutes(1), // first retry 1 minute after first failure
	Duration.ofMinutes(10), // second retry 10 minutes after second failure
	Duration.ofMinutes(30), // third retry 30 minutes after third failure
	Duration.ofMinutes(60), // fourth retry 60 minutes after fourth failure,
	Duration.ofMinutes(120), // fifth retry 30 minutes after fifth failure
);
```

## Automatic discovery of job runners

Job runners can be discovered automatically using a `TypeFinder`:

```java
// The type finder used to locate job runners
TypeFinder typeFinder = TypeFinder.builder()
  .addPackage("com.example.package")
  .setInstanceFactory(instanceFactory)
  .build();

// Pass the type finder to the builder to automatically find runners
LocalJobs.builder()
  .withBackend(backend)
  .withTypeFinder(typeFinder)
  .build();
```

## Backends

### Memory based

`InMemoryJobsBackend` is a backend included within the module `jobs-engine`.
It provides a queue that is kept entirely in memory without any persistance,
meaning that any jobs submitted will be lost if the backend is stopped or the
process is restarted.

There is also a risk that this type of backend will run of out memory if too
many jobs are submitted.

### Persisted using Silo

`SiloJobsBackend` is available in the module `jobs-backend-silo` and will
persist jobs using a [Silo instance](https://github.com/levelfourab/silo).
This type of backend is useful for single-process systems that want to store
jobs between restarts.

For this backend an entity needs to be defined on the `SiloBuilder` when 
creating the `LocalSilo` instance:

```java
SiloBuilder builder = LocalSilo.open(pathToStorage);

SiloJobsBackend.defineJobEntity(builder, "jobs:queue");

Silo silo = builder.build();
```

This entity should then be passed to the backend:

```java
new SiloJobsBackend(silo.structured("jobs:queue"));
```
