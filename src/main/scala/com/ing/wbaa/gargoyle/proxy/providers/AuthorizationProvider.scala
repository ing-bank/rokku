package com.ing.wbaa.gargoyle.proxy.providers

import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.data.S3Request
import org.apache.ranger.plugin.policyengine.{RangerAccessRequestImpl, RangerAccessResourceImpl}
import org.apache.ranger.plugin.service.RangerBasePlugin

import scala.collection.JavaConverters._

/**
 * Interface for security provider implementations.
 */
trait AuthorizationProvider {

  val rangerSettings: GargoyleRangerSettings

  private lazy val plugin = {
    val p = new RangerBasePlugin(rangerSettings.serviceType, rangerSettings.appId)
    p.init()
    p
  }

  def isAuthorized(request: S3Request): Boolean = {
    val resource = new RangerAccessResourceImpl(
      Map[String, AnyRef]("path" -> request.path).asJava
    )

    // TODO: use .setContext for metadata like arn
    val rangerRequest = new RangerAccessRequestImpl(
      resource,
      request.accessType.toString,
      request.username,
      request.userGroups.asJava
    )

    Option(plugin.isAccessAllowed(rangerRequest)).exists(_.getIsAllowed)
  }
}
