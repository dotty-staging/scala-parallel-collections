val scalaVersions =  Seq("2.13.16", "3.3.6")
val defaultScalaVersion = scalaVersions.head

// When defining JVM / Scala Native matrix we don't want duplicated projects for Scala 2/3
val matrixScalaVersions = Seq(defaultScalaVersion)

ThisBuild / crossScalaVersions := scalaVersions
ThisBuild / scalaVersion := defaultScalaVersion

// Global / concurrentRestrictions += Tags.limit(NativeTags.Link, 1)
Global / cancelable := true
publish / skip := true // in root

lazy val commonSettings: Seq[Setting[_]] =
  Seq(scalaModuleAutomaticModuleName := Some("scala.collection.parallel")) ++
  ScalaModulePlugin.scalaModuleSettings ++ Seq(
    // versionPolicyIntention := Compatibility.BinaryCompatible,
    crossScalaVersions := scalaVersions,
    Compile / compile / scalacOptions --= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq("-Xlint")
      case _            => Seq()
    }),
    Compile / compile / scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq()
      case _            => Seq("-Werror"),
    }),
  )

lazy val testNativeSettings: Seq[Setting[_]] = Seq(
    // Required by Scala Native testing infrastructure
    Test / fork := false,
)

lazy val core = projectMatrix.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "scala-parallel-collections",
    // Compile / doc / autoAPIMappings := true,
    // don't run Dottydoc, it errors and isn't needed anyway.
    // but we leave `publishArtifact` set to true, otherwise Sonatype won't let us publish
    Compile / doc / sources := (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((0 | 3, _)) => Seq()
      case _ => (Compile / doc / sources).value
    }),
  )
  .jvmPlatform(matrixScalaVersions)
  // .nativePlatform(matrixScalaVersions, settings = testNativeSettings ++ Seq(
  //   versionPolicyPreviousArtifacts := Nil, // TODO: not yet published
  //   mimaPreviousArtifacts := Set.empty
  // ))

lazy val junit = projectMatrix.in(file("junit"))
  .settings(commonSettings)
  .settings(
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v"),
    publish / skip := true,
    // as per remarks at https://github.com/lampepfl/dotty/pull/10179 ,
    // the Dotty community build uses sbt 1.3.8 to build all projects;
    // this can be removed once we move to sbt 1.4, which supports
    // scala-2 and scala-3 source directories directly
    Test / unmanagedSourceDirectories += {
      val major = CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((0 | 3, _)) => "3"
        case _ => "2"
      }
      baseDirectory.value / "src" / "test" / s"scala-$major"
    },
  ).dependsOn(testmacros, core)
  .jvmPlatform(matrixScalaVersions,
    settings = Seq(
      libraryDependencies += "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
      libraryDependencies += "junit" % "junit" % "4.13.2" % Test,
        // for javax.xml.bind.DatatypeConverter, used in SerializationStabilityTest
      libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.1" % Test,
      Test / fork := true,
    )
  )
  // .nativePlatform(matrixScalaVersions,
  //   axisValues = Nil,
  //   configure = _
  //     .enablePlugins(ScalaNativeJUnitPlugin)
  //     .settings(
  //       Test/unmanagedSources/excludeFilter ~= { _ ||
  //         "SerializationTest.scala" || // requires ObjectOutputStream
  //         "SerializationStability.scala" || // requires jaxb-api
  //         "SerializationStabilityBase.scala" ||
  //         "SerializationStabilityTest.scala"
  //       },
  //       Test / fork := false
  //     )
  // )

lazy val scalacheck = projectMatrix.in(file("scalacheck"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.18.1",
    Test / fork := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-workers", "1", "-minSize", "0", "-maxSize", "4000", "-minSuccessfulTests", "5"),
    publish / skip := true
  )
  .dependsOn(core)
  .jvmPlatform(matrixScalaVersions,
    settings = Seq(
      Test / fork := true
    )
  )
  // .nativePlatform(matrixScalaVersions, settings = testNativeSettings)

lazy val testmacros = projectMatrix.in(file("testmacros"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Nil
      case _            => List(scalaOrganization.value % "scala-compiler" % scalaVersion.value)
    }),
    publish / skip := true,
    // Dotty community build: see above remarks about sbt version
    Compile / unmanagedSourceDirectories += {
      val major = CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((0 | 3, _)) => "3"
        case _ => "2"
      }
      baseDirectory.value / "src" / "main" / s"scala-$major"
    },
  )
  .jvmPlatform(matrixScalaVersions)
  // .nativePlatform(matrixScalaVersions, settings = testNativeSettings)

commands += Command.single("setScalaVersion") { (state, arg) =>
  val command = arg match {
    case "3.next" => s"++${GetScala3Next.get()}!"
    case _        => s"++$arg"
  }
  command :: state
}

import sbt.internal.{ProjectMatrix, ProjectFinder}
def testPlatformCommand(name: String, selector: ProjectMatrix => ProjectFinder): Command =
  Command.command(name) { state =>
    List(junit, scalacheck, testmacros)
    .flatMap(selector(_).get)
    .map{ project => s"${project.id}/test"}
    .toList ::: state
  }

// commands += testPlatformCommand("testNative", _.native)
commands += testPlatformCommand("testJVM", _.jvm)
