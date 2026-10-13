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

package docs.remoting

import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.event.MarkerLoggingAdapter
import pekko.remote.artery.tcp.ConfigSSLEngineProvider

import com.typesafe.config.Config

// #ssl-engine-provider-session-verification
class ClusterScopedSSLEngineProvider(config: Config, log: MarkerLoggingAdapter)
    extends ConfigSSLEngineProvider(config, log) {

  def this(system: ActorSystem) =
    this(
      system.settings.config.getConfig("pekko.remote.artery.ssl.config-ssl-engine"),
      Logging.withMarker(system, classOf[ClusterScopedSSLEngineProvider].getName))

  private val allowedNames = Set("my-service")

  private def verify(session: SSLSession): Option[Throwable] =
    session.getPeerCertificates.headOption match {
      case Some(x509: X509Certificate) =>
        val subject = x509.getSubjectX500Principal.getName
        if (allowedNames.exists(subject.contains)) None
        else Some(new IllegalArgumentException(s"Peer [$subject] is not allowed to join this cluster"))
      case _ =>
        Some(new IllegalArgumentException("No X.509 peer certificate presented"))
    }

  override def verifyClientSession(hostname: String, session: SSLSession): Option[Throwable] =
    verify(session)

  override def verifyServerSession(hostname: String, session: SSLSession): Option[Throwable] =
    verify(session)
}
// #ssl-engine-provider-session-verification
