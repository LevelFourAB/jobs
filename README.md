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
  .at(When.after(Duration.ofMinutes(20)))
  .submit();
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
