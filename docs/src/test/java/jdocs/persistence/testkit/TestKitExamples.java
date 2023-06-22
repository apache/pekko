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

package jdocs.persistence.testkit;

import org.apache.pekko.persistence.testkit.DeleteEvents;
import org.apache.pekko.persistence.testkit.DeleteSnapshotByMeta;
import org.apache.pekko.persistence.testkit.DeleteSnapshotsByCriteria;
import org.apache.pekko.persistence.testkit.JournalOperation;
import org.apache.pekko.persistence.testkit.ProcessingPolicy;
import org.apache.pekko.persistence.testkit.ProcessingResult;
import org.apache.pekko.persistence.testkit.ProcessingSuccess;
import org.apache.pekko.persistence.testkit.ReadEvents;
import org.apache.pekko.persistence.testkit.ReadSeqNum;
import org.apache.pekko.persistence.testkit.ReadSnapshot;
import org.apache.pekko.persistence.testkit.Reject;
import org.apache.pekko.persistence.testkit.SnapshotOperation;
import org.apache.pekko.persistence.testkit.StorageFailure;
import org.apache.pekko.persistence.testkit.WriteEvents;
import org.apache.pekko.persistence.testkit.WriteSnapshot;

public class TestKitExamples {

  // #set-event-storage-policy
  class SampleEventStoragePolicy implements ProcessingPolicy<JournalOperation> {

    // you can use internal state, it does not need to be thread safe
    int count = 1;

    @Override
    public ProcessingResult tryProcess(String processId, JournalOperation processingUnit) {
      // check the type of operation and react with success or with reject or with failure.
      // if you return ProcessingSuccess the operation will be performed, otherwise not.
      if (count < 10) {
        count += 1;
        if (processingUnit instanceof ReadEvents) {
          ReadEvents read = (ReadEvents) processingUnit;
          if (read.batch().nonEmpty()) {
            ProcessingSuccess.getInstance();
          } else {
            return StorageFailure.create();
          }
        } else if (processingUnit instanceof WriteEvents) {
          return ProcessingSuccess.getInstance();
        } else if (processingUnit instanceof DeleteEvents) {
          return ProcessingSuccess.getInstance();
        } else if (processingUnit.equals(ReadSeqNum.getInstance())) {
          return Reject.create();
        }
        // you can set your own exception
        return StorageFailure.create(new RuntimeException("your exception"));
      } else {
        return ProcessingSuccess.getInstance();
      }
    }
  }
  // #set-event-storage-policy

  // #set-snapshot-storage-policy
  class SnapshotStoragePolicy implements ProcessingPolicy<SnapshotOperation> {

    // you can use internal state, it doesn't need to be thread safe
    int count = 1;

    @Override
    public ProcessingResult tryProcess(String processId, SnapshotOperation processingUnit) {
      // check the type of operation and react with success or with failure.
      // if you return ProcessingSuccess the operation will be performed, otherwise not.
      if (count < 10) {
        count += 1;
        if (processingUnit instanceof ReadSnapshot) {
          ReadSnapshot read = (ReadSnapshot) processingUnit;
          if (read.getSnapshot().isPresent()) {
            ProcessingSuccess.getInstance();
          } else {
            return StorageFailure.create();
          }
        } else if (processingUnit instanceof WriteSnapshot) {
          return ProcessingSuccess.getInstance();
        } else if (processingUnit instanceof DeleteSnapshotsByCriteria) {
          return ProcessingSuccess.getInstance();
        } else if (processingUnit instanceof DeleteSnapshotByMeta) {
          return ProcessingSuccess.getInstance();
        }
        // you can set your own exception
        return StorageFailure.create(new RuntimeException("your exception"));
      } else {
        return ProcessingSuccess.getInstance();
      }
    }
  }
  // #set-snapshot-storage-policy

}
