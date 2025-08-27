/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.task.javadsl;

import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.Done;

import org.junit.ClassRule;
import org.junit.Test;

import org.apache.pekko.japi.function.Creator;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import java.util.Optional;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TaskTest extends StreamTest {
  private final Runtime runtime = Runtime.create(Materializer.createMaterializer(system));

  public TaskTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("TaskTest", PekkoSpec.testConf());

  private <T> T run(Task<T> task) throws Throwable {
    return runtime.run(task.timeout(Duration.ofSeconds(2)));
  }

  @Test
  public void can_run_task_from_lambda() throws Throwable {
    assertEquals("Hello", run(Task.run(() -> "Hello")));
  }

  @Test
  public void can_map() throws Throwable {
    assertEquals(25, run(Task.run(() -> "25").map(Integer::parseInt)).intValue());
  }

  @Test
  public void can_flatMap_to_run() throws Throwable {
    assertEquals(
        25, run(Task.run(() -> "25").flatMap(s -> Task.run(() -> Integer.parseInt(s)))).intValue());
  }

  @Test
  public void can_zipPar_two_tasks() throws Throwable {
    Task<String> task =
        Task.run(
            () -> {
              return "Hello";
            });
    assertEquals("HelloHello", run(task.zipPar(task, (s1, s2) -> s1 + s2)));
  }

  @Test
  public void zipPar_interrupts_first_on_error_in_second() throws Throwable {
    AtomicLong check = new AtomicLong();
    Task<String> task1 =
        Task.succeed("A").delayed(Duration.ofMillis(100)).before(Task.run(check::incrementAndGet));
    Task<String> task2 = Task.fail(new RuntimeException("simulated failure"));
    org.junit.Assert.assertThrows(
        RuntimeException.class, () -> run(task1.zipPar(task2, (a, b) -> a + b)));
    assertEquals(0, check.get());
  }

  @Test
  public void zipPar_interrupts_second_on_error_in_first() throws Throwable {
    AtomicLong check = new AtomicLong();
    Task<String> task1 =
        Task.succeed("A").delayed(Duration.ofMillis(100)).before(Task.run(check::incrementAndGet));
    Task<String> task2 = Task.fail(new RuntimeException("simulated failure"));
    org.junit.Assert.assertThrows(
        RuntimeException.class, () -> run(task2.zipPar(task1, (a, b) -> a + b)));
    assertEquals(0, check.get());
  }

  @Test
  public void can_interrupt_forked_task() throws Throwable {
    AtomicLong check = new AtomicLong();
    Task<Long> task = Task.run(() -> check.incrementAndGet()).delayed(Duration.ofMillis(100));
    run(task.forkDaemon().flatMap(fiber -> fiber.interrupt().map(cancelled -> "cancelled")));
    assertEquals(0, check.get());
  }

  @Test(expected = InterruptedException.class)
  public void joining_interrupted_fiber_yields_exception() throws Throwable {
    Task<Long> task = Task.succeed(42L).delayed(Duration.ofMillis(100));
    run(task.forkDaemon().flatMap(fiber -> fiber.interrupt().flatMap(cancelled -> fiber.join())));
  }

  @Test
  public void can_run_graph() throws Throwable {
    assertEquals(
        Optional.of("hello"), run(Task.connect(Source.single("hello"), Sink.headOption())));
  }

  @Test
  public void can_interrupt_graph() throws Throwable {
    AtomicLong check = new AtomicLong();
    assertEquals(
        Done.getInstance(),
        run(
            Task.connect(
                    Source.tick(Duration.ofMillis(1), Duration.ofMillis(1), ""),
                    Sink.foreach(s -> check.incrementAndGet()))
                .forkDaemon()
                .flatMap(fiber -> fiber.interrupt())));
    Thread.sleep(100);
    assertTrue(check.get() < 10);
  }

  @Test
  public void resource_is_acquired_and_released() throws Throwable {
    AtomicLong check = new AtomicLong();
    Resource<Long> res =
        Resource.acquireRelease(
            Task.run(() -> check.incrementAndGet()), i -> Task.run(() -> check.decrementAndGet()));
    Task<Long> task = res.use(i -> Task.succeed(i));
    assertEquals(1L, run(task).longValue());
    assertEquals(0L, check.get());
  }

  @Test
  public void resource_is_released_on_failure() throws Throwable {
    AtomicLong check = new AtomicLong();
    Resource<Long> res =
        Resource.acquireRelease(
            Task.run(() -> check.incrementAndGet()), i -> Task.run(() -> check.decrementAndGet()));
    Task<Long> task = res.use(i -> Task.fail(new RuntimeException("Simulated failure")));
    try {
      run(task);
    } catch (Exception ignored) {
    }
    assertEquals(0L, check.get());
  }

  @Test
  public void resource_closes_AutoCloseable() throws Throwable {
    AtomicLong created = new AtomicLong();
    AtomicLong closed = new AtomicLong();
    Resource<AutoCloseable> res =
        Resource.autoCloseable(
            Task.run(
                () -> {
                  created.incrementAndGet();
                  return () -> closed.incrementAndGet();
                }));
    run(res.use(ac -> Task.done));
    assertEquals(1L, created.get());
    assertEquals(1L, closed.get());
  }

  @Test
  public void resource_is_released_when_interrupted() throws Throwable {
    AtomicLong check = new AtomicLong();
    AtomicLong started = new AtomicLong();

    Resource<Long> res =
        Resource.acquireRelease(
            Task.run(
                () -> {
                  return check.incrementAndGet();
                }),
            i ->
                Task.run(
                    () -> {
                      return check.decrementAndGet();
                    }));

    Task<Long> task =
        res.use(
            i ->
                Task.run(() -> started.incrementAndGet())
                    .before(Clock.sleep(Duration.ofMillis(100))));
    run(task.forkDaemon().flatMap(fiber -> fiber.interrupt().delayed(Duration.ofMillis(50))));

    assertEquals(0L, check.get());
    assertEquals(1L, started.get());
  }

  @Test
  public void resource_can_fork() throws Throwable {
    AtomicLong check = new AtomicLong();
    Resource<Long> res =
        Resource.acquireRelease(Task.run(() -> check.incrementAndGet()), i -> Task.done);
    Task<Long> task = res.fork().use(fiber -> fiber.join());
    run(task);
    assertEquals(1L, check.get());
  }

  @Test
  public void resource_is_released_when_fork_is_interrupted() throws Throwable {
    AtomicLong check = new AtomicLong();
    Resource<Long> res =
        Resource.acquireRelease(
            Task.run(() -> check.incrementAndGet()), i -> Task.run(() -> check.decrementAndGet()));
    Task<Done> task = res.fork().use(fiber -> fiber.interrupt());
    run(task);
    assertEquals(0L, check.get());
  }

  @Test
  public void resource_is_released_when_fork_is_completed() throws Throwable {
    AtomicLong check = new AtomicLong();
    Resource<Long> res =
        Resource.acquireRelease(
            Task.run(() -> check.incrementAndGet()), i -> Task.run(() -> check.decrementAndGet()));
    Task<Long> task = res.fork().use(fiber -> fiber.join());
    run(task);
    assertEquals(0L, check.get());
  }

  @Test
  public void can_create_and_complete_promise() throws Throwable {
    Task<Integer> task =
        Promise.<Integer>make()
            .flatMap(
                promise ->
                    promise
                        .await()
                        .forkDaemon()
                        .flatMap(fiber -> promise.succeed(42).andThen(fiber.join())));
    assertEquals(42, run(task).intValue());
  }

  @Test
  public void can_race_two_tasks() throws Throwable {
    Task<Integer> task1 = Task.succeed(0).delayed(Duration.ofMillis(100));
    Task<Integer> task2 = Task.succeed(42);
    Task<Integer> task = Task.raceAll(task1, task2);
    assertEquals(42, run(task).intValue());
  }

  @Test
  public void can_race_task_with_never() throws Throwable {
    Task<Integer> task1 = Task.succeed(42).delayed(Duration.ofMillis(100));
    Task<Integer> task2 = Task.never();
    Task<Integer> task = Task.raceAll(task1, task2);
    assertEquals(42, run(task).intValue());
  }
}
