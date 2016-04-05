import sbt._
import Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object FPInScalaBuild extends Build {

  val opts = Project.defaultSettings ++ Seq(
    scalaVersion := "2.11.7",
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
//    scalacOptions ++= Seq(
//      "-deprecation",
//      "-unchecked"),
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Bintray  Repository" at "http://dl.bintray.com/scalaz/releases/"
  )
  
 
  lazy val root =
    Project(id = "fpinscala",
            base = file("."),
            settings = opts ++ Seq(
              onLoadMessage ~= (_ + nio2check())
            ))
      .aggregate (chapterCode, exercises, answers, tests)
      .dependsOn(tests % "compile->test")
  lazy val chapterCode =
    Project(id = "chapter-code",
            base = file("chaptercode"),
            settings = opts)
  lazy val exercises =
    Project(id = "exercises",
            base = file("exercises"),
            settings = opts)
  lazy val answers =
    Project(id = "answers",
            base = file("answers"),
            settings = opts)
            
  lazy val moduleToTest = if(System.getProperty("test") == "answers") answers else exercises
            
  lazy val tests =
    Project(id = "tests",
            base = file("tests"),
            settings = opts ++ Seq(
	      scalacOptions in Test ++= Seq("-Yrangepos"),
	      libraryDependencies ++= Seq(
		"org.specs2" %% "specs2-core" % "3.5" % "test",
		"org.specs2" %% "specs2-junit" % "3.5"
	      ),
	      EclipseKeys.skipParents in ThisBuild := false,
	      resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
	    ))
       .dependsOn(moduleToTest).aggregate(moduleToTest)

  def nio2check(): String = {
    val cls = "java.nio.channels.AsynchronousFileChannel"
    try {Class.forName(cls); ""}
    catch {case _: ClassNotFoundException =>
      ("\nWARNING: JSR-203 \"NIO.2\" (" + cls + ") not found.\n" +
       "You are probably running Java < 1.7; answers will not compile.\n" +
       "You seem to be running " + System.getProperty("java.version") + ".\n" +
       "Try `project exercises' before compile, or upgrading your JDK.")
    }
  }
}
