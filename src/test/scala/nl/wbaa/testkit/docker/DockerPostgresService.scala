package nl.wbaa.testkit.docker

import java.sql.DriverManager

import com.whisk.docker._
import nl.wbaa.testkit.AwaitAtMostTrait

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

trait DockerPostgresService extends DockerKit with DockerPortPicker with AwaitAtMostTrait {
  override val StartContainersTimeout = waitAtMostDuration
  override val StopContainersTimeout = waitAtMostDuration

  def databaseUser: String
  def databasePassword: String
  def databaseName: String
  def databaseVersion: String
  val databaseExposedPort: Int = randomAvailablePort()

  private val postgresPort = 5432

  lazy val postgresContainer: DockerContainer = DockerContainer(s"postgres:$databaseVersion", Some(s"postgres-$databaseExposedPort"))
    .withPorts((postgresPort, Some(databaseExposedPort)))
    .withEnv(
      s"POSTGRES_USER=$databaseUser",
      s"POSTGRES_PASSWORD=$databasePassword",
      s"POSTGRES_DB=$databaseName"
    )
    .withReadyChecker(
      new PostgresReadyChecker(databaseUser, databasePassword, databaseName, Some(databaseExposedPort)).looped(15, 1.second)
    )

  abstract override def dockerContainers: List[DockerContainer] =
    postgresContainer :: super.dockerContainers
}

class PostgresReadyChecker(user: String, password: String, database: String, port: Option[Int] = None) extends DockerReadyChecker {
  override def apply(container: DockerContainerState)(implicit docker: DockerCommandExecutor, ec: ExecutionContext) =
    container.getPorts().map(ports =>
      Try {
        Class.forName("org.postgresql.Driver")
        val url = s"jdbc:postgresql://${docker.host}:${port.getOrElse(ports.values.head)}/$database"
        Option(DriverManager.getConnection(url, user, password))
          .map(_.close)
          .isDefined
      }.getOrElse(false)
    )
}
