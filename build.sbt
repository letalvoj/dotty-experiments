organization in ThisBuild := "io.circe"

val scalaCheckDependencies = Seq(
  "org.scalacheck" %% "scalacheck" % "1.14.0"
)

val disciplineDependencies = Seq(
  "org.typelevel" %% "discipline-core" % "1.0.0"
)

val testDependencies = Seq(
  "io.monix" %% "minitest" % "2.5.0",
  "io.monix" %% "minitest-laws" % "2.5.0"
)

val baseSettings = Seq(
  scalaVersion := "0.18.0-bin-20190812-0ebbcff-NIGHTLY",
  scalacOptions ++= Seq( /*"-language:strictEquality",*/ "-Ykind-projector"),
  libraryDependencies ++= testDependencies.map(_ % Test).map(_.withDottyCompat(scalaVersion.value)),
  testFrameworks += new TestFramework("minitest.runner.Framework")
)

val lawsSettings = Seq(
  libraryDependencies ++= disciplineDependencies.map(_.withDottyCompat(scalaVersion.value))
)

val root =
  project
    .in(file("."))
    .settings(baseSettings)
    .aggregate(catsKernel,
               catsKernelLaws,
               catsCore,
               catsLaws,
               catsTests,
               circeNumbersTesting,
               circeNumbers,
               circeCore,
               circeRs,
               circeJawn,
               circeLiteral,
               circeTesting,
               circeTests)
    .dependsOn(circeJawn, circeLiteral, circeTesting)

lazy val catsKernel = project
  .in(file("dotty-cats/kernel"))
  .settings(baseSettings)
  .settings(sourceGenerators in Compile += (sourceManaged in Compile).map(CatsKernelBoilerplate.gen).taskValue)

lazy val catsKernelLaws = project
  .in(file("dotty-cats/kernel-laws"))
  .settings(baseSettings)
  .settings(lawsSettings)
  .dependsOn(catsKernel)

lazy val catsCore = project
  .in(file("dotty-cats/core"))
  .settings(baseSettings)
  .settings(sourceGenerators in Compile += (sourceManaged in Compile).map(CatsBoilerplate.gen).taskValue)
  .dependsOn(catsKernel)

lazy val catsLaws = project
  .in(file("dotty-cats/laws"))
  .settings(baseSettings)
  .settings(lawsSettings)
  .dependsOn(catsCore, catsKernelLaws)

lazy val catsTests = project
  .in(file("dotty-cats/tests"))
  .settings(baseSettings)
  .settings(
    libraryDependencies ++= testDependencies.map(_.withDottyCompat(scalaVersion.value))
  )
  .dependsOn(catsLaws, catsKernelLaws)

lazy val circeNumbersTesting = project
  .in(file("dotty-circe/numbers-testing"))
  .settings(baseSettings)
  .settings(libraryDependencies ++= scalaCheckDependencies.map(_.withDottyCompat(scalaVersion.value)))

lazy val circeNumbers = project
  .in(file("dotty-circe/numbers"))
  .settings(baseSettings)
  .dependsOn(circeNumbersTesting % Test)

lazy val circeCore = project
  .in(file("dotty-circe/core"))
  .settings(baseSettings)
  .settings(sourceGenerators in Compile += (sourceManaged in Compile).map(CirceBoilerplate.gen).taskValue)
  .dependsOn(circeNumbers, catsCore)

lazy val circeRs = project
  .in(file("dotty-circe/rs"))
  .settings(baseSettings)
  .dependsOn(circeCore)

lazy val circeJawn = project
  .in(file("dotty-circe/jawn"))
  .settings(baseSettings)
  .settings(
    libraryDependencies += ("org.typelevel" %% "jawn-parser" % "0.14.2").withDottyCompat(scalaVersion.value)
  )
  .dependsOn(circeCore)

lazy val circeLiteral = project
  .in(file("dotty-circe/literal"))
  .settings(baseSettings)
  .dependsOn(circeCore, circeJawn)

lazy val circeTesting = project
  .in(file("dotty-circe/testing"))
  .settings(baseSettings)
  .settings(libraryDependencies ++= scalaCheckDependencies.map(_.withDottyCompat(scalaVersion.value)))
  .dependsOn(circeNumbersTesting, circeRs, catsLaws)

lazy val circeTests = project
  .in(file("dotty-circe/tests"))
  .settings(baseSettings)
  .settings(
    libraryDependencies ++= testDependencies.map(_.withDottyCompat(scalaVersion.value))
  )
  .dependsOn(circeTesting, circeJawn)
