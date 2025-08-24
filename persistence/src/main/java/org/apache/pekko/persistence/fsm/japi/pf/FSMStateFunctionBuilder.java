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

import java.util.List;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.function.Function2;
import org.apache.pekko.japi.function.Predicate;
import org.apache.pekko.japi.function.Predicate2;
import org.apache.pekko.japi.pf.PFBuilder;
import org.apache.pekko.persistence.fsm.PersistentFSM;
import scala.PartialFunction;

/**
 * Builder used to create a partial function for {@link org.apache.pekko.actor.FSM#whenUnhandled}.
 *
 * @deprecated use EventSourcedBehavior since Akka 2.6.0
 * @param <S> the state type
 * @param <D> the data type
 * @param <E> the domain event type
 */
@SuppressWarnings("rawtypes")
@Deprecated
public class FSMStateFunctionBuilder<S, D, E> {

  private final PFBuilder<
          org.apache.pekko.persistence.fsm.PersistentFSM.Event<D>,
          org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>>
      builder = PFBuilder.create();

  /**
   * An erased processing of the event matcher. The compile time checks are enforced by the public
   * typed versions.
   *
   * <p>It works like this.
   *
   * <p>If eventOrType or dataOrType is a Class, then we do a isInstance check, otherwise we do an
   * equals check. The null value compares true for anything. If the predicate is null, it is
   * skipped otherwise the predicate has to match as well.
   *
   * @param eventOrType an event or a type to match against
   * @param dataOrType a data instance or a type to match against
   * @param predicate a predicate to match against
   * @param apply an action to apply to the event and state data if there is a match
   * @return the builder with the case statement added
   */
  private FSMStateFunctionBuilder<S, D, E> erasedEvent(
      final Object eventOrType,
      final Object dataOrType,
      final Predicate2 predicate,
      final Function2 apply) {
    builder.match(
        org.apache.pekko.persistence.fsm.PersistentFSM.Event.class,
        new Predicate<PersistentFSM.Event>() {
          @Override
          public boolean test(org.apache.pekko.persistence.fsm.PersistentFSM.Event e) {
            boolean res = true;
            if (eventOrType != null) {
              if (eventOrType instanceof Class) {
                Class eventType = (Class) eventOrType;
                res = eventType.isInstance(e.event());
              } else {
                res = eventOrType.equals(e.event());
              }
            }
            if (res && dataOrType != null) {
              if (dataOrType instanceof Class) {
                Class dataType = (Class) dataOrType;
                res = dataType.isInstance(e.stateData());
              } else {
                res = dataOrType.equals(e.stateData());
              }
            }
            if (res && predicate != null) {
              @SuppressWarnings("unchecked")
              boolean ures = predicate.test(e.event(), e.stateData());
              res = ures;
            }
            return res;
          }
        },
        new Function<PersistentFSM.Event, PersistentFSM.State<S, D, E>>() {
          public org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E> apply(
              org.apache.pekko.persistence.fsm.PersistentFSM.Event e) throws Exception {
            @SuppressWarnings("unchecked")
            org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E> res =
                (org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>)
                    apply.apply(e.event(), e.stateData());
            return res;
          }
        });

    return this;
  }

  /**
   * Add a case statement that matches on an event and data type and a predicate.
   *
   * @param eventType the event type to match on
   * @param dataType the data type to match on
   * @param predicate a predicate to evaluate on the matched types
   * @param apply an action to apply to the event and state data if there is a match
   * @param <P> the event type to match on
   * @param <Q> the data type to match on
   * @return the builder with the case statement added
   */
  public final <P, Q> FSMStateFunctionBuilder<S, D, E> event(
      final Class<P> eventType,
      final Class<Q> dataType,
      final Predicate2<P, Q> predicate,
      final Function2<P, Q, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>> apply) {
    erasedEvent(eventType, dataType, predicate, apply);
    return this;
  }

  /**
   * Add a case statement that matches on an event and data type.
   *
   * @param eventType the event type to match on
   * @param dataType the data type to match on
   * @param apply an action to apply to the event and state data if there is a match
   * @param <P> the event type to match on
   * @param <Q> the data type to match on
   * @return the builder with the case statement added
   */
  public <P, Q> FSMStateFunctionBuilder<S, D, E> event(
      final Class<P> eventType,
      final Class<Q> dataType,
      final Function2<P, Q, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>> apply) {
    return erasedEvent(eventType, dataType, null, apply);
  }

  /**
   * Add a case statement that matches if the event type and predicate matches.
   *
   * @param eventType the event type to match on
   * @param predicate a predicate that will be evaluated on the data and the event
   * @param apply an action to apply to the event and state data if there is a match
   * @return the builder with the case statement added
   */
  public <P> FSMStateFunctionBuilder<S, D, E> event(
      final Class<P> eventType,
      final Predicate2<P, D> predicate,
      final Function2<P, D, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>> apply) {
    return erasedEvent(eventType, null, predicate, apply);
  }

