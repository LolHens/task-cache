name := "task-cache"
version := "0.0.0"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "io.monix" %% "monix" % "3.0.0-RC1"
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")
