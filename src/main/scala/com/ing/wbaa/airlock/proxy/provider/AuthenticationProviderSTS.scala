package com.ing.wbaa.airlock.proxy.provider

import java.security.Key

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.ing.wbaa.airlock.proxy.config.StsSettings
import com.ing.wbaa.airlock.proxy.data._
import com.typesafe.scalalogging.LazyLogging
import io.jsonwebtoken.Jwts

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait AuthenticationProviderSTS extends JsonProtocols with LazyLogging {

  private val key: Key = {
    import java.security.KeyFactory
    import java.security.spec.X509EncodedKeySpec
    import java.util.Base64

    val publicKeyContent = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArQuAZEdjYst6u9+r3ZHiHeA/U3pDEopukbMCNlCvsIx7Li12MfJXqfg6XRY5Lxkk9l2Y2pRjrY23aFzvl0s6AkzeJfCATe711FRvRgeCQ1NBheI+bwCCrAvxZqiqywy2KRKCS7jGAoS+bveq+ScKdQgdqrFdRzxB8aUiDaZRfATyihJ5MaTttMv4COpkZgmsKtt8Q47sgpPf2G/uzyrLdMKuU/qcHTG2Z+bf+lSSmnL/H8m9IwdWqFEJkYZjxus5zQJdfEBEhh7BrY7MVVv90XVa4JveZtFGTDYfLo78MI8fsS1Y11m+IN2kZW05Umdl91VOz1ibHYwblw7S5fHdGQIDAQAB"

    val kf = KeyFactory.getInstance("RSA")
    val keySpec = new X509EncodedKeySpec(Base64.getDecoder.decode(publicKeyContent))
    val pubKey = kf.generatePublic(keySpec)

    pubKey
  }

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def materializer: Materializer

  protected[this] def stsSettings: StsSettings

  protected[this] def areCredentialsActive(awsRequestCredential: AwsRequestCredential): Future[Option[User]] = {
    val optToken = awsRequestCredential.sessionToken

    val user =
      optToken flatMap { token =>
        Try {
          val claims = Jwts.parser().setSigningKey(key).parseClaimsJws(token.value).getBody
          val userName = UserName(claims.getSubject)
          val userGroups = claims.get("user-groups", classOf[String]).split(':').map(UserGroup).toSet
          val secretKey = AwsSecretKey(claims.get("secret-key", classOf[String]))
          User(userName, userGroups, awsRequestCredential.accessKey, secretKey)
        }.toOption
      }

    Future.successful(user)
  }
}

object AuthenticationProviderSTS {
  final case class STSException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
