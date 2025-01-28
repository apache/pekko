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

object VerifyJDK9Classes {
  import sbt._
  import sbt.Keys._

  lazy val settings: Seq[Setting[_]] = inConfig(Compile) {
    Seq {
      sourceGenerators += {
        generateAndWriteScalaCLIScript(target)
      }
    }
  }

  def generateAndWriteScalaCLIScript(dir: SettingKey[File]): Def.Initialize[Task[Seq[sbt.File]]] =
    Def.task[Seq[File]] {
      val scalaVer = scalaVersion.value
      val script = generateScalaCLIScript(version.value, scalaVer)
      val file = dir.value / "scala-cli" / s"VerifyJDK9Classes-${scalaVer}.sc"
      val content = script.stripMargin.format(version.value)
      if (!file.exists || IO.read(file) != content) IO.write(file, content)
      // the generated file is not used.
      Nil
    }

  private def generateScalaCLIScript(version: String, scalaBinaryVersion: String): String =
    s"""
      |//> using scala ${scalaBinaryVersion}
      |//> using dep "org.apache.pekko::pekko-stream:${version}"
      |
      |/*
      | * Licensed to the Apache Software Foundation (ASF) under one or more
      | * contributor license agreements. See the NOTICE file distributed with
      | * this work for additional information regarding copyright ownership.
      | * The ASF licenses this file to You under the Apache License, Version 2.0
      | * (the "License"); you may not use this file except in compliance with
      | * the License. You may obtain a copy of the License at
      | *
      | *    http://www.apache.org/licenses/LICENSE-2.0
      | *
      | * Unless required by applicable law or agreed to in writing, software
      | * distributed under the License is distributed on an "AS IS" BASIS,
      | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      | * See the License for the specific language governing permissions and
      | * limitations under the License.
      | */
      |
      |object VerifyJDK9Classes {
      |  def main(args: Array[String]): Unit = {
      |    import org.apache.pekko.actor.ActorSystem
      |    import org.apache.pekko.stream.scaladsl.{ JavaFlowSupport, Source }
      |
      |    import java.lang.System.exit
      |    import scala.concurrent.Await
      |    import scala.concurrent.duration.DurationInt
      |    implicit val system: ActorSystem = ActorSystem.create("test")
      |    val future = Source(1 to 3).runWith(
      |      JavaFlowSupport.Sink.asPublisher[Int](fanout = false).mapMaterializedValue { p =>
      |        JavaFlowSupport.Source.fromPublisher(p).runFold(0)(_ + _)
      |      })
      |
      |    val result = Await.result(future, 3.seconds)
      |    println(s"Result:" + result)
      |    system.terminate()
      |    exit(if (result == 6) 0 else 1)
      |  }
      |}
      |
      |""".stripMargin
}
