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

package org.apache.pekko.dispatch;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.*;

public class CompletionStagesTests {
    private final Duration timeout = Duration.create(5, TimeUnit.SECONDS);

    @Test
    @SuppressWarnings("deprecation")
    public void testAsScala() throws Exception {
        final CompletionStage<Integer> successCs = CompletableFuture.completedFuture(42);
        Future<Integer> scalaFuture = CompletionStages.asScala(successCs);
        Assert.assertEquals(42, Await.result(scalaFuture, timeout).intValue());
        //failed
        Assert.assertThrows("Simulated failure", RuntimeException.class, () -> {
            final CompletionStage<Integer> failedCs = Futures.failedCompletionStage(new RuntimeException(
                "Simulated failure"));
            Await.result(CompletionStages.asScala(failedCs), timeout);
        });
    }

    @Test
    public void testFind() throws Exception {
        final List<CompletionStage<Integer>> stages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stages.add(CompletableFuture.completedFuture(i));
        }
        final CompletionStage<Optional<Integer>> found = CompletionStages.find(stages, i -> i == 3);
        Assert.assertEquals(Optional.of(3), found.toCompletableFuture().get(3, TimeUnit.SECONDS));
        final CompletionStage<Optional<Integer>> notFound = CompletionStages.find(stages, i -> i == 42);
        Assert.assertEquals(Optional.empty(), notFound.toCompletableFuture().get(3, TimeUnit.SECONDS));
    }

    @Test
    public void testFirstCompletedOf() throws Exception {
        final List<CompletionStage<Integer>> stages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stages.add(new CompletableFuture<>());
        }
        ForkJoinPool.commonPool().submit(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                //ignore
            }
            ((CompletableFuture<Integer>) stages.get(3)).complete(42);
        });
        final CompletionStage<Integer> first = CompletionStages.firstCompletedOf(stages);
        Assert.assertEquals(42, first.toCompletableFuture().get(3, TimeUnit.SECONDS).intValue());
    }

    @Test
    public void testFold() throws Exception {
        final List<CompletionStage<Integer>> stages = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            stages.add(CompletableFuture.completedFuture(i));
        }
        final CompletionStage<Integer> folded = CompletionStages.fold(0, stages, Integer::sum);
        Assert.assertEquals(15, folded.toCompletableFuture().get(3, TimeUnit.SECONDS).intValue());
    }

    @Test
    public void testReduce() throws Exception {
        final List<CompletionStage<Integer>> stages = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            stages.add(CompletableFuture.completedFuture(i));
        }
        final CompletionStage<Integer> reduced = CompletionStages.reduce(stages, Integer::sum);
        Assert.assertEquals(15, reduced.toCompletableFuture().get(3, TimeUnit.SECONDS).intValue());
        //reduce empty list
        final List<CompletionStage<Integer>> empty = new ArrayList<>();
        final CompletionStage<Integer> reducedEmpty = CompletionStages.reduce(empty, Integer::sum);
        try {
            reducedEmpty.toCompletableFuture().get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof NoSuchElementException);
            Assert.assertEquals("reduce of an empty iterable of CompletionStages", e.getCause().getMessage());
        }
    }

    @Test
    public void testSequence() throws Exception {
        testSequence0(null);
        testSequence0(ForkJoinPool.commonPool());
    }

    private void testSequence0(final Executor executor) throws Exception {
        final List<CompletionStage<Integer>> stages = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            stages.add(CompletableFuture.completedFuture(i));
        }
        final CompletionStage<List<Integer>> sequenced = CompletionStages.sequence(stages, executor);
        Assert.assertEquals(Lists.newArrayList(1, 2, 3, 4, 5),
            sequenced.toCompletableFuture().get(3, TimeUnit.SECONDS));
        //sequence empty list
        final List<CompletionStage<Integer>> empty = new ArrayList<>();
        final CompletionStage<List<Integer>> sequencedEmpty = CompletionStages.sequence(empty, executor);
        Assert.assertEquals(Collections.emptyList(), sequencedEmpty.toCompletableFuture().get(3, TimeUnit.SECONDS));
    }

    @Test
    public void testTraverse() throws Exception {
        testTraverse0(null);
        testTraverse0(ForkJoinPool.commonPool());
    }

    private void testTraverse0(final Executor executor) throws Exception {
        final List<Integer> values = Arrays.asList(1, 2, 3, 4, 5);
        final CompletionStage<List<Integer>> traversed = CompletionStages.traverse(values,
            CompletableFuture::completedFuture, executor);
        Assert.assertEquals(Lists.newArrayList(1, 2, 3, 4, 5),
            traversed.toCompletableFuture().get(3, TimeUnit.SECONDS));
        //traverse empty list
        final List<Integer> empty = Collections.emptyList();
        final CompletionStage<List<Integer>> traversedEmpty = CompletionStages.traverse(empty,
            CompletableFuture::completedFuture, executor);
        Assert.assertEquals(Collections.emptyList(), traversedEmpty.toCompletableFuture().get(3, TimeUnit.SECONDS));
    }


}
