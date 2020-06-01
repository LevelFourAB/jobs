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
Optional<Job> job = jobs.getViaId("report-sender");

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
public class SendReport extends JobData {
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
public class SendReportRunner implements JobRunner<SendReport> {
  public void run(JobEncounter<SendReport> encounter) {
    // Code that performs the job goes here    
  }
}
```

Runners are registered when `LocalJobs` are built either manually by calling
`addRunner` or using a `TypeFinder` for automatic registration.

## Job control

In many cases it's important to have control over how a job is retried if it
fails or if you want to deliver a result.

```java
/* 
 * Delay for 1 minute after first attempt and then double at each failed
 * attempt. Also cap the delay at one day.
 */
Delay customDelay = Delay.max(Delay.exponential(Duration.ofMinutes(1)), Duration.ofDays(1));

public run(JobEncounter<Data> encounter) {
  try {
    // Do something for the job here
    
    // Indicate that the job has completed
    encounter.complete(result);
  } catch(Throwable t) {
    if(encounter.getAttempt() >= 5) {
      // This is the fifth attempt - lets skip retrying it at this point
      encounter.failNoRetry(t);
    } else {
      // Fail with the custom delay
      encounter.fail(t, customDelay);
    }
  }
}
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
