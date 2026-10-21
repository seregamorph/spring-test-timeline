# spring-test-timeline
Spring Test and Context Timeline ⏱️

## How to use

Add Maven
```xml
<dependency>
    <groupId>com.github.seregamorph</groupId>
    <artifactId>spring-test-timeline</artifactId>
    <version>0.1-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

or Gradle dependency to your project:
```kotlin
testRuntimeOnly("com.github.seregamorph:spring-test-timeline:0.1-SNAPSHOT")
```

and then run integration tests of the project.

## Sample report
Sample [report](https://seregamorph.github.io/spring-test-timeline/spring-test-timeline.html) for 
[spring-petclinic](https://github.com/spring-projects/spring-petclinic):

<img src="docs/timeline.png" alt="Timeline" width="700"/>

## Report location

When the tests finish, `spring-test-timeline.html` is written to the build output directory: `target/` for Maven or
`build/` for Gradle. It is a single self-contained file with the report data inlined, so you can open it directly in a
browser, attach it as a CI artifact or share it.

If the build tool can't be detected (no `pom.xml` or `build.gradle[.kts]` in the working directory) and no report
directory is configured, the report is skipped and an INFO message is logged.

## Privacy

All collected data stays on your machine. The library sends nothing over the network: it only writes the HTML report to
the local file system. The one external request is made by the browser when you open the report: it loads the
[D3.js](https://d3js.org/) library from the jsDelivr CDN (`https://cdn.jsdelivr.net/npm/d3@7/dist/d3.min.js`). The report
data is not sent anywhere.

## Configuring

To write the report it to another directory, set the `springTestTimelineReportDir` system property on the test JVM. A relative
path resolves against the test JVM's working directory.

Maven (Surefire/Failsafe):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <springTestTimelineReportDir>${project.build.directory}/timeline</springTestTimelineReportDir>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

Or pass it on the command line: `./mvnw verify -DspringTestTimelineReportDir=/tmp/timeline`.

Gradle:
```kotlin
tasks.test {
    systemProperty("springTestTimelineReportDir", layout.buildDirectory.dir("timeline").get().asFile.absolutePath)
}
```
