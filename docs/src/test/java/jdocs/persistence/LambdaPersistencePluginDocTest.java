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

package jdocs.persistence;

// #plugin-imports
import org.apache.pekko.dispatch.Futures;
import org.apache.pekko.persistence.*;
import org.apache.pekko.persistence.journal.japi.*;
import org.apache.pekko.persistence.snapshot.japi.*;
// #plugin-imports

import org.apache.pekko.actor.*;
import org.apache.pekko.persistence.journal.leveldb.SharedLeveldbJournal;
import org.apache.pekko.persistence.journal.leveldb.SharedLeveldbStore;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.runner.RunWith;
import org.scalatestplus.junit.JUnitRunner;
import scala.concurrent.Future;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.iq80.leveldb.util.FileUtils;
import java.util.Optional;

import org.apache.pekko.persistence.japi.journal.JavaJournalSpec;
import org.apache.pekko.persistence.japi.snapshot.JavaSnapshotStoreSpec;

public class LambdaPersistencePluginDocTest {

  static Object o1 =
      new Object() {
        final ActorSystem system = null;
        // #shared-store-creation
        final ActorRef store = system.actorOf(Props.create(SharedLeveldbStore.class), "store");
        // #shared-store-creation

        // #shared-store-usage
        class SharedStorageUsage extends AbstractActor {
          @Override
          public void preStart() throws Exception {
            String path = "pekko://example@127.0.0.1:7355/user/store";
            ActorSelection selection = getContext().actorSelection(path);
            selection.tell(new Identify(1), getSelf());
          }

          @Override
          public Receive createReceive() {
            return receiveBuilder()
                .match(
                    ActorIdentity.class,
                    ai -> {
                      if (ai.correlationId().equals(1)) {
                        Optional<ActorRef> store = ai.getActorRef();
                        if (store.isPresent()) {
                          SharedLeveldbJournal.setStore(store.get(), getContext().getSystem());
                        } else {
                          throw new RuntimeException("Couldn't identify store");
                        }
                      }
                    })
                .build();
          }
        }
        // #shared-store-usage
      };

  class MySnapshotStore extends SnapshotStore {
    @Override
    public CompletionStage<Optional<SelectedSnapshot>> doLoadAsync(
        String persistenceId, SnapshotSelectionCriteria criteria) {
      return null;
    }

    @Override
    public CompletionStage<Void> doSaveAsync(SnapshotMetadata metadata, Object snapshot) {
      return null;
    }

    @Override
    public CompletionStage<Void> doDeleteAsync(SnapshotMetadata metadata) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> doDeleteAsync(
        String persistenceId, SnapshotSelectionCriteria criteria) {
      return CompletableFuture.completedFuture(null);
    }
  }

  class MyAsyncJournal extends AsyncWriteJournal {
    // #sync-journal-plugin-api
    @Override
    public CompletionStage<Iterable<Optional<Exception>>> doAsyncWriteMessages(
        Iterable<AtomicWrite> messages) {
      try {
        Iterable<Optional<Exception>> result = new ArrayList<Optional<Exception>>();
        // blocking call here...
        // result.add(..)
        return CompletableFuture.completedFuture(result);
      } catch (Exception e) {
        return Futures.failedCompletionStage(e);
      }
    }
    // #sync-journal-plugin-api

    @Override
    public CompletionStage<Void> doAsyncDeleteMessagesTo(String persistenceId, long toSequenceNr) {
      return null;
    }

    @Override
    public Future<Void> doAsyncReplayMessages(
        String persistenceId,
        long fromSequenceNr,
        long toSequenceNr,
        long max,
        Consumer<PersistentRepr> replayCallback) {
      return null;
    }

    @Override
    public Future<Long> doAsyncReadHighestSequenceNr(String persistenceId, long fromSequenceNr) {
      return null;
    }
  }

  static Object o2 =
      new Object() {
        // #journal-tck-java
        @RunWith(JUnitRunner.class)
        class MyJournalSpecTest extends JavaJournalSpec {

          public MyJournalSpecTest() {
            super(
                ConfigFactory.parseString(
                    "pekko.persistence.journal.plugin = "
                        + "\"pekko.persistence.journal.leveldb-shared\""));
          }

          @Override
          public CapabilityFlag supportsRejectingNonSerializableObjects() {
            return CapabilityFlag.off();
          }
        }
        // #journal-tck-java
      };

  static Object o3 =
      new Object() {
        // #snapshot-store-tck-java
        @RunWith(JUnitRunner.class)
        class MySnapshotStoreTest extends JavaSnapshotStoreSpec {

          public MySnapshotStoreTest() {
            super(
                ConfigFactory.parseString(
                    "pekko.persistence.snapshot-store.plugin = "
                        + "\"pekko.persistence.snapshot-store.local\""));
          }
        }
        // #snapshot-store-tck-java
      };

  static Object o4 =
      new Object() {
        // https://github.com/pekko/pekko/issues/26826
        // #journal-tck-before-after-java
        @RunWith(JUnitRunner.class)
        class MyJournalSpecTest extends JavaJournalSpec {

          List<File> storageLocations = new ArrayList<File>();

          public MyJournalSpecTest() {
            super(
                ConfigFactory.parseString(
                    "persistence.journal.plugin = "
                        + "\"pekko.persistence.journal.leveldb-shared\""));

            Config config = system().settings().config();
            storageLocations.add(
                new File(config.getString("pekko.persistence.journal.leveldb.dir")));
            storageLocations.add(
                new File(config.getString("pekko.persistence.snapshot-store.local.dir")));
          }

          @Override
          public CapabilityFlag supportsRejectingNonSerializableObjects() {
            return CapabilityFlag.on();
          }

          @Override
          public void beforeAll() {
            for (File storageLocation : storageLocations) {
              FileUtils.deleteRecursively(storageLocation);
            }
            super.beforeAll();
          }

          @Override
          public void afterAll() {
            super.afterAll();
            for (File storageLocation : storageLocations) {
              FileUtils.deleteRecursively(storageLocation);
            }
          }
        }
        // #journal-tck-before-after-java
      };
}
