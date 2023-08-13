/*
 * No License Header (original 3rd party file has no license header, see below)
 */

package org.apache.pekko.stream

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.{ NoStackTrace, NonFatal }
import scala.util.{ Failure, Success, Try }
import org.apache.pekko
import pekko.dispatch.ExecutionContexts
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.Attributes.{ Name, SourceLocation }
import pekko.stream.scaladsl.{ Flow, FlowWithContext, Source, SourceWithContext }
import pekko.stream.stage.{ AsyncCallback, GraphStage, GraphStageLogic, InHandler, OutHandler }

import scala.annotation.nowarn

// derived from https://github.com/jaceksokol/akka-stream-map-async-partition/tree/45bdbb97cf82bf22d5decd26df18702ae81a99f9/src/main/scala/com/github/jaceksokol/akka/stream
// licensed under an MIT License
private[stream] object MapAsyncPartition {

  val DefaultBufferSize = 10

  private def extractPartitionWithCtx[In, Ctx, Partition](extract: In => Partition)(tuple: (In, Ctx)): Partition =
    extract(tuple._1)

  private def fWithCtx[In, Out, Ctx](f: In => Future[Out])(tuple: (In, Ctx)): Future[(Out, Ctx)] =
    f(tuple._1).map(_ -> tuple._2)(ExecutionContexts.parasitic)

  def mapSourceAsyncPartition[In, Out, Partition, Mat](source: Source[In, Mat], parallelism: Int,
      bufferSize: Int = DefaultBufferSize)(
      extractPartition: In => Partition)(
      f: In => Future[Out]): Source[Out, Mat] =
    source.via(new MapAsyncPartition[In, Out, Partition](parallelism, bufferSize, extractPartition, f))

  def mapSourceWithContextAsyncPartition[In, Ctx, T, Partition, Mat](flow: SourceWithContext[In, Ctx, Mat],
      parallelism: Int, bufferSize: Int = DefaultBufferSize)(
      extractPartition: In => Partition)(
      f: In => Future[T]): SourceWithContext[T, Ctx, Mat] =
    flow.via(
      new MapAsyncPartition[(In, Ctx), (T, Ctx), Partition](
        parallelism,
        bufferSize,
        extractPartitionWithCtx(extractPartition),
        fWithCtx(f)))

  def mapFlowAsyncPartition[In, Out, T, Partition, Mat](flow: Flow[In, Out, Mat], parallelism: Int,
      bufferSize: Int = DefaultBufferSize)(
      extractPartition: Out => Partition)(
      f: Out => Future[T]): Flow[In, T, Mat] =
    flow.via(new MapAsyncPartition[Out, T, Partition](parallelism, bufferSize, extractPartition, f))

  def mapFlowWithContextAsyncPartition[In, Out, CtxIn, CtxOut, T, Partition, Mat](
      flow: FlowWithContext[In, CtxIn, Out, CtxOut, Mat],
      parallelism: Int, bufferSize: Int = DefaultBufferSize)(
      extractPartition: Out => Partition)(
      f: Out => Future[T]): FlowWithContext[In, CtxIn, T, CtxOut, Mat] =
    flow.via(
      new MapAsyncPartition[(Out, CtxOut), (T, CtxOut), Partition](
        parallelism,
        bufferSize,
        extractPartitionWithCtx(extractPartition),
        fWithCtx(f)))

  private object Holder {
    val NotYetThere: Failure[Nothing] = Failure(new Exception with NoStackTrace)
  }

  private final class Holder[T](var elem: Try[T], val cb: AsyncCallback[Holder[T]]) extends (Try[T] => Unit) {

    // To support both fail-fast when the supervision directive is Stop
    // and not calling the decider multiple times (#23888) we need to cache the decider result and re-use that
    private var cachedSupervisionDirective: Option[Supervision.Directive] = None

    def supervisionDirectiveFor(decider: Supervision.Decider, ex: Throwable): Supervision.Directive = {
      cachedSupervisionDirective match {
        case Some(d) => d
        case _ =>
          val d = decider(ex)
          cachedSupervisionDirective = Some(d)
          d
      }
    }

    def setElem(t: Try[T]): Unit = {
      elem = t
    }

    override def apply(t: Try[T]): Unit = {
      setElem(t)
      cb.invoke(this)
    }
  }
}

