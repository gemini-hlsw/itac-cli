
inThisBuild(Seq(
  scalaVersion := "2.12.10",
  resolvers    += "Gemini Repository" at "https://github.com/gemini-hlsw/maven-repo/raw/master/releases",
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
))

lazy val engine = project
  .in(file("modules/engine"))
  .disablePlugins(TpolecatPlugin)
  .settings(
    name := "itc-cli-engine",
    libraryDependencies ++= Seq(
      "edu.gemini.ocs"          %% "edu-gemini-model-p1"         % "2020001.1.0",
      "edu.gemini.ocs"          %% "edu-gemini-util-skycalc"     % "2019101.1.4",
      "edu.gemini.ocs"          %% "edu-gemini-shared-skyobject" % "2019101.1.4",
      "org.scala-lang.modules"  %% "scala-xml"                   % "2.0.0-M1",
      "log4j"                    % "log4j"                       % "1.2.17",
      "junit"                    % "junit"                       % "4.12"    % "test",
      "org.mockito"              % "mockito-all"                 % "1.10.19" % "test",
      "com.novocode"             % "junit-interface"             % "0.11"    % "test"
    )
  )

lazy val main = project
  .in(file("modules/main"))
  .dependsOn(engine)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "itc-cli-main",
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "cats-effect"         % "2.0.0",
      "com.monovore"   %% "decline"             % "1.0.0",
      "com.monovore"   %% "decline-effect"      % "1.0.0"
    )
  )

