---
project.description: How to package an Apache Pekko application for deployment.
---
# Packaging

The simplest way to use Apache Pekko is as a regular library, adding the Pekko jars you
need to your classpath (in case of a web app, in `WEB-INF/lib`).

In many cases, such as deploying to an analytics cluster, building your application into a single 'fat jar' is needed.
When building fat jars, some additional configuration is needed to merge Pekko config files, because each Pekko jar
contains a `reference.conf` resource with default values.

The method for ensuring `reference.conf` and other `*.conf` resources are merged depends on the tooling you use to create the fat jar:

 * sbt: as an application packaged with [sbt-native-packager](https://github.com/sbt/sbt-native-packager)
 * Maven: using the Maven Shade plugin
 * Gradle: using the Jar task from the Java plugin
 
## sbt: Native Packager

[sbt-native-packager](https://github.com/sbt/sbt-native-packager) is a tool for creating
distributions of any type of application, including Pekko applications.

Define sbt version in `project/build.properties` file:

```none
sbt.version=1.13.0
```

Add [sbt-native-packager](https://github.com/sbt/sbt-native-packager) in `project/plugins.sbt` file:

```none
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.13.0")
```

Follow the instructions for the `JavaAppPackaging` in the [sbt-native-packager plugin documentation](https://sbt-native-packager.readthedocs.io/en/latest/archetypes/java_app/index.html).

## Maven: Shade plugin

You can use the [Apache Maven Shade Plugin](https://maven.apache.org/plugins/maven-shade-plugin/)
support for [Resource Transformers](https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html#AppendingTransformer)
to merge all the reference.confs on the build classpath into one.

The plugin configuration might look like this:

```xml
<plugin>
 <groupId>org.apache.maven.plugins</groupId>
 <artifactId>maven-shade-plugin</artifactId>
 <version>3.6.2</version>
 <executions>
  <execution>
   <id>shade-my-jar</id>
   <phase>package</phase>
   <goals>
    <goal>shade</goal>
   </goals>
   <configuration>
    <shadedArtifactAttached>true</shadedArtifactAttached>
    <shadedClassifierName>allinone</shadedClassifierName>
    <artifactSet>
     <includes>
      <include>*:*</include>
     </includes>
    </artifactSet>
    <transformers>
      <transformer
       implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
       <resource>reference.conf</resource>
      </transformer>
      <transformer
       implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
       <resource>version.conf</resource>
      </transformer>
      <transformer
       implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
       <manifestEntries>
        <Main-Class>myapp.Main</Main-Class>
       </manifestEntries>
      </transformer>
    </transformers>
   </configuration>
  </execution>
 </executions>
</plugin>
```


## Gradle: the Jar task from the Java plugin

When using Gradle, you would typically use the
[Jar task from the Java plugin](https://www.baeldung.com/gradle-fat-jar)
to create the fat jar.

To make sure the `reference.conf` resources are correctly merged, you might
use the [Shadow plugin](https://gradleup.com/shadow/), which might
look something like this:

```groovy
plugins {
    id 'java'
    id 'com.gradleup.shadow' version '9.6.1'
}

shadowJar {
    append 'reference.conf'
    append 'version.conf'
    // the default duplicates strategy (EXCLUDE) would drop all but the first
    // copy of these files before they reach the append transformers
    filesMatching(['reference.conf', 'version.conf']) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
```

Or when you use the Kotlin DSL:

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

tasks.shadowJar {
    append("reference.conf")
    append("version.conf")
    // the default duplicates strategy (EXCLUDE) would drop all but the first
    // copy of these files before they reach the append transformers
    filesMatching(listOf("reference.conf", "version.conf")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
```