private[stream] class MapAsyncPartition[In, Out, Partition](
    parallelism: Int,
    bufferSize: Int,
    extractPartition: In => Partition,
    f: In => Future[Out]) extends GraphStage[FlowShape[In, Out]] {

  private val in = Inlet[In]("MapAsyncPartition.in")
  private val out = Outlet[Out]("MapAsyncPartition.out")

  override val shape: FlowShape[In, Out] = FlowShape(in, out)

  override def initialAttributes: Attributes = Attributes(Name("MapAsyncPartition")) and SourceLocation.forLambda(f)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {

      private val contextPropagation = pekko.stream.impl.ContextPropagation()

      private case class Contextual[T](context: AnyRef, element: T) {
        private var suspended = false

        def suspend(): Unit =
          if (!suspended) {
            suspended = true
            contextPropagation.suspendContext()
          }

        def resume(): Unit =
          if (suspended) {
            suspended = false
            contextPropagation.resumeContext(context)
          }

      }

      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var inProgress: mutable.Map[Partition, Contextual[MapAsyncPartition.Holder[Out]]] = _
      private var waiting: mutable.Queue[(Partition, Contextual[In])] = _

      private val futureCB = getAsyncCallback[MapAsyncPartition.Holder[Out]](holder =>
        holder.elem match {
          case Success(_) => pushNextIfPossible()
          case Failure(ex) =>
            holder.supervisionDirectiveFor(decider, ex) match {
              // fail fast as if supervision says so
              case Supervision.Stop => failStage(ex)
              case _                => pushNextIfPossible()
            }
        })

      override def preStart(): Unit = {
        inProgress = mutable.Map()
        waiting = mutable.Queue()
      }

      override def onPull(): Unit =
        pushNextIfPossible()

      override def onPush(): Unit = {
        try {
          val element = Contextual(contextPropagation.currentContext(), grab(in))
          val partition = extractPartition(element.element)

          if (inProgress.contains(partition) || inProgress.size >= parallelism) {
            element.suspend()
            waiting.enqueue(partition -> element)
          } else {
            processElement(partition, element)
          }
        } catch {
          case NonFatal(ex) => if (decider(ex) == Supervision.Stop) failStage(ex)
        }

        pullIfNeeded()
      }

      override def onUpstreamFinish(): Unit =
        if (idle()) completeStage()

      private def processElement(partition: Partition, element: Contextual[In]): Unit = {
        val future = f(element.element)
        val holder = new MapAsyncPartition.Holder[Out](MapAsyncPartition.Holder.NotYetThere, futureCB)
        inProgress.put(partition, Contextual(element.context, holder))

        future.value match {
          case None    => future.onComplete(holder)(ExecutionContexts.parasitic)
          case Some(v) =>
            // #20217 the future is already here, optimization: avoid scheduling it on the dispatcher and
            // run the logic directly on this thread
            holder.setElem(v)
            v match {
              // this optimization also requires us to stop the stage to fail fast if the decider says so:
              case Failure(ex) if holder.supervisionDirectiveFor(decider, ex) == Supervision.Stop => failStage(ex)
              case _                                                                              => pushNextIfPossible()
            }
        }
      }

      @nowarn("msg=deprecated") // use Map.retain to support Scala 2.12
      private def pushNextIfPossible(): Unit =
        if (inProgress.isEmpty) {
          drainQueue()
          pullIfNeeded()
        } else if (isAvailable(out)) {
          inProgress.retain { case (_, Contextual(_, holder)) =>
            if ((holder.elem eq MapAsyncPartition.Holder.NotYetThere) || !isAvailable(out)) {
              true
            } else {
              holder.elem match {
                case Success(elem) =>
                  if (elem != null) {
                    push(out, elem)
                    pullIfNeeded()
                  } else {
                    // elem is null
                    pullIfNeeded()
                  }

                case Failure(NonFatal(ex)) =>
                  holder.supervisionDirectiveFor(decider, ex) match {
                    // this could happen if we are looping in pushNextIfPossible and end up on a failed future before the
                    // onComplete callback has run
                    case Supervision.Stop =>
                      failStage(ex)
                    case _ =>
                    // try next element
                  }
                case Failure(ex) =>
                  // fatal exception in buffer, not sure that it can actually happen, but for good measure
                  throw ex
              }
              false
            }
          }
          drainQueue()
        }

      private def drainQueue(): Unit = {
        if (waiting.nonEmpty) {
          val todo = waiting
          waiting = mutable.Queue[(Partition, Contextual[In])]()

          todo.foreach { case (partition, element) =>
            if (inProgress.size >= parallelism || inProgress.contains(partition)) {
              waiting.enqueue(partition -> element)
            } else {
              element.resume()
              processElement(partition, element)
            }
          }
        }
      }

      private def pullIfNeeded(): Unit =
        if (isClosed(in) && idle()) completeStage()
        else if (waiting.size < bufferSize && !hasBeenPulled(in)) tryPull(in)
      // else already pulled and waiting for next element

      private def idle(): Boolean =
        inProgress.isEmpty && waiting.isEmpty

      setHandlers(in, out, this)
    }

}
