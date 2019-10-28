lazy val kindProjectorVersion = "0.10.3"
lazy val ocsVersion           = "2020001.1.0"

inThisBuild(Seq(
  resolvers += "Gemini Repository" at "https://github.com/gemini-hlsw/maven-repo/raw/master/releases",
  addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorVersion),
))

lazy val core = project
  .in(file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "itc-cli-core",
    libraryDependencies ++= Seq(
      "edu.gemini.ocs" %% "edu-gemini-model-p1" % ocsVersion
      )
  )

