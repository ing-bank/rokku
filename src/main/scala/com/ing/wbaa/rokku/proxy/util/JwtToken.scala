package com.ing.wbaa.rokku.proxy.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ing.wbaa.rokku.proxy.config.StsSettings

import scala.util.{Failure, Success, Try}

trait JwtToken {
  protected[this] def stsSettings: StsSettings

  lazy val createInternalToken: String =
    Try {
      val algorithm = Algorithm.HMAC256(stsSettings.encodeSecret)
      JWT
        .create()
        .withIssuer("rokku")
        .withClaim("service", "rokku")
        .sign(algorithm)
    } match {
      case Success(t)         => t
      case Failure(exception) => throw exception
    }

}
