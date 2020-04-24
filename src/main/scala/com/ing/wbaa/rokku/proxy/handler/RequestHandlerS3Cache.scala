package com.ing.wbaa.rokku.proxy.handler

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.cache.{ CacheRulesV1, HazelcastCache }
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.parsers.CacheHelpers._
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.AWSRequestType

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

trait RequestHandlerS3Cache extends HazelcastCache with RequestHandlerS3 with CacheRulesV1 {

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
    val obj = getObject(getKey(request))
    if (obj.isDefined && isHead(request)) {
      val parsedObj = processHeadersFromCache(obj)
      val responseHeaders = parsedObj._2
      val statusCode = parsedObj._1
      val contentLength = responseHeaders.find(_.name == "ContentLength")

      contentLength match {
        case Some(RawHeader(_, v)) =>
          Future.successful(
            //todo: get rid of body after setting correct contentLength, see generateFakeEntity for details
            HttpResponse(entity = generateFakeEntity(v.trim.toInt))
              .withHeaders(responseHeaders)
              .withStatus(statusCode)
          )
        case None =>
          Future.successful(
            HttpResponse(entity = HttpEntity.apply(ContentType.WithMissingCharset(MediaTypes.`text/plain`), ByteString()))
              .withHeaders(responseHeaders)
              .withStatus(statusCode)
          )
      }
    } else if (obj.isDefined) {
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
        val contentLength = response.entity.contentLengthOption.getOrElse(8388608L)
        lazy val bytesWithHeadersAndSizeAndStatus =
          response.entity.toStrict(3.seconds, contentLength).flatMap { r =>
            r.dataBytes.runFold(ByteString.empty) { case (acc, b) => acc ++ b }
          }.map(bs => (bs, response.headers, response.entity.contentLengthOption, response.status))

        if (isEligibleSize(response) && !isHead(request)) {
          bytesWithHeadersAndSizeAndStatus
        } else if (isHead(request)) {
          bytesWithHeadersAndSizeAndStatus
        } else {
          response.entity.discardBytes()
          Future.failed(new ObjectTooBigException())
        }
      }.onComplete {
        case Failure(exception: ObjectTooBigException) => logger.debug("Object too big to be stored in cache {}", key, exception)
        case Failure(exception)                        => logger.error("Cannot store object () in cache {}", key, exception)
        case Success((respBytes, headers, contentLength, statusCode)) if respBytes.isEmpty && isHead(request) =>
          // for head cache entry must be different from actual object
          putObject(key, ByteString(processHeadersForCache(headers, contentLength, statusCode)))
        case Success((respBytes, _, _, _)) if respBytes.nonEmpty => putObject(key, respBytes)
      }
    }
  }

  class ObjectTooBigException extends Exception
}
