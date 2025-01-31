import Dependencies._
import sbt.Package.{ MainClass, ManifestAttributes }

inThisBuild(
  List(
    scalaVersion := "2.13.6",
    organization := "io.scalac",
    homepage := Some(url("https://github.com/ScalaConsultants/mesmer-akka-agent")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jczuchnowski",
        "Jakub Czuchnowski",
        "jakub.czuchnowski@gmail.com",
        url("https://github.com/jczuchnowski")
      ),
      Developer(
        "worekleszczy",
        "Piotr Jósiak",
        "piotr.josiak@gmail.com",
        url("https://github.com/worekleszczy")
      )
    ),
    scalacOptions ++= Seq("-deprecation", "-feature"),
    semanticdbEnabled := true,
    scalacOptions += "-Wunused:imports",
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0",
    scalafixScalaBinaryVersion := "2.13"
  )
)

addCommandAlias("fmt", "scalafmtAll; scalafixAll")
addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll")

lazy val all = (project in file("."))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name := "mesmer-all",
    publish / skip := true
  )
  .aggregate(extension, agent, example, core)

lazy val core = (project in file("core"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name := "mesmer-akka-core",
    libraryDependencies ++= {
      akka ++
      openTelemetryApi ++
      openTelemetryApiMetrics ++
      scalatest ++
      akkaTestkit
    }
  )

lazy val extension = (project in file("extension"))
  .enablePlugins(MultiJvmPlugin)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .configs(MultiJvm)
  .settings(
    Test / parallelExecution := true,
    name := "mesmer-akka-extension",
    libraryDependencies ++= {
      akka ++
      openTelemetryApi ++
      openTelemetryApiMetrics ++
      akkaTestkit ++
      scalatest ++
      akkaMultiNodeTestKit ++
      logback.map(_ % Test)
    }
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val agent = (project in file("agent"))
  .settings(
    name := "mesmer-akka-agent",
    libraryDependencies ++= {
      akka.map(_    % "provided") ++
      logback.map(_ % Test) ++
      byteBuddy ++
      scalatest ++
      akkaTestkit ++
      slf4jApi ++
      reflection(scalaVersion.value)
    },
    Compile / mainClass := Some("io.scalac.mesmer.agent.Boot"),
    Compile / packageBin / packageOptions := {
      (Compile / packageBin / packageOptions).value.map {
        case MainClass(mainClassName) =>
          ManifestAttributes(List("Premain-Class" -> mainClassName): _*)
        case other => other
      }
    },
    assembly / test := {},
    assembly / assemblyJarName := "mesmer-akka-agent.jar",
    assembly / assemblyOption ~= { _.withIncludeScala(false) },
    assemblyMergeStrategySettings,
    Test / fork := true,
    Test / parallelExecution := true,
    Test / testGrouping := ((Test / testGrouping).value flatMap { group =>
      group.tests.map { test =>
        Tests.Group(name = test.name, tests = Seq(test), runPolicy = group.runPolicy)
      }
    }),
    Test / testOnly / testGrouping := (Test/ testGrouping).value
  )
  .dependsOn(
    core % "compile->compile;test->test"
  )

lazy val example = (project in file("example"))
  .enablePlugins(JavaAppPackaging, DockerPlugin, UniversalPlugin)
  .settings(
    name := "mesmer-akka-example",
    publish / skip := true,
    libraryDependencies ++= {
      akka ++
      scalatest ++
      akkaTestkit ++
      akkaPersistance ++
      logback ++
      exampleDependencies
    },
    assemblyMergeStrategySettings,
    assembly / mainClass := Some("io.scalac.Boot"),
    assembly / assemblyJarName := "mesmer-akka-example.jar",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    run / fork := true,
    run / javaOptions ++= {
      val properties = System.getProperties

      import scala.collection.JavaConverters._
      for {
        (key, value) <- properties.asScala.toList if value.nonEmpty
      } yield s"-D$key=$value"
    },
    commands += runWithAgent,
    Universal / mappings += {
      val jar = (agent / assembly).value
      jar -> "mesmer.agent.jar"
    },
    dockerEnvVars := {
      Map("JAVA_OPTS" -> s"-javaagent:/opt/docker/mesmer.agent.jar -Dconfig.resource=dev/application.conf")
    },
    dockerExposedPorts ++= Seq(8080),
    Docker / dockerRepository := {
      sys.env.get("DOCKER_REPO")
    },
    Docker / dockerRepository := {
      sys.env.get("DOCKER_USER")
    },
    Docker / packageName := {
      val old = (Docker / packageName).value
      sys.env.getOrElse("DOCKER_PACKAGE_NAME", old)
    },
    Docker / version := version.value.takeWhile(_ != '+'), //drop the snapshot part (eg. 0.1.1+553-b4ee8e44-SNAPSHOT)
    dockerUpdateLatest := true
  )
  .dependsOn(extension)

lazy val assemblyMergeStrategySettings = assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", _ @_*)           => MergeStrategy.concat
  case PathList("META-INF", _ @_*)                       => MergeStrategy.discard
  case PathList("reference.conf")                        => MergeStrategy.concat
  case PathList("jackson-annotations-2.10.3.jar", _ @_*) => MergeStrategy.last
  case PathList("jackson-core-2.10.3.jar", _ @_*)        => MergeStrategy.last
  case PathList("jackson-databind-2.10.3.jar", _ @_*)    => MergeStrategy.last
  case PathList("jackson-dataformat-cbor-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-datatype-jdk8-2.10.3.jar", _ @_*) => MergeStrategy.last
  case PathList("jackson-datatype-jsr310-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-module-parameter-names-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-module-paranamer-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case _ => MergeStrategy.first
}

lazy val benchmark = (project in file("benchmark"))
  .enablePlugins(JmhPlugin)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings {
    name := "akka-monitoring-benchmark"
  }
  .dependsOn(extension)

def runWithAgent = Command.command("runWithAgent") { state =>
  val extracted = Project extract state
  val newState =
    extracted.appendWithSession(
      Seq(
        run / javaOptions ++= Seq(
          "-Denv=local",
          "-Dconfig.resource=local/application.conf",
          s"-javaagent:${(agent / assembly).value.absolutePath}"
        )
      ),
      state
    )
  val (s, _) =
    Project.extract(newState).runInputTask(Compile / run, "", newState)
  s
}
