name := "grakn-supervisor"

version := "0.1"

scalaVersion := "2.13.0"

resolvers +=
  "repo.grakn.ai" at "https://repo.grakn.ai/repository/maven/"

checksums in update := Nil

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.2",
  "org.fluentd" % "fluent-logger" % "0.3.4",
  "io.grakn.client" % "api" % "1.5.2"
)

enablePlugins(JavaAppPackaging)
mainClass in Compile := Some("taxfix.graknsupervisor.GraknSupervisor")

// Add macro binary to the resulting package
mappings in Universal += file("config/logback.xml") -> "config/logback.xml"
bashScriptExtraDefines += """addJava "-Duser.dir=${app_home}/..""""