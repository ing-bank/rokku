package com.ing.wbaa.rokku.proxy.handler

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.cache.{ CacheRulesV1, HazelcastCacheWithConf }
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.parsers.CacheHelpers._
import com.ing.wbaa.rokku.proxy.handler.parsers.{ GetCacheValueObject, HeadCacheValueObject }
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.AWSRequestType

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

trait RequestHandlerS3Cache extends HazelcastCacheWithConf with RequestHandlerS3 with CacheRulesV1 {

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
   * Get object from cache or from a storage if is not in cache
   *
   * @param request
   * @param id
   * @return requested object
   */
  private def getObjectFromCacheOrStorage(request: HttpRequest)(implicit id: RequestId): Future[HttpResponse] = {
    val obj = getObject(getKey(request))
    if (obj.isDefined) {
      if (isHead(request)) {
        readHeadFromCache(obj)
      } else if (obj.get.size < getMaxEligibleCacheObjectSizeInBytes) {
        Future.successful(HttpResponse.apply(entity = obj.get))
      } else {
        super.fireRequestToS3(request)
      }
    } else {
      // let's add to cache for next request
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
    //todo: consider narrowing to allowed paths only
    if (isEligibleToBeInvalidated(request)) {
      removeObject(getKey(request))
    }
  }

  private def readHeadFromCache(obj: Option[ByteString]): Future[HttpResponse] = {
    val parsedObj = processHeadersFromCache(obj)
    val responseHeaders = parsedObj._2
    val statusCode = parsedObj._1
    val contentLength = responseHeaders.find(_.name == "ContentLength")
    val data = contentLength match {
      case Some(RawHeader(_, v)) =>
        generateFakeEntity(v.trim.toLong)
      case None =>
        HttpEntity.apply(ContentType.WithMissingCharset(MediaTypes.`text/plain`), ByteString())
    }

    Future.successful(
      HttpResponse(entity = data).withHeaders(responseHeaders).withStatus(statusCode)
    )
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

        if (isHead(request)) {
          response.entity.discardBytes()
          Future.successful(HeadCacheValueObject(response.headers, response.entity.contentLengthOption, response.status))
        } else if (isEligibleSize(response)) {
          //todo: add head request to reduce amount of get's
          response.entity.toStrict(3.seconds).flatMap { r =>
            r.dataBytes.runFold(ByteString.empty) { case (acc, b) => acc ++ b }
          }.map(bs => GetCacheValueObject(bs))
        } else {
          response.entity.discardBytes()
          Future.failed(new ObjectTooBigException())
        }
      }.onComplete {
        case Failure(exception: ObjectTooBigException) => logger.debug("Object too big to be stored in cache {}", key, exception)
        case Failure(exception)                        => logger.error("Cannot store object () in cache {}", key, exception)
        case Success(headValue: HeadCacheValueObject) =>
          val value = processHeadersForCache(headValue.headers, headValue.contentLength, headValue.statusCode)
          logger.info("head object cache value {} for key {}", value, key)
          putObject(key, ByteString(value))
        case Success(getValue: GetCacheValueObject) => putObject(key, getValue.data)
      }
    }
  }

  class ObjectTooBigException extends Exception

}
