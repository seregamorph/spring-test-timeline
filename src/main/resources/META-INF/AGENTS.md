# spring-test-timeline: notes for agents working in a consumer project

This file ships inside the jar (`META-INF/AGENTS.md`). Read it before decompiling the classes. A `-sources.jar` is also
published, so prefer it over decompiling.

Coordinates: `com.github.seregamorph:spring-test-timeline`, test scope (`testRuntimeOnly` in Gradle). Project page:
https://github.com/seregamorph/spring-test-timeline

## What it does

On the test classpath it records the lifecycle of every Spring test `ApplicationContext` (creating, refreshed, closed,
destroying), which test classes used each context, and JVM metrics. When the test JVM finishes it writes
`spring-test-timeline.html`: a standalone interactive report (D3 is loaded from a CDN, so it needs internet access to
render). The raw data is inlined in it as JSON, inside `<script id="timelineData" type="application/json">`. Extract it
from there for analysis (`</` is escaped as `<\/`, which is still valid JSON).

It needs no code changes and no annotations: everything is registered automatically, as listed below.

## Configuration

The only setting is a system property **of the test JVM** (not of Maven/Gradle itself):

| System property               | Default                    | Meaning                                                      |
|-------------------------------|----------------------------|--------------------------------------------------------------|
| `springTestTimelineReportDir` | `target/` or `build/`, see below | Directory for the report file. A relative path resolves against the test JVM's working directory. Created if missing. |

How the default directory is chosen:

1. If `springTestTimelineReportDir` is set and not blank, it is used.
2. If this is not a Gradle worker and `pom.xml` exists in `${basedir}` (set by Surefire/Failsafe) or else the working
   directory, the report goes to `<basedir>/target/`.
3. If the system property `org.gradle.test.worker` is set, or `build.gradle[.kts]` exists in the working directory, the
   report goes to `./build/`.
4. Otherwise no report is written, and an INFO message says so.

Maven: pass it via Surefire/Failsafe `<systemPropertyVariables>`, or as `-DspringTestTimelineReportDir=...` on the
command line (Surefire forwards user properties). Gradle: `tasks.test { systemProperty("springTestTimelineReportDir", ...) }`.

There is no on/off switch and no setting for the sampling interval, log level or file names. To disable it, remove the
dependency (for example, put it in an opt-in Maven profile).

## Things to check first

- **Several test JVMs overwrite each other.** One report is written per test JVM, always with the same file names.
  With Surefire/Failsafe `forkCount > 1` and `reuseForks=false`, or with both Surefire and Failsafe, or with several
  Gradle test tasks or `maxParallelForks`, the last JVM to finish wins. Give each one its own directory, for example
  `<springTestTimelineReportDir>${project.build.directory}/timeline-${surefire.forkNumber}</springTestTimelineReportDir>`,
  or a different directory per plugin or task.
- **No report at all.** Look in the test output for:
  - `Spring test timeline report: <path>`: the report was written (INFO, logger
    `com.github.seregamorph.testtimeline.EventTrackerSupport`);
  - `Neither Maven nor Gradle build is recognized ...`: set `springTestTimelineReportDir`;
  - `Failed to create report directory ...`: the path isn't writable;
  - `timelineHelper not initialized` (WARN): the suite start callback never ran. Tests must run through the JUnit
    Platform launcher (1.8+, which has `LauncherSessionListener`) or through TestNG with its `ServiceLoader` listeners
    enabled. A custom launcher or a disabled `ServiceLoader` lookup prevents this.
  - `Failed to generate spring-test-timeline.html` (WARN): the report failed, but the test run is not failed.
- **Stdout lines** `SuiteLauncherSessionListener.launcherSessionOpened/Closed` and
  `TimelineExecutionListener.onExecutionStart/Finish` come from this library, not from the project.
- **Log noise.** It logs one INFO line per context event (`Initializing context for test class ...`,
  `Refreshed context for test class ...`, and so on), logger `com.github.seregamorph.testtimeline.TimelineHelper`.
  Raise that logger's level to silence it. It uses SLF4J, so the project's binding decides where these lines go.
