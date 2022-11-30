/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.typed.tutorial_2;

/*
//#iot-supervisor
package com.example;

//#iot-supervisor
*/

// #iot-supervisor
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class IotSupervisor extends AbstractBehavior<Void> {

  public static Behavior<Void> create() {
    return Behaviors.setup(IotSupervisor::new);
  }

  private IotSupervisor(ActorContext<Void> context) {
    super(context);
    context.getLog().info("IoT Application started");
  }

  // No need to handle any messages
  @Override
  public Receive<Void> createReceive() {
    return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
  }

  private IotSupervisor onPostStop() {
    getContext().getLog().info("IoT Application stopped");
    return this;
  }
}
// #iot-supervisor
