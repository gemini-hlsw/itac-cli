
inThisBuild(Seq(
  scalaVersion := "2.12.10",
  resolvers    += "Gemini Repository" at "https://github.com/gemini-hlsw/maven-repo/raw/master/releases",
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
))

lazy val engine = project
  .in(file("modules/engine"))
  .settings(
    name := "itc-cli-engine",
    libraryDependencies ++= Seq(
      "edu.gemini.ocs"          %% "edu-gemini-model-p1" % "2020001.1.0",
      "edu.gemini.ocs"          %% "edu-gemini-shared-skyobject" % "2019101.1.4",
      "edu.gemini.ocs"          %% "edu-gemini-util-skycalc"     % "2019101.1.4",
      "org.scala-lang.modules"  %% "scala-xml"           % "2.0.0-M1",
      "org.slf4j"                % "slf4j-api"           % "1.7.28",
      "com.novocode"             % "junit-interface"     % "0.11"    % "test",
      "junit"                    % "junit"               % "4.12"    % "test",
      "org.mockito"              % "mockito-all"         % "1.10.19" % "test",
    ),
    Test / scalacOptions := Nil, // don't worry about warnings in tests right now
  )

lazy val main = project
  .in(file("modules/main"))
  .dependsOn(engine)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "itc-cli-main",
    libraryDependencies ++= Seq(
      "com.monovore"      %% "decline-effect"         % "1.0.0",
      "com.monovore"      %% "decline"                % "1.0.0",
      "io.chrisdavenport" %% "log4cats-slf4j"         % "1.0.1",
      "io.circe"          %% "circe-core"             % "0.11.1",
      "io.circe"          %% "circe-generic"          % "0.11.1",
      "io.circe"          %% "circe-parser"           % "0.11.1",
      "io.circe"          %% "circe-yaml"             % "0.10.0",
      "org.slf4j"          % "slf4j-simple"           % "1.7.28",
      "org.tpolecat"      %% "atto-core"              % "0.7.1",
      "org.typelevel"     %% "cats-effect"            % "2.0.0",
      "org.typelevel"     %% "cats-testkit"           % "2.0.0"     % "test",
      "org.typelevel"     %% "cats-testkit-scalatest" % "1.0.0-RC1" % "test",
    )
  )

