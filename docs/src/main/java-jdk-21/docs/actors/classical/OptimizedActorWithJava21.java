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

package docs.actors.classical;
// #pattern-matching

import org.apache.pekko.actor.UntypedAbstractActor;

public class OptimizedActorWithJava21 extends UntypedAbstractActor {
    public static class Msg1 {
    }

    public static class Msg2 {
    }

    public static class Msg3 {
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        switch (msg) {
            case Msg1 msg1 -> receiveMsg1(msg1);
            case Msg2 msg2 -> receiveMsg2(msg2);
            case Msg3 msg3 -> receiveMsg3(msg3);
            default -> unhandled(msg);
        }
    }

    private void receiveMsg1(Msg1 msg) {
        // actual work
    }

    private void receiveMsg2(Msg2 msg) {
        // actual work
    }

    private void receiveMsg3(Msg3 msg) {
        // actual work
    }
}
// #pattern-matching