  /**
   * Add a case statement that matches if the event type and predicate matches.
   *
   * @param eventType the event type to match on
   * @param apply an action to apply to the event and state data if there is a match
   * @return the builder with the case statement added
   */
  public <P> FSMStateFunctionBuilder<S, D, E> event(
      final Class<P> eventType,
      final Function2<P, D, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>> apply) {
    return erasedEvent(eventType, null, null, apply);
  }

  /**
   * Add a case statement that matches if the predicate matches.
   *
   * @param predicate a predicate that will be evaluated on the data and the event
   * @param apply an action to apply to the event and state data if there is a match
   * @return the builder with the case statement added
   */
  public FSMStateFunctionBuilder<S, D, E> event(
      final Predicate2<Object, D> predicate,
      final Function2<Object, D, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>>
          apply) {
    return erasedEvent(null, null, predicate, apply);
  }

  /**
   * Add a case statement that matches on the data type and if any of the event types in the list
   * match or any of the event instances in the list compares equal.
   *
   * @param eventMatches a list of types or instances to match against
   * @param dataType the data type to match on
   * @param apply an action to apply to the event and state data if there is a match
   * @param <Q> the data type to match on
   * @return the builder with the case statement added
   */
  public <Q> FSMStateFunctionBuilder<S, D, E> event(
      final List<Object> eventMatches,
      final Class<Q> dataType,
      final Function2<Object, Q, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>>
          apply) {
    builder.match(
        org.apache.pekko.persistence.fsm.PersistentFSM.Event.class,
        new Predicate<org.apache.pekko.persistence.fsm.PersistentFSM.Event>() {
          @Override
          public boolean test(org.apache.pekko.persistence.fsm.PersistentFSM.Event e) {
            if (dataType != null && !dataType.isInstance(e.stateData())) return false;

            boolean emMatch = false;
            Object event = e.event();
            for (Object em : eventMatches) {
              if (em instanceof Class) {
                Class emc = (Class) em;
                emMatch = emc.isInstance(event);
              } else {
                emMatch = event.equals(em);
              }
              if (emMatch) break;
            }
            return emMatch;
          }
        },
        new Function<
            org.apache.pekko.persistence.fsm.PersistentFSM.Event,
            org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>>() {
          public org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E> apply(
              org.apache.pekko.persistence.fsm.PersistentFSM.Event e) throws Exception {
            @SuppressWarnings("unchecked")
            Q q = (Q) e.stateData();
            return apply.apply(e.event(), q);
          }
        });

    return this;
  }

  /**
   * Add a case statement that matches if any of the event types in the list match or any of the
   * event instances in the list compares equal.
   *
   * @param eventMatches a list of types or instances to match against
   * @param apply an action to apply to the event and state data if there is a match
   * @return the builder with the case statement added
   */
  public FSMStateFunctionBuilder<S, D, E> event(
      final List<Object> eventMatches,
      final Function2<Object, D, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>>
          apply) {
    return event(eventMatches, null, apply);
  }

  /**
   * Add a case statement that matches on the data type and if the event compares equal.
   *
   * @param event an event to compare equal against
   * @param dataType the data type to match on
   * @param apply an action to apply to the event and state data if there is a match
   * @param <Q> the data type to match on
   * @return the builder with the case statement added
   */
  public <P, Q> FSMStateFunctionBuilder<S, D, E> eventEquals(
      final P event,
      final Class<Q> dataType,
      final Function2<P, Q, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>> apply) {
    return erasedEvent(event, dataType, null, apply);
  }

  /**
   * Add a case statement that matches if event compares equal.
   *
   * @param event an event to compare equal against
   * @param apply an action to apply to the event and state data if there is a match
   * @return the builder with the case statement added
   */
  public <P> FSMStateFunctionBuilder<S, D, E> eventEquals(
      final P event,
      final Function2<P, D, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>> apply) {
    return erasedEvent(event, null, null, apply);
  }

  /**
   * Add a case statement that matches on any type of event.
   *
   * @param apply an action to apply to the event and state data
   * @return the builder with the case statement added
   */
  public FSMStateFunctionBuilder<S, D, E> anyEvent(
      final Function2<Object, D, org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>>
          apply) {
    return erasedEvent(null, null, null, apply);
  }

  /**
   * Build a {@link scala.PartialFunction} from this builder. After this call the builder will be
   * reset.
   *
   * @return a PartialFunction for this builder.
   */
  public PartialFunction<
          org.apache.pekko.persistence.fsm.PersistentFSM.Event<D>,
          org.apache.pekko.persistence.fsm.PersistentFSM.State<S, D, E>>
      build() {
    return builder.build();
  }
}
