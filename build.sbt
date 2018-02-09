val zookeeperVersion = "3.4.8"

val akkaVersion = "2.4.4"

val commonSettings = Seq(
  organization := "github.com/TanUkkii007",
  homepage := Some(url("https://github.com/TanUkkii007/reactive-zookeeper")),
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-encoding", "UTF-8", "-language:implicitConversions", "-language:postfixOps"),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
)

val noPublishSettings = Seq(
  publish := (),
  publishArtifact in Compile := false
)

val publishSettings = Seq(
  releaseCrossBuild := true
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .aggregate(reactiveZookeeper, reactiveZookeeperExample)

lazy val reactiveZookeeper = (project in file("reactive-zookeeper")).settings(
  name := "reactive-zookeeper",
  commonSettings ++ publishSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.apache.zookeeper" % "zookeeper" % zookeeperVersion % "provided",
      "org.slf4j" % "slf4j-log4j12" % "1.7.21",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test",
      "org.apache.curator" % "curator-test" % "2.11.0" % "test"
    ),
    BintrayPlugin.autoImport.bintrayPackage := "reactive-zookeeper"
  )
).enablePlugins(BintrayPlugin)

lazy val reactiveZookeeperExample = (project in file("example"))
  .settings(noPublishSettings)
  .settings(
  commonSettings ++ Seq(
    name := "reactive-zookeeper-example",
    libraryDependencies ++= Seq(
      "org.apache.zookeeper" % "zookeeper" % zookeeperVersion
    )
  )
).dependsOn(reactiveZookeeper)