- **A test class named `com.github.seregamorph.testtimeline.TimelineHelper$UndefinedTestClass`** in the report means
  the event happened on a thread where no test class was known. Typical causes: context work on a non-test thread
  (async close, shutdown hook, a custom executor), or a test engine that doesn't report a class or method source.
- **Testcontainers.** If `org.testcontainers` is on the classpath, it lists the Docker containers labelled with the
  current Testcontainers session every 250 ms. If Docker isn't reachable, this shows up as warnings, never as test
  failures.

## What it adds to every test context

These are visible in bean listings and context dumps. They are expected and harmless:

- the infrastructure bean `com.github.seregamorph.testtimeline.SpringLifecycleTrackingBean`, which marks creating and
  destroying;
- an `ApplicationListener` (`SpringContextEventTrackerListener`), and a `BeanPostProcessor`
  (`SpringContextUsageBeanPostProcessor`) that reacts to the `<test class>.ORIGINAL` test instance bean;
- a `ContextCustomizer` that is part of `MergedContextConfiguration`. All of its instances are equal, so it **does not
  change Spring's context cache key**. If contexts aren't reused, the cause is in the project's configuration (for
  example different `@MockBean`/`@MockitoBean`, `@TestPropertySource`, profiles, `@DirtiesContext`), not in this library.

Registration (all in the jar's `META-INF`):

- `spring.factories`: `org.springframework.test.context.ContextCustomizerFactory` =
  `SpringContextEventTrackerListenerCustomizerFactory`;
- `services/org.junit.platform.launcher.LauncherSessionListener`: `junit.SuiteSessionListener` (start/finish, once per
  JVM);
- `services/org.junit.platform.launcher.TestExecutionListener`: `junit.CurrentTestExecutionListener` (current test
  class);
- `services/org.testng.ITestNGListener`: `testng.SuiteExecutionListener`, `testng.CurrentTestListener`.

When TestNG runs through the JUnit Platform `testng-engine`, both sets fire. Only the outermost start/finish pair counts,
and the TestNG discovery (dry-run) phase is ignored.

## Compatibility

- Java 8+ bytecode. Compiled against Spring Framework 6.1. `ContextPausedEvent`/`ContextRestartedEvent` (Spring 7.0)
  are looked up reflectively.
- Spring, JUnit Platform/Jupiter, TestNG and Testcontainers are `provided`: the project supplies them, and any subset
  works. The jar's own dependencies are `slf4j-api` and `jackson-databind`.

## Reading the report JSON

```
meta:     { threads, duration, contexts }  // duration in seconds; threads = number of worker threads seen
contexts: [ {
  contextId,                // "1", "2", ...: sequential in this JVM, in creation order
  testClassNames: [ ... ],  // fully qualified; the first created the context, the others reused it from the cache
  events: [ { type, threadId, testClassName, start, end, duration } ]
} ]
metrics:  [ { t, active, heapUsed, heapCommitted, gc, processCpu, systemCpu,
              totalThreads, daemonThreads, containersCount } ]
```

- All times are seconds since the test JVM's suite start.
- Each event's `[start, end]` is the span **ending** at that event: `creating` = from context creation start until the
  first bean was created, `refreshed` = until `ContextRefreshedEvent` (the actual startup cost), `closed` = the time the
  context sat in the cache in use until `ContextClosedEvent`, `destroying` = bean destruction until the context is fully
  torn down. `paused`/`restarted` occur with Spring 7 context pausing.
- `keep_alive` is synthetic: a context that was refreshed but never destroyed before the report was written (still
  cached at JVM exit). Its span runs until the end of the run.
- `threadId` is a 0-based index of the worker thread, not a JVM thread id. `testClassName` is the class running on that
  thread when the event happened, which may differ from the class that created the context.
- Metrics are sampled every 250 ms. `active` = live contexts, heap values in MB, CPU in percent (0-100), `gc` = a GC
  happened around this sample. `containersCount` is `null` without Testcontainers.

Useful questions the JSON answers: how many contexts were created (`meta.contexts`) and why (compare the creators in
`testClassNames[0]`); total startup cost (sum of `creating` + `refreshed` durations); which contexts stayed cached the
longest (`keep_alive`, long `closed`); how many test classes shared each context (`testClassNames.length`).
