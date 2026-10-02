# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A test-scoped library (`com.github.seregamorph:spring-test-timeline`) that, once on a project's test classpath, records
the lifecycle of every Spring test `ApplicationContext` (creation, refresh, close, destroy) plus JVM metrics, and writes
a timeline report when the test JVM finishes. It needs no user configuration: everything is wired through
`ServiceLoader`/`spring.factories` registrations in `src/main/resources/META-INF`.

## Build

Maven, via the wrapper:

```
./mvnw clean install          # compile, javadoc, sources jar, install locally
./mvnw -q compile             # quick compile check
```

- The bytecode target is **Java 8** (`maven.compiler.source/target=1.8`), so don't use newer language features or APIs,
  even though the dependencies (Spring 6.1) are newer.
- Spring, JUnit Platform/Jupiter, the TestNG engine and Testcontainers are all `provided`: the code must still work when
  only some of them are on the user's classpath (see how `TestcontainersMetricsCollector.isAvailable()` and the
  reflective `ContextPausedEvent`/`ContextRestartedEvent` lookup in `SpringContextEventTrackerListener` handle this).
- There are no tests in this repo (no `src/test`). To check a change, install it locally and run the integration tests
  of a project that depends on it.
- Releases use `maven-release-plugin` plus the `performRelease` profile (GPG signing) and
  `central-publishing-maven-plugin`.

## Architecture

Three cooperating pieces, all static/thread-local state shared within one test JVM:

1. **Suite lifecycle → `EventTrackerSupport`** starts and stops the single `TimelineHelper`.
   - JUnit Platform: `junit.SuiteSessionListener` (`LauncherSessionListener`, once per JVM fork).
   - TestNG: `testng.SuiteExecutionListener` (`IExecutionListener`).
   - When TestNG runs through the JUnit Platform `testng-engine`, both fire. A depth counter in `EventTrackerSupport`
     makes only the outermost start/finish pair count. TestNG callbacks also skip `RuntimeBehavior.isDryRun()` (the
     discovery phase under testng-engine).
   - On finish it writes `spring-test-timeline.json` and `spring-test-timeline.html` to the current working directory
     (the module directory under Maven or Gradle). If HTML rendering fails, a warning is logged and the test run is not
     failed.

2. **Current test class → `CurrentTestContextSupport`** keeps a thread-local stack of the test class currently running,
   so each context event can be attributed to the test class that triggered it.
   - `junit.CurrentTestExecutionListener` and `testng.CurrentTestListener` push and pop on class-level callbacks, which
     fire before `@BeforeAll`/`@BeforeClass` so the class is known while the context bootstraps. Method-level callbacks
     are a fallback for parallel execution where the class callbacks ran on another thread.
   - It is a stack so that nested classes and nested engines work.

3. **Spring context hooks**: `SpringContextEventTrackerListenerCustomizerFactory` is registered in `spring.factories` as
   a `ContextCustomizerFactory`. For each new context it:
   - assigns a sequential context id;
   - adds a `SpringContextEventTrackerListener`, whose constructor emits "initializing" and which then maps
     `ApplicationContextEvent` subtypes to `ContextEventType`. The check order matters, because Paused extends Stopped
     and Restarted extends Started;
   - registers a `SpringLifecycleTrackingBean` infrastructure bean before the application beans. Its
     `afterPropertiesSet` and `destroy` mark CREATING and DESTROYING, and because it is destroyed last, DESTROYING marks
     the moment the context is fully torn down.
   - The `ContextCustomizerImpl.equals`/`hashCode` make every instance equal. **This is required**: otherwise each test
     class would get a distinct `MergedContextConfiguration` and Spring's context cache would break.

`TimelineHelper` collects per-context events (with a worker-thread index and test class) and turns them into
`TimelineReportData`. Each event's span runs from the previous event to this one, and a synthetic `keep_alive` segment
is appended for contexts that were refreshed but never destroyed. `MetricsCollector` samples heap, CPU, threads, GC,
active-context count and (optionally) the Testcontainers container count every 250 ms on a background thread.

## HTML report

`TimelineHtmlReport` loads `src/main/resources/com/github/seregamorph/testtimeline/timeline-report.html` and replaces
the `__SPRING_TEST_TIMELINE_JSON__` placeholder inside `<script id="timelineData">` with the JSON, escaping `</`. The
template is a standalone D3 (loaded from a CDN) page that reads the JSON shape of `TimelineReportData` (`meta`,
`contexts[].events[]`, `metrics[]`). If you rename or add fields in `TimelineReportData`, update the template's JS too.
Event `type` values are the lowercase `ContextEventType` names plus `keep_alive`.
