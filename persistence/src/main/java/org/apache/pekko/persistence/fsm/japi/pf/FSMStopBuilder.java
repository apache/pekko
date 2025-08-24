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

package org.apache.pekko.persistence.fsm.japi.pf;

import org.apache.pekko.japi.function.Predicate;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.japi.function.Procedure2;
import org.apache.pekko.japi.function.Procedure3;
import org.apache.pekko.japi.pf.UnitPFBuilder;
import org.apache.pekko.persistence.fsm.PersistentFSM;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * Builder used to create a partial function for {@link org.apache.pekko.actor.FSM#onTermination}.
 *
 * @deprecated use EventSourcedBehavior since Akka 2.6.0
 * @param <S> the state type
 * @param <D> the data type
 */
@Deprecated
public class FSMStopBuilder<S, D> {

  private UnitPFBuilder<org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent<S, D>> builder =
      new UnitPFBuilder<>();

  /**
   * Add a case statement that matches on an {@link org.apache.pekko.actor.FSM.Reason}.
   *
   * @param reason the reason for the termination
   * @param apply an action to apply to the event and state data if there is a match
   * @return the builder with the case statement added
   */
  public FSMStopBuilder<S, D> stop(
      final org.apache.pekko.persistence.fsm.PersistentFSM.Reason reason,
      final Procedure2<S, D> apply) {
    builder.match(
        org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent.class,
        new Predicate<org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent>() {
          @Override
          public boolean test(org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent e) {
            return reason.equals(e.reason());
          }
        },
        new Procedure<org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent>() {
          public void apply(org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent e)
              throws Exception {
            @SuppressWarnings("unchecked")
            S s = (S) e.currentState();
            @SuppressWarnings("unchecked")
            D d = (D) e.stateData();
            apply.apply(s, d);
          }
        });

    return this;
  }

  /**
   * Add a case statement that matches on a reason type.
   *
   * @param reasonType the reason type to match on
   * @param apply an action to apply to the reason, event and state data if there is a match
   * @param <P> the reason type to match on
   * @return the builder with the case statement added
   */
  public <P extends org.apache.pekko.persistence.fsm.PersistentFSM.Reason>
      FSMStopBuilder<S, D> stop(final Class<P> reasonType, final Procedure3<P, S, D> apply) {
    return this.stop(
        reasonType,
        new Predicate<P>() {
          @Override
          public boolean test(P p) {
            return true;
          }
        },
        apply);
  }

  /**
   * Add a case statement that matches on a reason type and a predicate.
   *
   * @param reasonType the reason type to match on
   * @param apply an action to apply to the reason, event and state data if there is a match
   * @param predicate a predicate that will be evaluated on the reason if the type matches
   * @param <P> the reason type to match on
   * @return the builder with the case statement added
   */
  public <P extends org.apache.pekko.persistence.fsm.PersistentFSM.Reason>
      FSMStopBuilder<S, D> stop(
          final Class<P> reasonType,
          final Predicate<P> predicate,
          final Procedure3<P, S, D> apply) {
    builder.match(
        org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent.class,
        new Predicate<org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent>() {
          @Override
          public boolean test(org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent e) {
            if (reasonType.isInstance(e.reason())) {
              @SuppressWarnings("unchecked")
              P p = (P) e.reason();
              return predicate.test(p);
            } else {
              return false;
            }
          }
        },
        new Procedure<PersistentFSM.StopEvent>() {
          public void apply(org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent e)
              throws Exception {
            @SuppressWarnings("unchecked")
            P p = (P) e.reason();
            @SuppressWarnings("unchecked")
            S s = (S) e.currentState();
            @SuppressWarnings("unchecked")
            D d = (D) e.stateData();
            apply.apply(p, s, d);
          }
        });

    return this;
  }

  /**
   * Build a {@link scala.PartialFunction} from this builder. After this call the builder will be
   * reset.
   *
   * @return a PartialFunction for this builder.
   */
  public PartialFunction<org.apache.pekko.persistence.fsm.PersistentFSM.StopEvent<S, D>, BoxedUnit>
      build() {
    return builder.build();
  }
}
