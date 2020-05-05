package com.ing.wbaa.rokku.proxy.api

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import com.ing.wbaa.rokku.proxy.cache.HazelcastCacheWithConf._
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.RequestId
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait AdminService {
  protected[this] def storageS3Settings: StorageS3Settings
  protected[this] def setCacheParam(key: String, value: String, map: String = "S3CacheConf")(implicit id: RequestId): Future[Unit]

  case class EligiblePaths(paths: String)
  case class EligibleSize(size: Long)

  implicit val eligiblePathsF = jsonFormat1(EligiblePaths)
  implicit val eligibleSizeF = jsonFormat1(EligibleSize)

  //todo: authentication
  val adminRoute =
    post {
      pathPrefix("api" / "admin") {
        implicit val requestId: RequestId = RequestId(UUID.randomUUID().toString)
        path("allowed" / "paths") {
          entity(as[EligiblePaths]) { conf =>
            onComplete(setCacheParam(ELIGIBLE_CACHE_PATHS, conf.paths)) {
              case Success(_)       => complete(s"Allowed paths - setting updated")
              case Failure(ex)      => complete(s"Failed to update configuration parameter: ${ex.getMessage}")
            }
          }
        } ~ path("allowed" / "size") {
          entity(as[EligibleSize]) { conf =>
            onComplete(setCacheParam(MAX_ELIGIBLE_CACHE_OBJECT_SIZE_IN_BYTES, conf.size.toString)) {
              case Success(_)       => complete(s"Allowed paths - setting updated")
              case Failure(ex)      => complete(s"Failed to update configuration parameter ${ex.getMessage}")
            }
          }
        }
      }
    }
}
