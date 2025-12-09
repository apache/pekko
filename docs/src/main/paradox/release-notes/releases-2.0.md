# Release Notes (2.0.x)

Apache Pekko 2.0.x releases support Java 17 and above.

## 2.0.0-M1

This is milestone release and is aimed at testing this new major version
by early adopters. This is experimental. This release should not be used in production.

### Main changes

* Java 17 is the new minimum
* Scala 2.12 support dropped
* A lot of deprecated code removed
* A lot of pekko.util classes for Scala version compatibility have been removed
* Big change for all Java DSL users due to the removal of `pekko.japi.Function` (and related classes) to use `pekko.japi.function.Function` instead (lambdas should recompile ok but if you declared variables or functions explicitly, then you may need to change your imports)
* New pekko-serialization-jackson3. Users who are happy with the pekko-serialization-jackson, which uses Jackson 2, can stick with that
* Changed the pekko-serialization-jackson lz4-java dependency to `at.yawk.lz4:lz4-java`, a fork that has important bugfixes
