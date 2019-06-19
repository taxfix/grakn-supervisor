package taxfix.graknsupervisor

import java.io.File
import java.nio.file.Paths

import com.typesafe.config.ConfigFactory
import org.fluentd.logger.FluentLogger

import scala.sys.process._
import sun.misc.Signal

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

object GraknSupervisor extends App {

  val log = FluentLogger.getLogger("grakn")

  implicit val executionContext = ExecutionContext.global

  val config = ConfigFactory.load()
  val graknHome = config.getString("grakn.home")

  val storageProcess = startStorage()
  val serverProcess = startServer()

  // magic flag to know if are shutting down because of kill signal
  var killed = false

  // Setup the callback for exit signal to shutdown Grakn gracefully
  Signal.handle(new Signal("INT"), (_: Signal) => kill())
  Signal.handle(new Signal("TERM"), (_: Signal) => kill())

  def kill() = {
    killed = true
    log.log("supervisor", "message", s"Received kill signal! Stopping all Grakn processes")
    handleSupervisorExit()
  }


  val storageExitFuture = Future {
    handleProcessExit("Storage", storageProcess.exitValue())
  }

  val serverExitFuture = Future {
    handleProcessExit("Server", serverProcess.exitValue())
  }

  GraknProbe.start().recover({
    case e =>
      log.log("supervisor",  Map[String, Object]("message" -> s"Grakn probe failed: ${e.getMessage}", "severity" -> 500.asInstanceOf[Integer]).asJava)
      killAllProcess()
  })

  // wait util both processes exit
  Await.result(Future.sequence(Seq(storageExitFuture, serverExitFuture)), Duration.Inf)
  if(!killed) handleSupervisorExit()


  def startStorage() = {
    log.log("supervisor", "messa38217ge", "Starting Grakn Storage")
    Process(graknStorageCommand(), new File(graknHome)).run(logger("storage"))
  }

  def startServer() = {
    waitUntilStorageReady()
    log.log("supervisor", "message", "Grakn Storage started SUCCESSFULLY")
    log.log("supervisor", "message", "Starting Grakn Server")
    Process(graknServerCommand(), new File(graknHome)).run(logger("server"))
  }

  def logger(processName: String) = ProcessLogger(
    stdoutLine => log.log(processName, "message", stdoutLine),
    stderrLine => log.log(processName, Map[String, Object]("message" -> stderrLine, "severity" -> 500.asInstanceOf[Integer]).asJava)
  )

  def waitUntilStorageReady(attempts: Int = 0): Unit = {
    if(attempts > 60) {
      log.log("supervisor",  Map[String, Object]("message" -> "Unable to start storage after 5 min. Giving up!", "severity" -> 500.asInstanceOf[Integer]).asJava)
      kill()
    }
    Try(nodeToolCommand().!!) match {
      case Success(output) =>
        if(output.trim == "running") return
      case _ =>
    }
    Thread.sleep(5000)
    waitUntilStorageReady(attempts + 1)
  }

  def logbackConfig = Paths.get("config", "logback.xml").toAbsolutePath

  def graknStorageCommand(): String = {
    val graknConfig = s"$graknHome/server/conf/grakn.properties"
    val serviceLibClasspath = s"$graknHome/server/services/lib/*"
    val cassandraClasspath = s"$graknHome/server/services/cassandra/"
    val configClasspath = s"$graknHome/server/conf/"
    val classpath = s"$serviceLibClasspath:$cassandraClasspath:$configClasspath"
    val storagePidFile = Paths.get(System.getProperty("java.io.tmpdir"), "grakn-storage.pid")
    s"""java ${config.getString("grakn.storage.javaOpts")} -cp "$classpath" -Dgrakn.dir="$graknHome" -Dgrakn.conf="$graknConfig" -Dlogback.configurationFile=$logbackConfig -Dcassandra.logdir=$graknHome/logs -Dcassandra-pidfile=$storagePidFile -Dcassandra.jmx.local.port=7199 -XX:+CrashOnOutOfMemoryError grakn.core.server.GraknStorage"""
  }

  def graknServerCommand() = {
    val graknConfig = s"$graknHome/server/conf/grakn.properties"
    val serviceLibClasspath = s"$graknHome/server/services/lib/*"
    val configClasspath = s"$graknHome/server/conf/"
    val classpath = s"$serviceLibClasspath:$configClasspath"
    val hadoopPath = s"$graknHome/server/services/hadoop"
    val serverPidFile = Paths.get(System.getProperty("java.io.tmpdir"), "grakn-core-server.pid")

    s"""java ${config.getString("grakn.server.javaOpts")} -cp "$classpath" -Dgrakn.dir="$graknHome" -Dgrakn.conf="$graknConfig" -Dgrakn.pidfile=$serverPidFile -Dlogback.configurationFile=$logbackConfig -Dhadoop.home.dir="$hadoopPath" grakn.core.server.Grakn"""
  }

  def nodeToolCommand(): String = {
    val serviceLibClasspath = s"$graknHome/server/services/lib/*"
    val configClasspath = s"$graknHome/server/conf/"
    val classpath = s"$serviceLibClasspath:$configClasspath"
    s"""java -cp "$classpath"  -Dlogback.configurationFile=$logbackConfig org.apache.cassandra.tools.NodeTool statusthrift"""
  }

  def killAllProcess() = {
    if (storageProcess != null && storageProcess.isAlive()) storageProcess.destroy()
    if (serverProcess != null && serverProcess.isAlive()) serverProcess.destroy()
  }

  def handleSupervisorExit() = {
    killAllProcess()
    if(!killed) log.log("supervisor", "message", "Exiting Grakn Supervisor")
    if (log.isConnected) log.close()
  }

  def handleProcessExit(processName: String, statusCode: Int) = {
    if(!killed) log.log("supervisor", "message", s"Grakn $processName exit with status code: $statusCode")
    killAllProcess()
  }

}
