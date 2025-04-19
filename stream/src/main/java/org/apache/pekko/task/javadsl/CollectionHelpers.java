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

import java.util.ArrayList;
import java.util.function.Function;

/**
 * Functional helpers for collections. Prefixed with "c" so they don't conflict with local methods
 * of the same name (in Java 8's limited name resolution)
 */
public class CollectionHelpers {
  public static <T, U> ArrayList<U> cmap(
      Iterable<? extends T> src, Function<? super T, ? extends U> fn) {
    ArrayList<U> list = new ArrayList<>();
    for (T t : src) {
      list.add(fn.apply(t));
    }
    return list;
  }
}
