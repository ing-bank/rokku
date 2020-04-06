package com.ing.wbaa.rokku.proxy.handler

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.cache.{ CacheRulesV1, MemoryStorageCache }
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.AWSRequestType

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

trait RequestHandlerS3Cache extends MemoryStorageCache with RequestHandlerS3 with CacheRulesV1 {

  private val logger = new LoggerHandlerWithId
  implicit val materializer: ActorMaterializer

  def awsRequestFromRequest(request: HttpRequest): AWSRequestType

  def isEligibleToBeCached(request: HttpRequest)(implicit id: RequestId): Boolean

  def isEligibleToBeInvalidated(request: HttpRequest)(implicit id: RequestId): Boolean

  override protected[this] def fireRequestToS3(request: HttpRequest)(implicit id: RequestId): Future[HttpResponse] = {

    if (storageS3Settings.isCacheEnabled) {
      if (isEligibleToBeCached(request)) {
        getObjectFromCacheOrStorage(request)
      } else {
        invalidateEntryIfObjectInCache(request)
        super.fireRequestToS3(request)
      }
    } else {
      super.fireRequestToS3(request)
    }
  }

  /**
   * Get object from cache or from a strorage if is not in cache
   *
   * @param request
   * @param id
   * @return requested object
   */
  private def getObjectFromCacheOrStorage(request: HttpRequest)(implicit id: RequestId): Future[HttpResponse] = {
    val obj = getObject(request)
    if (obj.isDefined) {
      Future.successful(HttpResponse.apply(entity = obj.get))
    } else {
      readFromStorageAndUpdateCache(request)
      super.fireRequestToS3(request)
    }
  }

  /**
   * For PUT/POST/DELETE ... we need to remove an object from cache
   *
   * @param request
   * @param id
   */
  def invalidateEntryIfObjectInCache(request: HttpRequest)(implicit id: RequestId): Unit = {
    if (isEligibleToBeInvalidated(request)) {
      removeObject(getKey(request))
    }
  }

  /**
   * Reads the object from a storage and put in cache
   *
   * @param request
   * @param id
   * @return
   */
  private def readFromStorageAndUpdateCache(request: HttpRequest)(implicit id: RequestId): Future[Unit] = {
    Future {
      val key = getKey(request)
      super.fireRequestToS3(request).flatMap { response =>
        response.entity.toStrict(3.seconds).flatMap { r =>
          r.dataBytes.runFold(ByteString.empty) { case (acc, b) => acc ++ b }
        }
      }.onComplete {
        case Failure(exception) => logger.error("cannot store object () in cache {}", key, exception)
        case Success(value) =>
          putObject(key, value)
      }
    }
  }
}
