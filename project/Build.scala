package psp
package build

import scala.Predef.{ conforms => _ }
import sbt._, Keys._, psp.libsbt._, Deps._
import psp.std._

object Build extends sbt.Build {
  // Assign settings logger here during initialization so it won't be garbage collected.
  // sbt otherwise will throw an exception if it is used after project loading.
  private[this] var settingsLogger: Logger = _

  def slog       = settingsLogger
  def commonArgs = wordSeq("-Yno-predef -Yno-adapted-args -unchecked")
  def stdArgs    = "-Yno-imports" +: commonArgs
  def replArgs   = "-language:_" +: commonArgs
  def ammonite   =  "com.lihaoyi" %% "ammonite-repl" % "0.2.7"

  def rootResourceDir: SettingOf[File] = resourceDirectory in Compile in LocalRootProject
  def subprojects                      = List[sbt.Project](api, dmz, std, pio, dev, jvm, scalac)
  def classpathDeps                    = convertSeq(subprojects): List[ClasspathDep[ProjectReference]]
  def projectRefs                      = convertSeq(subprojects): List[ProjectReference]

  def parserDep = Def setting {
    scalaBinaryVersion.value match {
      case "2.10" => Seq()
      case _      => Seq(scalaParsers)
    }
  }
  implicit class ProjectOps(val p: Project) {
    def setup(): Project             = p.alsoToolsJar also commonSettings(p) also (name := "psp-" + p.id)
    def setup(text: String): Project = setup() also (description := text)
    def usesCompiler                 = p settings (libraryDependencies += Deps.scalaCompiler.value)
    def usesReflect                  = p settings (libraryDependencies += Deps.scalaReflect.value)
    def usesParsers                  = p settings (libraryDependencies ++= parserDep.value)
    def helper(): Project            = p.noArtifacts setup "helper project" dependsOn (classpathDeps: _*)
    def noSources                    = p in file("target/helper/" + p.id)
  }

  private def commonSettings(p: Project) = standardSettings ++ Seq(
            resolvers +=  Resolver.mavenLocal,
              version :=  sbtBuildProps.buildVersion,
         scalaVersion :=  scalaVersionLatest,
   crossScalaVersions :=  Seq(scalaVersion.value),
             licenses :=  pspLicenses,
         organization :=  pspOrg,
        scalacOptions ++= scalacOptionsFor(scalaBinaryVersion.value) ++ stdArgs,
            maxErrors :=  10,
     triggeredMessage :=  Watched.clearWhenTriggered,
    publishMavenStyle :=  true
  ) ++ (
    if (p.id == "root") Nil
    else Seq(target <<= javaCrossTarget(p.id))
  )

  private def loudLog(msg: String)(f: String => Unit): Unit = {
    f("")
    f("<*> " + msg)
    f("")
  }

  def aggregateIn[A](key: TaskKey[A], project: Project) = {
    def label = key.key.label
    def nameOf(r: Reference): String = r match {
      case LocalProject(id) => id
      case _                => "" + r
    }
    val aggregated = (project: ProjectDefinition[ProjectReference]).aggregate map nameOf mkString ", "

    Command.command(label) { state =>
      loudLog(show"$label is aggregated in [ $aggregated ]")(s => state.log.info(s))
      state runAll (key in project)
    }
  }

  lazy val root = project.root.setup dependsOn (classpathDeps: _*) settings (
    initialize := { this.settingsLogger = (sLog in GlobalScope).value ; initialize.value },
    commands ++= Seq(
      aggregateIn(clean, compileOnly),
      aggregateIn(compile in Compile, compileOnly),
      aggregateIn(test, testOnly),
      aggregateIn(key.packageTask, publishOnly),
      aggregateIn(publish, publishOnly),
      aggregateIn(publishLocal, publishOnly),
      aggregateIn(publishM2, publishOnly)
    ),
    console in Compile <<=  console in Compile in consoleOnly,
       console in Test <<=  console in Test in consoleOnly,
          watchSources <++= sources in Test in testOnly
  )

  lazy val api    = project setup "psp's non-standard api"
  lazy val dmz    = project setup "psp's non-standard dmz"
  lazy val std    = project setup "psp's non-standard standard library" dependsOn (api, dmz) also (guava, spire, jsr305, ammonite)
  lazy val pio    = project setup "psp's non-standard io library" dependsOn std
  lazy val jvm    = project.usesCompiler.usesParsers setup "psp's non-standard jvm code" dependsOn pio
  lazy val dev    = project setup "psp's non-standard unstable code" dependsOn std also (javaSysMon, squants, okhttp)
  lazy val scalac = project.usesCompiler setup "psp's non-standard scalac-requiring code" dependsOn (pio, dev)

  lazy val publishOnly = project.helper.noSources aggregate (api, dmz, std, pio)
  lazy val compileOnly = project.helper.noSources aggregate (projectRefs: _*)
  lazy val testOnly    = project.helper aggregate (projectRefs: _*) settings (
          testOptions in Test  +=  Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "1"),
    parallelExecution in Test  :=  false,
                  logBuffered  :=  false,
          libraryDependencies <++= testDependencies,
                         test  :=  (run in Test toTask "").value
  )

  // A console project which pulls in misc additional dependencies currently being explored.
  // Removing all scalac options except the ones listed here, to eliminate all the warnings
  // repl startup code in resources/initialCommands.scala
  lazy val consoleOnly = (
    project.helper.usesCompiler.alsoToolsJar
    dependsOn (testOnly % "test->test")
    dependsOn (classpathDeps: _*)
    also (guava, jsr305, ammonite) settings (
                      libraryDependencies <+=  scalaCompiler,
      scalacOptions in (Compile, console)  :=  replArgs,
         scalacOptions in (Test, console)  :=  replArgs,
                             key.initRepl <+=  resourceDirectory in Compile mapValue (d => IO.read(d / "initialCommands.scala")),
                     key.initRepl in Test  +=  "\nimport org.scalacheck._, Prop._, Gen._\nimport psp.tests._"
    )
  )

  def testDependencies = Def setting Seq(
    Deps.scalaReflect.value,
    scalacheck.copy(configurations = None)
  )
}
