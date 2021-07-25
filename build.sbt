val scalaVer = "2.13.3"
val zioVersion = "1.0.9"
val zioConfig = "1.0.6"
val javeCore = "3.1.1"
val zHttp = "1.0.0.0-RC16"

lazy val compileDependencies = Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-macros" % zioVersion,
  "dev.zio" %% "zio-config" % zioConfig,
  "dev.zio" %% "zio-config-typesafe" % zioConfig,
  "dev.zio" %% "zio-config-magnolia" % zioConfig,
  "dev.zio" %% "zio-logging" % "0.5.11",
  "ws.schild" % "jave-core" % javeCore,
  "ws.schild" % "jave-all-deps" % javeCore,
  "io.d11" %% "zhttp" % zHttp,

) map (_ % Compile)

lazy val testDependencies = Seq(
  "dev.zio" %% "zio-test" % zioVersion,
  "dev.zio" %% "zio-test-sbt" % zioVersion
) map (_ % Test)

lazy val settings = Seq(
  name := "zio-webm2mp4",
  version := "0.0.1",
  scalaVersion := scalaVer,
  scalacOptions += "-Ymacro-annotations",
  libraryDependencies ++= compileDependencies ++ testDependencies,
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
  mainClass in(Compile, run) := Some("com.unknownnpc.webm2mp4.WebServer"),
  sourceDirectories in (Compile, TwirlKeys.compileTemplates) +=
   ((resourceDirectory in Compile).value / "web" / "templates")

)

lazy val root = (project in file("."))
  .settings(settings)
    .enablePlugins(SbtTwirl)
