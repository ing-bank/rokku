package com.ing.wbaa.airlock.proxy.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ing.wbaa.airlock.proxy.config.StsSettings

import scala.util.{ Failure, Success, Try }

trait JwtToken {
  protected[this] def stsSettings: StsSettings

  lazy val createInternalToken: String =
    Try {
      val algorithm = Algorithm.HMAC256(stsSettings.encodeSecret)
      JWT.create()
        .withIssuer("airlock")
        .withClaim("service", "airlock")
        .sign(algorithm)
    } match {
      case Success(t)         => t
      case Failure(exception) => throw exception
    }

}
