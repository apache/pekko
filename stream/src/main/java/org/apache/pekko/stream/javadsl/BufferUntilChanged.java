/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.annotation.ApiMayChange;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.function.Function2;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Java API: Additional buffer operations for Flow and Source.
 */
public final class BufferUntilChanged {

    private BufferUntilChanged() {}

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another) into multiple
     * `List` buffers that will be emitted by the resulting Flow.
     *
     * @param <T> the element type
     * @return a Flow that buffers elements until they change
     */
    @ApiMayChange
    public static <T> Flow<T, List<T>, NotUsed> flow() {
        return Flow.<T>create()
                .statefulMapConcat(() -> {
                    List<T> buffer = new ArrayList<>();
                    return element -> {
                        if (!buffer.isEmpty() && !buffer.get(0).equals(element)) {
                            List<T> result = new ArrayList<>(buffer);
                            buffer.clear();
                            buffer.add(element);
                            return Collections.singletonList(result);
                        } else {
                            buffer.add(element);
                            return Collections.emptyList();
                        }
                    };
                })
                .concat(
                    Flow.<T>create()
                        .statefulMapConcat(() -> {
                            List<T> buffer = new ArrayList<>();
                            return element -> {
                                buffer.add(element);
                                List<T> result = buffer.isEmpty() ? Collections.emptyList() : Collections.singletonList(new ArrayList<>(buffer));
                                return result;
                            };
                        })
                        .take(1));
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another), as compared by a key
     * extracted through the user provided `keySelector` function, into multiple `List` buffers that will be emitted by the
     * resulting Flow.
     *
     * @param <T> the element type
     * @param <K> the key type
     * @param keySelector function to compute comparison key for each element
     * @return a Flow that buffers elements until they change based on the key
     */
    @ApiMayChange
    public static <T, K> Flow<T, List<T>, NotUsed> flow(Function<T, K> keySelector) {
        return Flow.<T>create()
                .statefulMapConcat(() -> {
                    List<T> buffer = new ArrayList<>();
                    Optional<K> lastKey = Optional.empty();
                    return element -> {
                        K key = keySelector.apply(element);
                        if (lastKey.isPresent() && !lastKey.get().equals(key)) {
                            List<T> result = new ArrayList<>(buffer);
                            buffer.clear();
                            buffer.add(element);
                            lastKey = Optional.of(key);
                            return Collections.singletonList(result);
                        } else {
                            buffer.add(element);
                            lastKey = Optional.of(key);
                            return Collections.emptyList();
                        }
                    };
                })
                .concat(
                    Flow.<T>create()
                        .statefulMapConcat(() -> {
                            List<T> buffer = new ArrayList<>();
                            return element -> {
                                buffer.add(element);
                                List<T> result = buffer.isEmpty() ? Collections.emptyList() : Collections.singletonList(new ArrayList<>(buffer));
                                return result;
                            };
                        })
                        .take(1));
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another), as compared by a key
     * extracted through the user provided `keySelector` function and compared using a supplied `keyComparator`, into multiple
     * `List` buffers that will be emitted by the resulting Flow.
     *
     * @param <T> the element type
     * @param <K> the key type
     * @param keySelector function to compute comparison key for each element
     * @param keyComparator predicate used to compare keys
     * @return a Flow that buffers elements until they change based on the key and comparator
     */
    @ApiMayChange
    public static <T, K> Flow<T, List<T>, NotUsed> flow(Function<T, K> keySelector, Function2<K, K, Boolean> keyComparator) {
        return Flow.<T>create()
                .statefulMapConcat(() -> {
                    List<T> buffer = new ArrayList<>();
                    Optional<K> lastKey = Optional.empty();
                    return element -> {
                        K key = keySelector.apply(element);
                        if (lastKey.isPresent() && !keyComparator.apply(lastKey.get(), key)) {
                            List<T> result = new ArrayList<>(buffer);
                            buffer.clear();
                            buffer.add(element);
                            lastKey = Optional.of(key);
                            return Collections.singletonList(result);
                        } else {
                            buffer.add(element);
                            lastKey = Optional.of(key);
                            return Collections.emptyList();
                        }
                    };
                })
                .concat(
                    Flow.<T>create()
                        .statefulMapConcat(() -> {
                            List<T> buffer = new ArrayList<>();
                            return element -> {
                                buffer.add(element);
                                List<T> result = buffer.isEmpty() ? Collections.emptyList() : Collections.singletonList(new ArrayList<>(buffer));
                                return result;
                            };
                        })
                        .take(1));
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another) into multiple
     * `List` buffers that will be emitted by the resulting Source.
     *
     * @param <T> the element type
     * @return a Source that buffers elements until they change
     */
    @ApiMayChange
    public static <T> Source<List<T>, NotUsed> source(Source<T, ?> source) {
        return source.via(flow());
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another), as compared by a key
     * extracted through the user provided `keySelector` function, into multiple `List` buffers that will be emitted by the
     * resulting Source.
     *
     * @param <T> the element type
     * @param <K> the key type
     * @param source the source to buffer
     * @param keySelector function to compute comparison key for each element
     * @return a Source that buffers elements until they change based on the key
     */
    @ApiMayChange
    public static <T, K> Source<List<T>, NotUsed> source(Source<T, ?> source, Function<T, K> keySelector) {
        return source.via(flow(keySelector));
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another), as compared by a key
     * extracted through the user provided `keySelector` function and compared using a supplied `keyComparator`, into multiple
     * `List` buffers that will be emitted by the resulting Source.
     *
     * @param <T> the element type
     * @param <K> the key type
     * @param source the source to buffer
     * @param keySelector function to compute comparison key for each element
     * @param keyComparator predicate used to compare keys
     * @return a Source that buffers elements until they change based on the key and comparator
     */
    @ApiMayChange
    public static <T, K> Source<List<T>, NotUsed> source(Source<T, ?> source, Function<T, K> keySelector, Function2<K, K, Boolean> keyComparator) {
        return source.via(flow(keySelector, keyComparator));
    }
}
