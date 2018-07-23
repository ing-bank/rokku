package nl.wbaa.gargoyle.proxy.providers

import java.util.Date

import com.typesafe.config.ConfigFactory
import nl.wbaa.gargoyle.proxy.data.S3Request
import org.apache.ranger.plugin.policyengine.{RangerAccessRequestImpl, RangerAccessResourceImpl, RangerAccessResult}
import org.apache.ranger.plugin.service.RangerBasePlugin

import scala.collection.JavaConverters
import scala.concurrent.Future

/**
 * Interface for security provider implementations.
 */
trait AuthorizationProvider {

  import AuthorizationProvider._

  def isAuthorized(request: S3Request): Future[Boolean] = {
    val rangerRequest = new RangerAccessRequestImpl()
    val resource = new RangerAccessResourceImpl()

    resource.setValue("demobucket/this.json", request.path)
    resource.setOwnerUser(request.owner)

    rangerRequest.setResource(resource)
    rangerRequest.setAccessTime(new Date)
    rangerRequest.setUser(request.username)
    rangerRequest.setUserGroups(JavaConverters.setAsJavaSet(request.userGroups.toSet))
    rangerRequest.setClientIPAddress(request.clientIp)
    rangerRequest.setAccessType(request.accessType)
    rangerRequest.setAction(request.method)
    rangerRequest.setForwardedAddresses(JavaConverters.bufferAsJavaList(
      request.fwdAddresses.toBuffer)
    )
    rangerRequest.setRemoteIPAddress(request.remoteAddr)

    // TODO: use .setContext for metadata like arn

    val result: Option[RangerAccessResult] = Option(plugin.isAccessAllowed(rangerRequest))
    Future.successful(result.exists(_.getIsAllowed))
  }
}

object AuthorizationProvider {
  private val rangerConfig = ConfigFactory.load().getConfig("ranger.settings")

  private lazy val plugin = {
    val p = new RangerBasePlugin(
      "ceph",
      "s3"
//      rangerConfig.getString("service_type"),
//      rangerConfig.getString("app_id")
    )
    p.init()
    p
  }
}
