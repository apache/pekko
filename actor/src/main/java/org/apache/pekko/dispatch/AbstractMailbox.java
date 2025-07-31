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

package org.apache.pekko.dispatch;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import org.apache.pekko.dispatch.sysmsg.SystemMessage;

final class AbstractMailbox {
    final static VarHandle mailboxStatusHandle;
    final static VarHandle systemMessageHandle;

    static {
        try {
            MethodHandles.Lookup lookup =
              MethodHandles.privateLookupIn(Mailbox.class, MethodHandles.lookup());
            mailboxStatusHandle =
              lookup.findVarHandle(
                  Mailbox.class,
                  "_statusDoNotCallMeDirectly",
                  int.class);
            systemMessageHandle =
              lookup.findVarHandle(
                  Mailbox.class,
                  "_systemQueueDoNotCallMeDirectly",
                  SystemMessage.class);
        } catch(Throwable t){
            throw new ExceptionInInitializerError(t);
        }
    }
}
