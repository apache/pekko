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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import org.apache.pekko.Done;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.function.BiProcedure;
import org.apache.pekko.japi.function.Creator;
import org.apache.pekko.japi.function.Effect;
import org.apache.pekko.japi.function.Function2;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.japi.function.Predicate;
import org.apache.pekko.stream.ActorAttributes.Dispatcher;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.Graph;
import org.apache.pekko.stream.KillSwitch;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.task.AbstractRuntime;
import org.apache.pekko.task.AbstractTask;
import org.apache.pekko.task.CallbackDef;
import org.apache.pekko.task.FiberDef;
import org.apache.pekko.task.FlatMapDef;
import org.apache.pekko.task.ForkDef;
import org.apache.pekko.task.InterruptabilityDef;
import org.apache.pekko.task.MapDef;
import org.apache.pekko.task.RunningGraph;
import org.apache.pekko.task.TaskDef;
import org.apache.pekko.task.ValueDef;

import scala.Tuple2;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import static org.apache.pekko.task.javadsl.CollectionHelpers.*;

import static scala.jdk.javaapi.FutureConverters.*;
import static scala.jdk.javaapi.CollectionConverters.*;

/**
 * A Task is a description of functionality (also called an "effect", or a "program") that, when
 * run, either asynchronously yields a value of type T, or fails with an exception.
 */
public class Task<T> implements AbstractTask<T> {
  public static final Task<Done> done = succeed(Done.getInstance());

  /** Returns a task that runs all the given task in sequence, returning all results. */
  @SafeVarargs
  public static <T> Task<List<T>> all(AbstractTask<T>... tasks) {
    return all(Arrays.asList(tasks));
  }

  /** Returns a task that runs all the given task in sequence, returning all results. */
  public static <T> Task<List<T>> all(Iterable<? extends AbstractTask<T>> tasks) {
    return new Task<>(TaskDef.all(asScala(cmap(tasks, t -> t.definition()))))
        .map(seq -> asJava(seq));
  }

  /**
   * Returns a task that runs all the given task in parallel, returning all results. If any task
   * fails, the rest is interrupted.
   */
  @SafeVarargs
  public static <T> Task<List<T>> allPar(AbstractTask<T>... tasks) {
    return allPar(Arrays.asList(tasks));
  }

  /**
   * Returns a task that runs all the given task in parallel, returning all results. If any task
   * fails, the rest is interrupted.
   */
  public static <T> Task<List<T>> allPar(Iterable<? extends AbstractTask<T>> tasks) {
    return new Task<>(TaskDef.allPar(asScala(cmap(tasks, t -> t.definition()))))
        .map(seq -> asJava(seq));
  }

  /**
   * Creates a Task that can integrate external code that can report results through a callback. The
   * launch function is given a callback to invoke, and should return a task that can handle
   * interruption of the running process.
   *
   * <p>For example: <code>
   * Task.callback(cb -> {
   * myExternalProcess.onSuccess(t -> { cb.accept(t, null); });
   * myExternalProcess.onFailure(x -> { cb.accept(null, x); });
   * return Task.run(() -> myExternalProcess.cancel());
   * })
   * </code>
   */
  public static <T> Task<T> callback(Function<BiConsumer<T, Throwable>, Task<Done>> launch) {
    return new Task<>(
        new CallbackDef<>(
            callback ->
                unchecked(() -> launch.apply((t, x) -> callback.apply(tryOf(t, x))).definition())));
  }

  /** Returns a task that completes the given CompletionStage, cancelling it on interruption. */
  public static <T> Task<T> complete(CompletionStage<T> stage) {
    return complete(() -> stage);
  }

  /** Returns a task that creates and completes a CompletionStage, cancelling it on interruption. */
  public static <T> Task<T> complete(Creator<? extends CompletionStage<T>> stage) {
    return complete(stage, fut -> fut.cancel(false));
  }

  /**
   * Returns a task that completes the given CompletionStage, leaving it running on interruption.
   */
  public static <T> Task<T> completeUninterruptable(CompletionStage<T> stage) {
    return completeUninterruptable(() -> stage);
  }

  /**
   * Returns a task that creates and completes a CompletionStage, leaving it running on
   * interruption.
   */
  public static <T> Task<T> completeUninterruptable(Creator<? extends CompletionStage<T>> stage) {
    return complete(stage, fut -> {});
  }

  /**
   * Returns a Task that connects the given source to a KillSwitch, and then through the given sink,
   * shutting down the kill switch on interrupt.
   */
  public static <A, T> Task<T> connect(
      Source<A, ?> source, Sink<A, ? extends CompletionStage<T>> sink) {
    return run(source.viaMat(KillSwitches.single(), Keep.right()), sink);
  }

  /** Returns a Task that fails with the given exception. */
  public static <T> Task<T> fail(Throwable x) {
    return new Task<>(TaskDef.fail(() -> x));
  }

  /** Returns a Task that fails with the given exception. */
  public static <T> Task<T> fail(Creator<Throwable> x) {
    return new Task<>(TaskDef.fail(x.toScala()));
  }

