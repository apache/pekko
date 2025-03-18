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
import org.apache.pekko.task.AbstractTask;
import org.apache.pekko.task.FiberDef;
import org.apache.pekko.task.FlatMapDef;
import org.apache.pekko.task.ForkDef;
import org.apache.pekko.task.GraphDef;
import org.apache.pekko.task.MapDef;
import org.apache.pekko.task.TaskDef;
import org.apache.pekko.task.ValueDef;
import org.apache.pekko.task.InterruptabilityDef;

import scala.Tuple2;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import static scala.jdk.javaapi.FutureConverters.*;

/**
 * A Task is a description of functionality (also called an "effect", or a "program") that, when
 * run, either asynchronously yields a value of type T, or fails with an exception.
 */
public class Task<T> extends AbstractTask<T> {
  /** Returns a Task that succeeds with the given value */
  public static <T> Task<T> succeed(T value) {
    return run(() -> value);
  }

  /** Returns a Task that fails with the given exception. */
  public static <T> Task<T> fail(Throwable x) {
    return new Task<>(new ValueDef<>(() -> new Failure<>(x)));
  }

  /** Returns a Task that invokes the given function when run, completing with its return value. */
  public static <T> Task<T> run(Creator<T> fn) {
    return new Task<>(new ValueDef<>(fn.andThen(t -> new Success<>(t)).toScala()));
  }

  /** Returns a Task that invokes the given function when run, completing with its returned Task. */
  public static <T> Task<T> runTask(Creator<Task<T>> fn) {
    return run(fn).flatMap(r -> r);
  }

  /** Returns a Task that invokes the given function when run, completing with Done. */
  public static Task<Done> run(Effect fn) {
    return run(
        () -> {
          fn.apply();
          return Done.getInstance();
        });
  }

  /**
   * Returns a Task that connects the given source to a KillSwitch, and then through the given sink.
   */
  public static <A, T> Task<Fiber<T>> connectCancellable(
      Source<A, ?> source, Sink<A, ? extends CompletionStage<T>> sink) {
    return connect(source.viaMat(KillSwitches.single(), Keep.right()), sink);
  }

  /** Returns a Task that runs the given cancellable source through the given sink. */
  public static <A, T> Task<Fiber<T>> connect(
      Source<A, ? extends KillSwitch> source, Sink<A, ? extends CompletionStage<T>> sink) {
    Task<FiberDef<T>> res =
        new Task<FiberDef<T>>(
            new GraphDef<T>(
                source.toMat(sink, (killswitch, cs) -> Tuple2.apply(killswitch, asScala(cs)))));

    return res.map(Fiber::new);
  }

  /**
   * Returns a Task that uses the given function to construct an uninterruptable Task, which is
   * allowed to contain interruptable blocks inside. For example: <code>
   *   Task.uninterruptableMask(makeInterruptable ->
   *     acquireResource().andThen(makeInterruptable.apply(doSomeWork)).andThen(releaseResource())
   *   )
   * </code>
   */
  public static <T> Task<T> uninterruptableMask(Function<Function<Task<T>, Task<T>>, Task<T>> fn) {
    return new Task<T>(
        new InterruptabilityDef<T>(
            false,
            restorer -> {
              Function<Task<T>, Task<T>> javaRestorer =
                  task -> new Task<T>(restorer.apply(task.definition()));
              return Task.runTask(() -> fn.apply(javaRestorer)).definition();
            }));
  }

  private final TaskDef<T> definition;

  Task(TaskDef<T> definition) {
    this.definition = definition;
  }

  /** Returns a task that maps this task's successful result through the given function */
  public <U> Task<U> map(Function<? super T, ? extends U> fn) {
    return new Task<U>(
        new MapDef<T, U>(
            definition,
            res -> {
              if (res.isFailure()) {
                return new Failure<>(res.failed().get());
              } else {
                return tryOf(() -> fn.apply(res.get()));
              }
            }));
  }

