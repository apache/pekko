/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.typed.tutorial_2;

/*
//#iot-app
package com.example;

//#iot-app
*/

// #iot-app
import org.apache.pekko.actor.typed.ActorSystem;

public class IotMain {

  public static void main(String[] args) {
    // Create ActorSystem and top level supervisor
    ActorSystem.create(IotSupervisor.create(), "iot-system");
  }
}
// #iot-app
