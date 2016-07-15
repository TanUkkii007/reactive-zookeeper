val zookeeperVersion = "3.4.8"

val akkaVersion = "2.4.4"

val commonSettings = Seq(
  version := "0.0.2",
  organization := "github.com/TanUkkii007",
  homepage := Some(url("https://github.com/TanUkkii007/reactive-zookeeper")),
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-encoding", "UTF-8", "-language:implicitConversions", "-language:postfixOps"),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
)

lazy val root = (project in file(".")).aggregate(reactiveZookeeper, reactiveZookeeperExample)

lazy val reactiveZookeeper = (project in file("reactive-zookeeper")).settings(
  commonSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.apache.zookeeper" % "zookeeper" % zookeeperVersion % "provided",
      "org.slf4j" % "slf4j-log4j12" % "1.7.21",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test",
      "commons-io" % "commons-io" % "2.4" % "test"
    ),
    BintrayPlugin.autoImport.bintrayPackage := "reactive-zookeeper"
  )
).enablePlugins(BintrayPlugin)

lazy val reactiveZookeeperExample = (project in file("example")).settings(
  commonSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.apache.zookeeper" % "zookeeper" % zookeeperVersion
    )
  )
).dependsOn(reactiveZookeeper)