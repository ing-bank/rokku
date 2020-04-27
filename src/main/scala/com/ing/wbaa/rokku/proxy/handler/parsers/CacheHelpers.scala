package com.ing.wbaa.rokku.proxy.handler.parsers

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.util.Random

object CacheHelpers {
  val random = Random

  val isHead: HttpRequest => Boolean = request => request.method == HttpMethods.HEAD

  def processHeadersForCache(headers: Seq[HttpHeader], contentLength: Option[Long], statusCode: StatusCode): String = {
    val originalHeaders = headers.map(r => (s"${r.name()}::${r.value()}"))
    contentLength match {
      case Some(cl) => Seq(statusCode.intValue()) ++ originalHeaders ++ Seq(RawHeader("ContentLength:", cl.toString))
      case None     => Seq(statusCode.intValue()) ++ originalHeaders
    }
  }.mkString("|")

  def processHeadersFromCache(bs: Option[ByteString]): (StatusCode, List[RawHeader]) = {
    bs.map { b =>
      {

        val params = b.utf8String.split("\\|")
        (
          StatusCode.int2StatusCode(params(0).toInt),
          params.drop(1)
          .map { pair =>
            pair.split("::").grouped(2)
              .map { arr =>
                RawHeader(arr(0), arr(1))
              }
          }.toList.flatten)
      }
    }.get
  }

  // https://github.com/akka/akka-http/issues/377
  // return a pseudo Default entity that contains the content-length and an Empty data stream
  def generateFakeEntity(contentLength: Int): HttpEntity.Default = {
    HttpEntity.Default(
      ContentType.WithMissingCharset(MediaTypes.`text/plain`),
      contentLength,
      Source(ByteString() :: Nil)
    )
  }

}
