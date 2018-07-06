name := "task-cache"
version := "0.0.0"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % "2.3.3",
  "io.monix" %% "monix" % "3.0.0-RC1",
  "com.lihaoyi" %% "upickle" % "0.6.6"
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")
