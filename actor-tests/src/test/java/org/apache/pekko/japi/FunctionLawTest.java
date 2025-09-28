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

package org.apache.pekko.japi;

import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.function.Function2;
import org.apache.pekko.japi.function.Predicate;
import org.apache.pekko.japi.function.Predicate2;
import org.junit.Assert;
import org.junit.Test;

public class FunctionLawTest {

  @Test
  public void testFunctionCompose() throws Exception {
    Function<Long, Long> f = v1 -> v1 + 1;
    Function<Long, Long> g = v1 -> v1 * 2;
    Function<Long, Long> h = f.compose(g);
    Assert.assertEquals(5L, h.apply(2L).longValue());
    Assert.assertEquals(6L, g.compose(f).apply(2L).longValue());
  }

  @Test
  public void testFunctionAndThen() throws Exception {
    Function<Long, Long> f = v1 -> v1 + 1;
    Function<Long, Long> g = v1 -> v1 * 2;
    Function<Long, Long> h = f.andThen(g);
    Assert.assertEquals(6L, h.apply(2L).longValue());
    Assert.assertEquals(5L, g.andThen(f).apply(2L).longValue());
  }

  @Test
  public void testFunction2AndThen() throws Exception {
    Function2<Long, Long, Long> f = Long::sum;
    Function<Long, Long> g = v1 -> v1 * 2;
    Function2<Long, Long, Long> h = f.andThen(g);
    Assert.assertEquals(10L, h.apply(2L, 3L).longValue());
  }

  @Test
  public void testPredicateNegate() {
    final Predicate<Object> alwaysTrue = o -> true;
    final Predicate<Object> alwaysFalse = alwaysTrue.negate();
    Assert.assertTrue(alwaysTrue.test(new Object()));
    Assert.assertFalse(alwaysFalse.test(new Object()));
    Assert.assertTrue(alwaysTrue.negate().negate().test(new Object()));
  }

  @Test
  public void testPredicate2Negate() {
    final Predicate2<Object, Object> alwaysTrue = (o1, o2) -> true;
    final Predicate2<Object, Object> alwaysFalse = alwaysTrue.negate();
    Assert.assertTrue(alwaysTrue.test(new Object(), new Object()));
    Assert.assertFalse(alwaysFalse.test(new Object(), new Object()));
    Assert.assertTrue(alwaysTrue.negate().negate().test(new Object(), new Object()));
  }
}
