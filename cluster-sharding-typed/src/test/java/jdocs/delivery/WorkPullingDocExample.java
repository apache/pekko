/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.delivery;

// #imports
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.delivery.ConsumerController;
import org.apache.pekko.actor.typed.delivery.DurableProducerQueue;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

// #imports

// #producer
import org.apache.pekko.actor.typed.delivery.WorkPullingProducerController;
import org.apache.pekko.Done;

// #producer

// #durable-queue
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.delivery.EventSourcedProducerQueue;

// #durable-queue

import org.apache.pekko.actor.typed.javadsl.StashBuffer;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

interface WorkPullingDocExample {

  // #consumer
  public class ImageConverter {
    interface Command {}

    public record ConversionJob(UUID resultId, String fromFormat, String toFormat, byte[] image) {}

    private record WrappedDelivery(ConsumerController.Delivery<ConversionJob> delivery)
        implements Command {}

    public static ServiceKey<ConsumerController.Command<ConversionJob>> serviceKey =
        ServiceKey.create(ConsumerController.serviceKeyClass(), "ImageConverter");

    public static Behavior<Command> create() {
      return Behaviors.setup(
          context -> {
            ActorRef<ConsumerController.Delivery<ConversionJob>> deliveryAdapter =
                context.messageAdapter(ConsumerController.deliveryClass(), WrappedDelivery::new);
            ActorRef<ConsumerController.Command<ConversionJob>> consumerController =
                context.spawn(ConsumerController.create(serviceKey), "consumerController");
            consumerController.tell(new ConsumerController.Start<>(deliveryAdapter));

            return Behaviors.receive(Command.class)
                .onMessage(WrappedDelivery.class, ImageConverter::onDelivery)
                .build();
          });
    }

    private static Behavior<Command> onDelivery(WrappedDelivery w) {
      byte[] image = w.delivery().message().image();
      String fromFormat = w.delivery().message().fromFormat();
      String toFormat = w.delivery().message().toFormat();
      // convert image...
      // store result with resultId key for later retrieval

      // and when completed confirm
      w.delivery().confirmTo().tell(ConsumerController.confirmed());

      return Behaviors.same();
    }
  }

  // #consumer

  // #producer
  public class ImageWorkManager {

    interface Command {}

    public record Convert(String fromFormat, String toFormat, byte[] image) implements Command {}

    public record GetResult(UUID resultId, ActorRef<Optional<byte[]>> replyTo) implements Command {}

    private record WrappedRequestNext(
        WorkPullingProducerController.RequestNext<ImageConverter.ConversionJob> next)
        implements Command {}

    // #producer
    // #ask
    public record ConvertRequest(
        String fromFormat, String toFormat, byte[] image, ActorRef<ConvertResponse> replyTo)
        implements Command {}

    interface ConvertResponse {}

    public record ConvertAccepted(UUID resultId) implements ConvertResponse {}

    enum ConvertRejected implements ConvertResponse {
      INSTANCE
    }

    public record ConvertTimedOut(UUID resultId) implements ConvertResponse {}

    private record AskReply(
        UUID resultId, ActorRef<ConvertResponse> originalReplyTo, boolean timeout)
        implements Command {}

    // #ask
    // #producer

    private final ActorContext<Command> context;
    private final StashBuffer<Command> stashBuffer;

    private ImageWorkManager(ActorContext<Command> context, StashBuffer<Command> stashBuffer) {
      this.context = context;
      this.stashBuffer = stashBuffer;
    }

    public static Behavior<Command> create() {
      return Behaviors.setup(
          context -> {
            ActorRef<WorkPullingProducerController.RequestNext<ImageConverter.ConversionJob>>
                requestNextAdapter =
                    context.messageAdapter(
                        WorkPullingProducerController.requestNextClass(), WrappedRequestNext::new);
            ActorRef<WorkPullingProducerController.Command<ImageConverter.ConversionJob>>
                producerController =
                    context.spawn(
                        WorkPullingProducerController.create(
                            ImageConverter.ConversionJob.class,
                            "workManager",
                            ImageConverter.serviceKey,
                            Optional.empty()),
                        "producerController");
            // #producer
            // #durable-queue
            Behavior<DurableProducerQueue.Command<ImageConverter.ConversionJob>> durableQueue =
                EventSourcedProducerQueue.create(PersistenceId.ofUniqueId("ImageWorkManager"));
            ActorRef<WorkPullingProducerController.Command<ImageConverter.ConversionJob>>
                durableProducerController =
                    context.spawn(
                        WorkPullingProducerController.create(
                            ImageConverter.ConversionJob.class,
                            "workManager",
                            ImageConverter.serviceKey,
                            Optional.of(durableQueue)),
                        "producerController");
            // #durable-queue
            // #producer
            producerController.tell(new WorkPullingProducerController.Start<>(requestNextAdapter));

            return Behaviors.withStash(
                1000, stashBuffer -> new ImageWorkManager(context, stashBuffer).waitForNext());
          });
    }