  /** Returns a task that replaces this task's successful result with the given one. */
  public <U> Task<U> as(U value) {
    return map(t -> value);
  }

  /** Returns a task that replaces this task's successful result with Done. */
  public Task<Done> asDone() {
    return as(Done.getInstance());
  }

  /**
   * Returns a task that maps this task's result through the given function, and runs the resulting
   * task after this one. On success, the function will receive a non-null first argument. On
   * failure, the function will receive a non-null second argument.
   */
  public <U> Task<U> flatMapResult(
      Function2<? super T, Throwable, ? extends AbstractTask<? extends U>> fn) {
    return new Task<U>(
        new FlatMapDef<T, U>(
            definition,
            res -> {
              T t = (res.isSuccess()) ? res.get() : null;
              Throwable x = (res.isFailure()) ? res.failed().get() : null;

              try {
                return TaskDef.narrow(fn.apply(t, x).definition());
              } catch (Exception fn_x) {
                return new ValueDef<>(() -> new Failure<>(fn_x));
              }
            }));
  }

  /**
   * Returns a task that maps this task's successful value through the given function, and runs the
   * resulting task after this one.
   */
  public <U> Task<U> flatMap(Function<? super T, ? extends AbstractTask<? extends U>> fn) {
    return new Task<U>(
        new FlatMapDef<T, U>(
            definition,
            res -> {
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

  /**
   * Returns a task that runs [that] after this task completes with success, using [combine] to
   * combine the results.
   */
  public <U, R> Task<R> zip(Task<U> that, Function2<? super T, ? super U, ? extends R> combine) {
    return flatMap(t -> that.map(u -> combine.apply(t, u)));
  }

  /**
   * Returns a task that runs [that] after this task completes with success, yielding the new task's
   * result.
   */
  public <U> Task<U> andThen(Task<U> that) {
    return flatMap(t -> that);
  }

  /**
   * Returns a task that runs [that] after this task completes with success, yielding this task's
   * result.
   */
  public Task<T> before(Task<?> that) {
    return flatMap(t -> that.as(t));
  }

  /**
   * Returns a task that forks this task into a background Fiber, which does not stop when the
   * calling fiber stops.
   */
  public Task<Fiber<T>> forkDaemon() {
    return new Task<>(new ForkDef<>(definition)).map(Fiber::new);
  }

  /**
   * Return a Resource which runs this task in a background fiber when used, automatically
   * interrupting the fiber when the resource is closed.
   */
  public Resource<Fiber<T>> forkResource() {
    return Resource.acquireRelease(forkDaemon(), fiber -> fiber.interrupt());
  }

  /**
   * Returns a task that runs this and another task in parallel, using [combine] to combine the
   * results.
   */
  public <U, R> Task<R> zipPar(
      Task<? extends U> that, Function2<? super T, ? super U, ? extends R> combine) {
    return that.forkResource()
        .use(fiber -> this.flatMap(t -> fiber.join().map(u -> combine.apply(t, u))));
  }

  /**
   * Returns a task that runs the given side effect when this task completes. On success, the
   * function will receive a non-null first argument. On failure, the function will receive a
   * non-null second argument.
   */
  public Task<T> onComplete(Function2<? super T, Throwable, ? extends AbstractTask<?>> fn) {
    return flatMapResult(
        (t, x) -> {
          if (t != null) {
            return task(fn.apply(t, null)).as(t);
          } else {
            return task(fn.apply(null, x)).flatMap(res -> fail(x));
          }
        });
  }

  /** Returns the underlying task definition. Not part of the Task API. */
  public TaskDef<T> definition() {
    return definition;
  }

  static <T> Task<T> task(AbstractTask<T> t) {
    return (t instanceof Task) ? (Task<T>) t : new Task<>(t.definition());
  }

  private static <T> Try<T> tryOf(Creator<T> fn) {
    return Try.apply(fn.toScala());
  }
}
