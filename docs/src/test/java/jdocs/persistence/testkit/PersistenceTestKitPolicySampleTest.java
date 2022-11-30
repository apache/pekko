/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.persistence.testkit;

import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.persistence.testkit.JournalOperation;
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin;
import org.apache.pekko.persistence.testkit.ProcessingPolicy;
import org.apache.pekko.persistence.testkit.ProcessingResult;
import org.apache.pekko.persistence.testkit.ProcessingSuccess;
import org.apache.pekko.persistence.testkit.StorageFailure;
import org.apache.pekko.persistence.testkit.WriteEvents;
import org.apache.pekko.persistence.testkit.javadsl.PersistenceTestKit;
import org.apache.pekko.persistence.typed.PersistenceId;
import com.typesafe.config.ConfigFactory;
import jdocs.AbstractJavaTest;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

// #policy-test
public class PersistenceTestKitPolicySampleTest extends AbstractJavaTest {

  @ClassRule
  public static final TestKitJunitResource testKit =
      new TestKitJunitResource(
          PersistenceTestKitPlugin.getInstance()
              .config()
              .withFallback(ConfigFactory.defaultApplication()));

  PersistenceTestKit persistenceTestKit = PersistenceTestKit.create(testKit.system());

  @Before
  public void beforeEach() {
    persistenceTestKit.clearAll();
    persistenceTestKit.resetPolicy();
  }

  @Test
  public void test() {
    SampleEventStoragePolicy policy = new SampleEventStoragePolicy();
    persistenceTestKit.withPolicy(policy);

    PersistenceId persistenceId = PersistenceId.ofUniqueId("some-id");
    ActorRef<YourPersistentBehavior.Cmd> ref =
        testKit.spawn(YourPersistentBehavior.create(persistenceId));

    YourPersistentBehavior.Cmd cmd = new YourPersistentBehavior.Cmd("data");
    ref.tell(cmd);

    persistenceTestKit.expectNothingPersisted(persistenceId.id());
  }

  static class SampleEventStoragePolicy implements ProcessingPolicy<JournalOperation> {
    @Override
    public ProcessingResult tryProcess(String processId, JournalOperation processingUnit) {
      if (processingUnit instanceof WriteEvents) {
        return StorageFailure.create();
      } else {
        return ProcessingSuccess.getInstance();
      }
    }
  }
}
// #policy-test
