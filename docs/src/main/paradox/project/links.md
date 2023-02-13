# Project

## Source Code

Pekko uses Git and is hosted at [Github apache/incubator-pekko](https://github.com/apache/incubator-pekko).

## Releases Repository

All Pekko releases are published via Sonatype to Maven Central, see
[search.maven.org](https://search.maven.org/search?q=g:org.apache.pekko)

## Snapshots Repository

Snapshot builds are available at [https://repository.apache.org/content/groups/snapshots/org/apache/pekko/](https://repository.apache.org/content/groups/snapshots/org/apache/pekko/). All Pekko modules that belong to the same build have the same version.

@@@ warning

The use of Pekko SNAPSHOTs, nightlies and milestone releases is discouraged unless you know what you are doing.

@@@

### sbt definition of snapshot repository

Make sure that you add the repository to the sbt resolvers:

```
resolvers ++= "Apache Pekko Snapshots" at "https://repository.apache.org/content/groups/snapshots"
```

Define the library dependencies with the complete version. For example:

@@@vars
```
libraryDependencies += "org.apache.pekko" % "pekko-remote_$scala.binary.version$" % "0.0.0+26565-a07a08f6-SNAPSHOT"
```
@@@

### Maven definition of snapshot repository

Make sure that you add the repository to the Maven repositories in pom.xml:

```
<repositories>
  <repository>
    <id>apache-pekko-snapshots</id>
    <url>https://repository.apache.org/content/groups/snapshots</url>
    <layout>default</layout>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>
```

Define the library dependencies with the timestamp as version. For example:

@@@vars
```
<dependencies>
  <dependency>
    <groupId>org.apache.pekko</groupId>
    <artifactId>pekko-remote_$scala.binary.version$</artifactId>
    <version>0.0.0+26565-a07a08f6-SNAPSHOT</version>
  </dependency>
</dependencies>
```
@@@
