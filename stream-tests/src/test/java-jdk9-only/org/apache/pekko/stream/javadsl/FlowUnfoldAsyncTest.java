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

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FlowUnfoldAsyncTest extends StreamTest {
    @ClassRule
    public static PekkoJUnitActorSystemResource actorSystemResource =
        new PekkoJUnitActorSystemResource("SourceTest", PekkoSpec.testConf());

    public FlowUnfoldAsyncTest() {
        super(actorSystemResource);
    }

    @Test
    public void testFoldAsync() throws Exception {
        final Integer result = Source.unfoldAsync(
                0,
                idx -> {
                    if (idx >= 10) {
                        return CompletableFuture.completedStage(Optional.empty());
                    } else {
                        return CompletableFuture.completedStage(Optional.of(Pair.create(idx + 1, idx)));
                    }
                })
            .runFold(0, Integer::sum, system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);
        Assert.assertEquals(45, result.intValue());
    }
}
