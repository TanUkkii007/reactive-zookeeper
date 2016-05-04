val zookeeperVersion = "3.4.8"

val akkaVersion = "2.4.4"

val commonSettings = Seq(
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-encoding", "UTF-8", "-language:implicitConversions", "-language:postfixOps")
)

lazy val root = (project in file(".")).aggregate(reactiveZookeeper, reactiveZookeeperExample)

lazy val reactiveZookeeper = (project in file("reactive-zookeeper")).settings(
  commonSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.apache.zookeeper" % "zookeeper" % zookeeperVersion,
      "org.slf4j" % "slf4j-log4j12" % "1.7.21",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test"
    )
  )
)

lazy val reactiveZookeeperExample = (project in file("example")).settings(
  commonSettings
).dependsOn(reactiveZookeeper)