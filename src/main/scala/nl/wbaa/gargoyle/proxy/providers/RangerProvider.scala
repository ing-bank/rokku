import java.util.Date

import com.typesafe.config.ConfigFactory
import nl.wbaa.gargoyle.proxy.providers.{AuthorizationProvider}
import org.apache.ranger.plugin.policyengine.{RangerAccessRequestImpl, RangerAccessResourceImpl, RangerAccessResult}
import org.apache.ranger.plugin.service.RangerBasePlugin

import scala.collection.JavaConverters

case class S3Request() {
  def WRITE     = s"write"
  def READ      = s"read"
  def WRITE_ACP = s"write_acp"
  def READ_ACP  = s"read_acp"

  var path: String = ""
  var owner: String = ""
  var method : String = ""
  var accessType: String = ""
  var username: String = ""
  var userGroups: Array[String] = Array[String]()
  var clientIp: String = ""
  var remoteAddr: String = ""
  var fwdAddresses: Array[String] = Array[String]()
}


object RangerProvider {
  private val config         = ConfigFactory.load().getConfig("ranger.settings")

  private val plugin         = new RangerBasePlugin(
    config.getString("service_type"),
    config.getString("app_id")
  )

  plugin.init()

   def isAccessible(request: S3Request): Boolean = {
    val rangerRequest = new RangerAccessRequestImpl()
    val resource = new RangerAccessResourceImpl()

    resource.setValue("path", request.path)
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
    if (result.isDefined)
      result.get.getIsAllowed
    else
      false
  }
}
