package com.ing.wbaa.rokku.proxy.handler.parsers

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString

object CacheHelpers {

  private val headerSeparator = "::"
  private val objSeparator = "|"
  val isHead: HttpRequest => Boolean = request => request.method == HttpMethods.HEAD

  def processHeadersForCache(headers: Seq[HttpHeader], contentLength: Option[Long], statusCode: StatusCode): String = {
    val originalHeaders = headers.map(r => s"${r.name()}$headerSeparator${r.value()}")
    contentLength match {
      case Some(cl) => Seq(statusCode.intValue()) ++ originalHeaders ++ Seq(RawHeader("ContentLength:", cl.toString))
      case None     => Seq(statusCode.intValue()) ++ originalHeaders
    }
  }.mkString(objSeparator)

  def processHeadersFromCache(bs: Option[ByteString]): (StatusCode, List[RawHeader]) = {
    bs.map { b =>
      {
        val params = b.utf8String.split(s"\\$objSeparator")
        (
          StatusCode.int2StatusCode(params(0).toInt),
          params.drop(1)
          .map { pair =>
            pair.split(headerSeparator).grouped(2)
              .map { arr =>
                RawHeader(arr(0), arr(1))
              }
          }.toList.flatten)
      }
    }.get
  }

  // https://github.com/akka/akka-http/issues/377
  // return a pseudo Default entity that contains the content-length and an Empty data stream
  def generateFakeEntity(contentLength: Long): ResponseEntity = {
    val entityWithSize: Long => HttpEntity.Default = cl =>
      HttpEntity.Default(
        ContentType.WithMissingCharset(MediaTypes.`text/plain`),
        cl,
        Source(ByteString() :: Nil)
      )

    if (contentLength == 0) {
      HttpEntity.Empty
    } else {
      entityWithSize(contentLength)
    }
  }

}
