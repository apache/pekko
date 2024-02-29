# Apache Pekko Microbenchmarks

This subproject contains some microbenchmarks excercising key parts of Apache Pekko. (Excluding typed which has its 
own jmh module)


Pekko uses [sbt-jmh](https://github.com/sbt/sbt-jmh) to integrate [Java Microbenchmark Harness](https://github.com/openjdk/jmh). You can run them like:

```shell
sbt shell
pekko > project bench-jmh
sbt:pekko-bench-jmh> Jmh/run -i 3 -wi 3 -f 1 .*ActorCreationBenchmark
```

or execute in one-line command

```shell
sbt bench-jmh/Jmh/run -i 3 -wi 3 -f 1 .*ActorCreationBenchmark
```
   

Use 'Jmh/run -h' to get an overview of the available options.

Some potentially out of date resources for writing JMH benchmarks:

* [Studying what's wrong with JMH benchmarks](https://www.researchgate.net/publication/333825812_What's_Wrong_With_My_Benchmark_Results_Studying_Bad_Practices_in_JMH_Benchmarks)
* [Writing good benchmarks](http://tutorials.jenkov.com/java-performance/jmh.html#writing-good-benchmarks)
