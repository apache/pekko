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

import java.util.concurrent.CompletionStage;

import org.apache.pekko.Done;
import org.apache.pekko.japi.function.Creator;
import org.apache.pekko.japi.function.Effect;
import org.apache.pekko.japi.function.Function2;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.KillSwitch;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.task.FiberImpl;
import org.apache.pekko.task.FlatMapDef;
import org.apache.pekko.task.ForkDef;
import org.apache.pekko.task.GraphDef;
import org.apache.pekko.task.MapDef;
import org.apache.pekko.task.TaskDef;
import org.apache.pekko.task.ValueDef;

import scala.Tuple2;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import static scala.jdk.javaapi.FutureConverters.*;

public class Task<T> extends org.apache.pekko.task.Task<T> {
    public static <T> Task<T> succeed(T value) {
        return run(() -> value);
    }

    public static <T> Task<T> run(Creator<T> fn) {
        return new Task<>(new ValueDef<>(fn.andThen(t -> new Success<>(t)).toScala()));
    }

    public static Task<Done> run(Effect fn) {
        return run(() -> {
            fn.apply();
            return Done.getInstance();
        });
    }

    /** Returns a Task that connects the given source to a KillSwitch, and then through the given sink. */
    public static <A,T> Task<Fiber<T>> connectCancellable(Source<A, ?> source, Sink<A, ? extends CompletionStage<T>> sink) {
        return connect(source.viaMat(KillSwitches.single(), Keep.right()), sink);
    }

    /** Returns a Task that runs the given cancellable source through the given sink. */
    public static <A,T> Task<Fiber<T>> connect(Source<A, ? extends KillSwitch> source, Sink<A, ? extends CompletionStage<T>> sink) {
        Task<FiberImpl<T>> res = new Task<FiberImpl<T>>(new GraphDef<T>(source.toMat(sink, (killswitch, cs) -> Tuple2.apply(killswitch, asScala(cs)))));

        return res.map(Fiber::new);
    }

    private final TaskDef<T> definition;

    Task(TaskDef<T> definition) {
        this.definition = definition;
    }

    public TaskDef<T> definition() {
        return definition;
    }

    /** Returns a task that maps this task's value through the given function */
    public <U> Task<U> map(Function<? super T, ? extends U> fn) {
        return new Task<U>(new MapDef<T,U>(definition, res -> {
            if (res.isFailure()) {
                return new Failure<>(res.failed().get());
            } else {
                return tryOf(() -> fn.apply(res.get()));
            }
        }));
    }

    public <U> Task<U> as(U value) {
        return map(t -> value);
    }

    public Task<Done> asDone() {
        return as(Done.getInstance());
    }

    /** Returns a task that maps this task's value through the given function, and runs the resulting task after this one. */
    public <U> Task<U> flatMap(Function<? super T, Task<? extends U>> fn) {
        return new Task<U>(new FlatMapDef<T,U>(definition, res -> {
            if (res.isFailure()) {
                return new ValueDef<>(() -> new Failure<>(res.failed().get()));
            } else {
                try {
                    return TaskDef.narrow(fn.apply(res.get()).definition());
                } catch (Exception x) {
                    return new ValueDef<>(() -> new Failure<>(x));
                }
            }
        }));
    }

    public Task<Fiber<T>> fork() {
        return new Task<>(new ForkDef<>(definition)).map(Fiber::new);
    }

    public <U,R> Task<R> zipPar(Task<? extends U> that, Function2<? super T, ? super U, ? extends R> combine) {
        return that.fork().flatMap(fiber ->
            this.flatMap(t ->
                fiber.join().map(u ->
                    combine.apply(t,u)
                )
            )
        );
    }

    private static <T> Try<T> tryOf(Creator<T> fn) {
        return Try.apply(fn.toScala());
    }
}