  /** Returns a Task that never completes. */
  public static <T> Task<T> never() {
    return new Task<>(TaskDef.never());
  }

  /**
   * Returns a Task which executes all given tasks in parallel, returning whichever of them
   * completes first, and the interrupts the rest.
   */
  public static <T> Task<T> raceAll(Iterable<? extends AbstractTask<T>> tasks) {
    return new Task<>(TaskDef.raceAll(asScala(cmap(tasks, t -> t.definition()))));
  }

  /**
   * Returns a Task which executes all given tasks in parallel, returning whichever of them
   * completes first, and the interrupts the rest.
   */
  @SafeVarargs
  public static <T> Task<T> raceAll(AbstractTask<T>... tasks) {
    return raceAll(Arrays.asList(tasks));
  }

  /**
   * Returns a Task that invokes the given function when run, completing with its return value. The
   * function is executed on the default dispatcher.
   */
  public static <T> Task<T> run(Creator<T> fn) {
    return new Task<>(TaskDef.succeed(fn.toScala()));
  }

  /** Returns a Task that invokes the given function when run, completing with Done. */
  public static Task<Done> run(Effect fn) {
    return new Task<>(TaskDef.succeed(fn.toScala())).asDone();
  }

  /**
   * Returns a Task that runs the given source through the given sink, shutting down the kill switch
   * on interrupt.
   */
  public static <A, T> Task<T> run(
      Source<A, ? extends KillSwitch> source, Sink<A, ? extends CompletionStage<T>> sink) {
    return run(source.toMat(sink, RunningGraphs::withShutdown));
  }

  /** Returns the given runnable graph, which must materialize into an instance of RunningGraph. */
  public static <T> Task<T> run(Graph<ClosedShape, RunningGraph<T>> graph) {
    return new Task<T>(TaskDef.run(graph));
  }

  /**
   * Returns a Task that invokes the given function when run, completing with its return value. The
   * function is executed on the given dispatcher. For example, provide
   * org.apache.pekko.dispatch.Dispatchers.DefaultBlockingDispatcherId to run this task on pekko's
   * blocking I/O thread pool.
   */
  public static <T> Task<T> runOn(String dispatcher, Creator<T> fn) {
    return new Task<>(TaskDef.succeedOn(dispatcher, fn.toScala()));
  }

  /** Returns a Task that invokes the given function when run, completing with Done. */
  public static Task<Done> runOn(String dispatcher, Effect fn) {
    return new Task<>(TaskDef.succeedOn(dispatcher, fn.toScala())).asDone();
  }

  /** Returns a Task that invokes the given function when run, completing with its returned Task. */
  public static <T> Task<T> runTask(Creator<Task<T>> fn) {
    return run(fn).flatMap(r -> r);
  }

  /** Returns a Task that invokes the given function when run, completing with its returned Task. */
  public static <T> Task<T> runTaskOn(String dispatcher, Creator<Task<T>> fn) {
    return runOn(dispatcher, fn).flatMap(r -> r);
  }

