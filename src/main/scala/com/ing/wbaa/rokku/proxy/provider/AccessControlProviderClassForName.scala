package com.ing.wbaa.rokku.proxy.provider

import com.ing.wbaa.rokku.proxy.config.AccessControlSettings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.security.{ AccessControl, AccessControlRequest }
import org.slf4j.LoggerFactory

trait AccessControlProviderClassForName extends AccessControl {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  lazy val auth: AccessControl =
    Class.forName(authorizerSettings.className)
      .getDeclaredConstructor(classOf[AccessControlSettings])
      .newInstance(authorizerSettings).asInstanceOf[AccessControl]

  def init(): Unit = {
    logger.info("AccessControlProviderClassForName uses {}", auth.getClass.getName)
    auth.init()
  }

  def isAccessAllowed(request: AccessControlRequest)(implicit id: RequestId): Boolean = {
    auth.isAccessAllowed(request)
  }
}