    private Behavior<Command> waitForNext() {
      return Behaviors.receive(Command.class)
          .onMessage(WrappedRequestNext.class, this::onWrappedRequestNext)
          .onMessage(Convert.class, this::onConvertWait)
          .onMessage(GetResult.class, this::onGetResult)
          .build();
    }

    private Behavior<Command> onWrappedRequestNext(WrappedRequestNext w) {
      return stashBuffer.unstashAll(active(w.next()));
    }

    private Behavior<Command> onConvertWait(Convert convert) {
      if (stashBuffer.isFull()) {
        context.getLog().warn("Too many Convert requests.");
        return Behaviors.same();
      } else {
        stashBuffer.stash(convert);
        return Behaviors.same();
      }
    }

    private Behavior<Command> onGetResult(GetResult get) {
      // TODO retrieve the stored result and reply
      return Behaviors.same();
    }

    private Behavior<Command> active(
        WorkPullingProducerController.RequestNext<ImageConverter.ConversionJob> next) {
      return Behaviors.receive(Command.class)
          .onMessage(Convert.class, c -> onConvert(c, next))
          .onMessage(GetResult.class, this::onGetResult)
          .onMessage(WrappedRequestNext.class, this::onUnexpectedWrappedRequestNext)
          .build();
    }

    private Behavior<Command> onUnexpectedWrappedRequestNext(WrappedRequestNext w) {
      throw new IllegalStateException("Unexpected RequestNext");
    }

    private Behavior<Command> onConvert(
        Convert convert,
        WorkPullingProducerController.RequestNext<ImageConverter.ConversionJob> next) {
      UUID resultId = UUID.randomUUID();
      next.sendNextTo()
          .tell(
              new ImageConverter.ConversionJob(
                  resultId, convert.fromFormat(), convert.toFormat(), convert.image()));
      return waitForNext();
    }

    // #producer

    Object askScope =
        new Object() {
          // #ask
          private Behavior<Command> waitForNext() {
            return Behaviors.receive(Command.class)
                .onMessage(WrappedRequestNext.class, this::onWrappedRequestNext)
                .onMessage(ConvertRequest.class, this::onConvertRequestWait)
                .onMessage(AskReply.class, this::onAskReply)
                .onMessage(GetResult.class, this::onGetResult)
                .build();
          }

          private Behavior<Command> onConvertRequestWait(ConvertRequest convert) {
            if (stashBuffer.isFull()) {
              convert.replyTo().tell(ConvertRejected.INSTANCE);
              return Behaviors.same();
            } else {
              stashBuffer.stash(convert);
              return Behaviors.same();
            }
          }

          private Behavior<Command> onAskReply(AskReply reply) {
            if (reply.timeout())
              reply.originalReplyTo().tell(new ConvertTimedOut(reply.resultId()));
            else reply.originalReplyTo().tell(new ConvertAccepted(reply.resultId()));
            return Behaviors.same();
          }

          private Behavior<Command> onWrappedRequestNext(WrappedRequestNext w) {
            return stashBuffer.unstashAll(active(w.next()));
          }

          private Behavior<Command> onGetResult(GetResult get) {
            // TODO retrieve the stored result and reply
            return Behaviors.same();
          }

          private Behavior<Command> active(
              WorkPullingProducerController.RequestNext<ImageConverter.ConversionJob> next) {
            return Behaviors.receive(Command.class)
                .onMessage(ConvertRequest.class, c -> onConvertRequest(c, next))
                .onMessage(AskReply.class, this::onAskReply)
                .onMessage(GetResult.class, this::onGetResult)
                .onMessage(WrappedRequestNext.class, this::onUnexpectedWrappedRequestNext)
                .build();
          }

          private Behavior<Command> onConvertRequest(
              ConvertRequest convert,
              WorkPullingProducerController.RequestNext<ImageConverter.ConversionJob> next) {
            UUID resultId = UUID.randomUUID();

            context.ask(
                Done.class,
                next.askNextTo(),
                Duration.ofSeconds(5),
                askReplyTo ->
                    new WorkPullingProducerController.MessageWithConfirmation<>(
                        new ImageConverter.ConversionJob(
                            resultId, convert.fromFormat(), convert.toFormat(), convert.image()),
                        askReplyTo),
                (done, exc) -> {
                  if (exc == null) return new AskReply(resultId, convert.replyTo(), false);
                  else return new AskReply(resultId, convert.replyTo(), true);
                });

            return waitForNext();
          }

          private Behavior<Command> onUnexpectedWrappedRequestNext(WrappedRequestNext w) {
            throw new IllegalStateException("Unexpected RequestNext");
          }

          // #ask
        };
    // #producer
  }
  // #producer

}
