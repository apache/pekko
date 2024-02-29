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

package docs.stream.operators.sourceorflow

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

import java.net.URL

object MapWithResource {
  implicit val actorSystem: ActorSystem = ???

  // #mapWithResource-blocking-api
  trait DBDriver {
    def create(url: URL, userName: String, password: String): Connection
  }
  trait Connection {
    def close(): Unit
  }
  trait Database {
    // blocking query
    def doQuery(connection: Connection, query: String): QueryResult = ???
  }
  trait QueryResult {
    def hasMore: Boolean
    // potentially blocking retrieval of each element
    def next(): DataBaseRecord
    // potentially blocking retrieval all element
    def toList(): List[DataBaseRecord]
  }
  trait DataBaseRecord
  // #mapWithResource-blocking-api
  val url: URL = ???
  val userName = "Akka"
  val password = "Hakking"
  val dbDriver: DBDriver = ???
  def mapWithResourceExample(): Unit = {
    // #mapWithResource
    // some database for JVM
    val db: Database = ???
    Source(
      List(
        "SELECT * FROM shop ORDER BY article-0000 order by gmtModified desc limit 100;",
        "SELECT * FROM shop ORDER BY article-0001 order by gmtModified desc limit 100;"))
      .mapWithResource(() => dbDriver.create(url, userName, password))(
        (connection, query) => db.doQuery(connection, query).toList(),
        conn => {
          conn.close()
          None
        })
      .mapConcat(identity)
      .runForeach(println)
    // #mapWithResource
  }
}
