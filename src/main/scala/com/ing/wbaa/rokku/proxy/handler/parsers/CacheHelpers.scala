package com.ing.wbaa.rokku.proxy.handler.parsers

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ ContentType, HttpEntity, HttpHeader, HttpMethods, HttpRequest, MediaTypes }
import akka.util.ByteString

import scala.util.Random

object CacheHelpers {
  val random = Random

  val isHead: HttpRequest => Boolean = request => request.method == HttpMethods.HEAD

  def processHeadersForCache(headers: Seq[HttpHeader], contentLength: Option[Long]): String = {
    val originalHeaders = headers.map(r => (s"${r.name()}::${r.value()}"))
    contentLength match {
      case Some(cl) => originalHeaders ++ Seq(RawHeader("ContentLength:", cl.toString))
      case None     => originalHeaders
    }
  }.mkString("|")

  def processHeadersFromCache(bs: Option[ByteString]): List[RawHeader] = {
    bs.map { b =>
      b.utf8String.split("\\|")
        .map { pair =>
          pair.split("::").grouped(2)
            .map { arr =>
              RawHeader(arr(0), arr(1))
            }
        }.toList.flatten
    }.get
  }

  // https://github.com/akka/akka-http/issues/377
  // return a pseudo Default entity that contains the content-length and an unmaterializable data stream
  def generateFakeEntity(contentLength: Int): HttpEntity.Strict = {
    val data = ByteString(random.alphanumeric.take(contentLength).mkString)
    HttpEntity.apply(ContentType.WithMissingCharset(MediaTypes.`text/plain`), data)
  }

}
