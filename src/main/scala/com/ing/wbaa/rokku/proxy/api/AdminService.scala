package com.ing.wbaa.rokku.proxy.api

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpCharsets.`UTF-8`
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ AuthenticationFailedRejection, Directive }
import akka.parboiled2.util.Base64
import com.ing.wbaa.rokku.proxy.cache.HazelcastCacheWithConf._
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait AdminService {
  protected[this] def storageS3Settings: StorageS3Settings
  protected[this] def setCacheParam(key: String, value: String, map: String = "S3CacheConf")(implicit id: RequestId): Future[Unit]

  case class EligiblePaths(paths: String)
  case class EligibleSize(size: Long)
  case class HeadEnabled(on: Boolean)

  implicit val eligiblePathsF = jsonFormat1(EligiblePaths)
  implicit val eligibleSizeF = jsonFormat1(EligibleSize)
  implicit val headEnabledF = jsonFormat1(HeadEnabled)

  //todo: replace with real authentication
  private val authToken = ConfigFactory.load().getString("rokku.storage.s3.adminToken")
  private def decodeAuthToken(token: String) = new String(Base64.rfc2045.decode(token), `UTF-8`.nioCharset).trim

  private def checkProvidedToken(token: Option[String]) = token match {
    case Some(t) if decodeAuthToken(t) == authToken => true
    case _ => false
  }

  def extractAuthToken: Directive[Tuple1[Option[String]]] =
    optionalHeaderValueByName("AUTH_TOKEN")
      .tflatMap { case Tuple1(v) =>
        checkProvidedToken(v) match {
          case true    => provide(v)
          case false   => reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.basic("Admin API")))
        }
      }
  val adminRoute =
    extractAuthToken { _ =>
      post {
        pathPrefix("api" / "admin") {
          implicit val requestId: RequestId = RequestId(UUID.randomUUID().toString)
          path("allowed" / "paths") {
            entity(as[EligiblePaths]) { conf =>
              onComplete(setCacheParam(ELIGIBLE_CACHE_PATHS, conf.paths)) {
                case Success(_)  => complete(s"Allowed paths - setting updated")
                case Failure(ex) => complete(s"Failed to update configuration parameter: ${ex.getMessage}")
              }
            }
          } ~ path("allowed" / "size") {
            entity(as[EligibleSize]) { conf =>
              onComplete(setCacheParam(MAX_ELIGIBLE_CACHE_OBJECT_SIZE_IN_BYTES, conf.size.toString)) {
                case Success(_)  => complete(s"Allowed size - setting updated")
                case Failure(ex) => complete(s"Failed to update configuration parameter ${ex.getMessage}")
              }
            }
          } ~ path("head" / "on") {
            entity(as[HeadEnabled]) { conf =>
              onComplete(setCacheParam(HEAD_CACHE_ENABLED, conf.on.toString)) {
                case Success(_)  => complete(s"Head on - setting updated")
                case Failure(ex) => complete(s"Failed to update configuration parameter ${ex.getMessage}")
              }
            }
          }
        }
      }
    }
}
