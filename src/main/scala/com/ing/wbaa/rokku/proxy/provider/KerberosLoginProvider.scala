package com.ing.wbaa.rokku.proxy.provider

import java.io.File

import com.ing.wbaa.rokku.proxy.config.KerberosSettings
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang.StringUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.security.UserGroupInformation

import scala.util.{Failure, Success, Try}

trait KerberosLoginProvider extends LazyLogging {

  protected[this] def kerberosSettings: KerberosSettings

  loginUserFromKeytab(kerberosSettings.keytab, kerberosSettings.principal)

  private def loginUserFromKeytab(keytab: String, principal: String): Unit =
    if (StringUtils.isNotBlank(keytab) && StringUtils.isNotBlank(principal)) {
      if (!new File(keytab).exists()) {
        logger.info("keytab file does not exist {}", keytab)
      } else {
        Try {
          UserGroupInformation.setConfiguration(new Configuration())
          UserGroupInformation.loginUserFromKeytab(principal, keytab)
        } match {
          case Success(_)         => logger.info("kerberos credentials provided {}", UserGroupInformation.getLoginUser)
          case Failure(exception) => logger.error("kerberos login error {}", exception)
        }
      }
    } else {
      logger.info("kerberos credentials are not provided")
    }

}
