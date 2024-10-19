
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.15"

lazy val root = (project in file("."))
  .settings(
    name := "LotteryService"
  )

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

enablePlugins(AkkaGrpcPlugin)

run / fork := true

coverageExcludedPackages := "lottery.service.*"

dependencyOverrides += "org.slf4j" % "slf4j-api" % "1.7.36"
val AkkaVersion = "2.9.6"
val AkkaProjectionVersion = "1.5.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.4.2",

  "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "8.0.0",

  "com.lightbend.akka" %% "akka-projection-jdbc" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,

  "org.scalatest" %% "scalatest" % "3.2.19",
  "org.scalamock" %% "scalamock" % "5.2.0",

  "org.postgresql" % "postgresql" % "42.7.3",

  "com.h2database" % "h2" % "2.2.224",


  "ch.qos.logback" % "logback-classic" % "1.2.13")