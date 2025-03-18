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

import org.apache.pekko.task.RuntimeDef;
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

public class TaskTest extends StreamTest{
    private final RuntimeDef runtime = new RuntimeDef(Materializer.createMaterializer(system));
    
    public TaskTest() {
        super(actorSystemResource);
    }
    
    @ClassRule
    public static PekkoJUnitActorSystemResource actorSystemResource =
        new PekkoJUnitActorSystemResource("TaskTest", PekkoSpec.testConf());

    private <T> T run(Task<T> task) throws Exception {
        return runtime.runAsync(task).get(2, TimeUnit.SECONDS);
    }

    @Test
    public void can_run_task_from_lambda() throws Exception {
        assertEquals("Hello", run(Task.run(() -> "Hello")));
    }

    @Test
    public void can_map() throws Exception {
        assertEquals(25, run(Task.run(() -> "25").map(Integer::parseInt)).intValue());
    }

    @Test
    public void can_flatMap_to_run() throws Exception {
        assertEquals(25, run(Task.run(() -> "25").flatMap(s -> Task.run(() -> Integer.parseInt(s)))).intValue());
    }

    @Test
    public void can_zipPar_two_tasks() throws Exception {
        Task<String> task = Task.run(() -> {
            return "Hello";
        });
        assertEquals("HelloHello", run(task.zipPar(task, (s1,s2) -> s1 + s2)));
    }

    @Test
    public void can_interrupt_forked_task() throws Exception {
        AtomicLong check = new AtomicLong();
        Task<Long> task = Task.run(() -> {
            Thread.sleep(100); // TODO replace with .delay() once available
        }).map(d -> check.incrementAndGet());
        run(task.forkDaemon().flatMap(fiber ->
            fiber.interrupt().map(cancelled ->
                "cancelled"
            )
        ));
        assertEquals(0, check.get());
    }

    @Test(expected=ExecutionException.class)
    public void joining_interrupted_fiber_yields_exception() throws Exception {
        Task<Long> task = Task.run(() -> Thread.sleep(100)).map(d -> 42L);
        run(task.forkDaemon().flatMap(fiber ->
            fiber.interrupt().flatMap(cancelled ->
                fiber.join()
            )
        ));
    }
    
    @Test
    public void can_run_graph() throws Exception {
        assertEquals(Optional.of("hello"),
            run(Task.connectCancellable(Source.single("hello"), Sink.headOption()).flatMap(fiber -> fiber.join())));
    }

    @Test
    public void can_interrupt_graph() throws Exception {
        AtomicLong check = new AtomicLong();
        assertEquals(Done.getInstance(), run(
            Task.connectCancellable(
                Source.tick(Duration.ofMillis(1), Duration.ofMillis(1), ""),
                Sink.foreach(s -> check.incrementAndGet())
            ).flatMap(fiber -> fiber.interrupt())
        ));
        Thread.sleep(100); 
        assertTrue(check.get() < 10);
    }

    @Test
    public void resource_is_acquired_and_released() throws Exception {
        AtomicLong check = new AtomicLong();
        Resource<Long> res = Resource.acquireRelease(Task.run(() -> check.incrementAndGet()), i -> Task.run(() -> check.decrementAndGet()));
        Task<Long> task = res.use(i -> Task.succeed(i));
        assertEquals(1L, run(task).longValue());
        assertEquals(0L, check.get());
    }

    @Test
    public void resource_is_released_on_failure() throws Exception {
        AtomicLong check = new AtomicLong();
        Resource<Long> res = Resource.acquireRelease(Task.run(() -> check.incrementAndGet()), i -> Task.run(() -> check.decrementAndGet()));
        Task<Long> task = res.use(i -> Task.fail(new RuntimeException("Simulated failure")));
        try { run(task); } catch (Exception ignored) {}
        assertEquals(0L, check.get());
    }

    @Test
    public void resource_is_released_when_interrupted() throws Exception {
        AtomicLong check = new AtomicLong();
        AtomicLong started = new AtomicLong();

        Resource<Long> res = Resource.acquireRelease(Task.run(() -> {
            return check.incrementAndGet();
        }), i -> Task.run(() -> {
            return check.decrementAndGet();
        }));

        Task<Done> task = res.use(i -> Task.run(() -> {
            started.incrementAndGet();
            Thread.sleep(100);  // TODO replace with .delay() once available
        }).flatMap(d -> Task.run(() -> {
            Thread.sleep(100);  // TODO replace with .delay() once available
        })));

        run(task.forkDaemon().flatMap(fiber -> {
            Thread.sleep(50);  // TODO replace with .delay() once available
            return fiber.interrupt();
        }));

        assertEquals(0L, check.get());
        assertEquals(1L, started.get());
    }
}
