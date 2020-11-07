Global / cancelable := true
publish / skip := true // in root

lazy val commonSettings: Seq[Setting[_]] =
  ScalaModulePlugin.scalaModuleSettings ++ Seq(
    Compile / compile / scalacOptions --= (if (isDotty.value) Seq("-Xlint")
                                           else Seq()),
    Compile / compile / scalacOptions ++= (if (isDotty.value) Seq()
                                           else Seq("-Werror")),
  )

lazy val core = project.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "scala-parallel-collections",
    // don't run Dottydoc, it errors and isn't needed anyway.
    // but we leave `publishArtifact` set to true, otherwise Sonatype won't let us publish
    Compile / doc / sources := (if (isDotty.value) Seq() else (Compile / doc / sources).value),
    scalaModuleMimaPreviousVersion := Some("1.0.0-RC1").filterNot(_ => isDotty.value),
  )

lazy val junit = project.in(file("junit"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,
    libraryDependencies += "junit" % "junit" % "4.13.1" % Test,
    // for javax.xml.bind.DatatypeConverter, used in SerializationStabilityTest
    libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.1" % Test,
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v"),
    Test / fork := true,
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

lazy val scalacheck = project.in(file("scalacheck"))
  .settings(commonSettings)
  .settings(
    // as per discussion at https://github.com/lampepfl/dotty/pull/10179 ,
    // the Dotty community build is on 1.14.1-SNAPSHOT for now
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.1-SNAPSHOT" withDottyCompat(scalaVersion.value),
    Test / fork := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-workers", "1", "-minSize", "0", "-maxSize", "4000", "-minSuccessfulTests", "5"),
    publish / skip := true
  ).dependsOn(core)

lazy val testmacros = project.in(file("testmacros"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += (
      if (isDotty.value)
        scalaOrganization.value %% "scala3-compiler" % scalaVersion.value
      else
        scalaOrganization.value % "scala-compiler" % scalaVersion.value
    ),
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