  /** Returns a Task that succeeds with the given value */
  public static <T> Task<T> succeed(T value) {
    return new Task<>(TaskDef.succeed(() -> value));
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
        TaskDef.uninterruptableMask(
            restorer -> {
              Function<Task<T>, Task<T>> javaRestorer =
                  task -> new Task<T>(restorer.apply(task.definition()));
              return Task.runTask(() -> fn.apply(javaRestorer)).definition();
            }));
  }

  // ================ Instance methods ====================

  /** Returns a task that replaces this task's successful result with the given one. */
  public <U> Task<U> as(U value) {
    return map(t -> value);
  }

  /** Returns a task that replaces this task's successful result with Done. */
  public Task<Done> asDone() {
    return as(Done.getInstance());
  }

  /**
   * Returns a task that runs [that] after this task completes with success, yielding the new task's
   * result.
   */
  public <U> Task<U> andThen(AbstractTask<U> that) {
    return flatMap(t -> that);
  }

  /**
   * Returns a task that runs [that] after this task completes with success, yielding this task's
   * result.
   */
  public Task<T> before(AbstractTask<?> that) {
    return flatMap(t -> task(that).as(t));
  }

  /** Returns a task that recovers from the matching exceptions using the given handler. */
  public <X extends Throwable> Task<T> catchSome(Class<X> exceptionType, Function<X, T> handler) {
    return catchSomeWith(exceptionType, x -> true, x -> succeed(handler.apply(x)));
  }

  /** Returns a task that recovers from the matching exceptions using the given handler. */
  @SuppressWarnings("unchecked")
  public <X extends Throwable> Task<T> catchSomeWith(
      Class<X> exceptionType, Function<X, AbstractTask<T>> handler) {
    return catchSomeWith(exceptionType, x -> true, handler);
  }

  /** Returns a task that recovers from the matching exceptions using the given handler. */
  @SuppressWarnings("unchecked")
  public <X extends Throwable> Task<T> catchSomeWith(
      Class<X> exceptionType, Predicate<X> test, Function<X, AbstractTask<T>> handler) {
    return flatMapResult(
        (t, x) -> {
          if (x != null) {
            return (exceptionType.isInstance(x) && test.test((X) x)
                ? handler.apply((X) x)
                : fail(x));
          } else {
            return succeed(t);
          }
        });
  }

  /** Returns a Task that executes this task after the given duration. */
  public Task<T> delayed(Duration duration) {
    return Clock.sleep(duration).andThen(this);
  }

  /**
   * Returns a task that maps this task's successful value through the given function, and runs the
   * resulting task after this one.
   */
  public <U> Task<U> flatMap(Function<? super T, ? extends AbstractTask<U>> fn) {
    return new Task<U>(definition.flatMap(t -> unchecked(() -> fn.apply(t).definition())));
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
   * Returns a task that maps this task's result through the given function, and runs the resulting
   * task after this one. On success, the function will receive a non-null first argument. On
   * failure, the function will receive a non-null second argument.
   */
  public <U> Task<U> flatMapResult(Function2<? super T, Throwable, ? extends AbstractTask<U>> fn) {
    return new Task<U>(
        definition.flatMapResult(res -> unchecked(() -> applyTry(res, fn).definition())));
  }

  /** Returns a task that maps this task's successful result through the given function */
  public <U> Task<U> map(Function<? super T, ? extends U> fn) {
    return new Task<U>(definition.map(t -> unchecked(() -> fn.apply(t))));
  }

  /**
   * Returns a task that runs the given side effect when this task completes. On success, the
   * function will receive a non-null first argument. On failure, the function will receive a
   * non-null second argument.
   */
  public Task<T> onComplete(Function2<? super T, Throwable, ? extends AbstractTask<?>> fn) {
    return new Task<T>(definition.onComplete(res -> applyTry(res, fn).definition()));
  }

  /**
   * Runs this task and [that] in parallel, returning whichever completes first, interrupting the
   * other.
   */
  public Task<T> race(AbstractTask<T> that) {
    return Task.<T>raceAll(this, that);
  }

  /**
   * Returns a Task that after the given duration, automatically interrupts itself and fails with
   * java.util.concurrent.TimeoutException.
   */
  public Task<T> timeout(Duration duration) {
    return race(
        Task.<T>fail(() -> new TimeoutException("Task timed out after " + duration))
            .delayed(duration));
  }

  /** Returns this task as a resource, without a cleanup action. */
  public Resource<T> toResource() {
    return Resource.succeedTask(this);
  }

  /**
   * Returns a task that runs [that] after this task completes with success, using [combine] to
   * combine the results.
   */
  public <U, R> Task<R> zip(
      AbstractTask<U> that, Function2<? super T, ? super U, ? extends R> combine) {
    return flatMap(t -> task(that).map(u -> combine.apply(t, u)));
  }

  /**
   * Returns a task that runs this and another task in parallel, using [combine] to combine the
   * results.
   */
  public <U, R> Task<R> zipPar(
      AbstractTask<? extends U> that, Function2<? super T, ? super U, ? extends R> combine) {
    return new Task<>(
        definition
            .zipPar(that.definition())
            .map(t -> unchecked(() -> combine.apply(t._1(), t._2()))));
  }

  /** Returns the underlying task definition. Not part of the Task API. */
  public TaskDef<T> definition() {
    return definition;
  }

  private final TaskDef<T> definition;

  Task(TaskDef<T> definition) {
    this.definition = definition;
  }

  /**
   * Returns a Task that when ran, creates the given completion stage, completing the task with its
   * result, and invoking onCancel when the task's fiber is cancelled.
   */
  private static <T> Task<T> complete(
      Creator<? extends CompletionStage<T>> stage, Procedure<CompletableFuture<T>> onCancel) {
    return callback(
        cb -> {
          CompletableFuture<T> fut = stage.create().toCompletableFuture();
          fut.whenComplete(
              (t, x) -> {
                cb.accept(t, x);
              });
          return Task.run(() -> onCancel.apply(fut));
        });
  }

  static <T> Task<T> task(AbstractTask<T> t) {
    return (t instanceof Task) ? (Task<T>) t : new Task<>(t.definition());
  }

  private static <T> Try<T> tryOf(Creator<T> fn) {
    return Try.apply(fn.toScala());
  }

  private static <T> Try<T> tryOf(T success, Throwable failure) {
    return (success != null) ? new Success<>(success) : new Failure<>(failure);
  }

  private static <T> T unchecked(Creator<T> fn) {
    try {
      return fn.create();
    } catch (Throwable t) {
      return sneakyThrow(t);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }

  private static <T, U> U applyTry(Try<T> res, Function2<? super T, Throwable, U> fn) {
    T t = (res.isSuccess()) ? res.get() : null;
    Throwable x = (res.isFailure()) ? res.failed().get() : null;

    return unchecked(() -> fn.apply(t, x));
  }
}
