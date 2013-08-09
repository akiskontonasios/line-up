name := "line-up"

version := "0.1"

scalaVersion := "2.10.0"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"

parallelExecution in Test := false

javacOptions ++= Seq("-Xlint:unchecked", "-target", "1.5")

packageOptions in (Compile, packageBin) += Package.ManifestAttributes(java.util.jar.Attributes.Name.MAIN_CLASS -> "lineup.Demo")
