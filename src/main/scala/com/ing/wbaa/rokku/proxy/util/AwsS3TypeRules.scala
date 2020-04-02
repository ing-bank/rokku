package com.ing.wbaa.rokku.proxy.util

import java.nio.charset.Charset

import akka.http.scaladsl.model.{ HttpMethods, HttpRequest }

//TODO create all aws s3 rules - when is bucket createion/ get object / list .....
object AwsS3TypeRules {

  object isGetObject {
    def unapply(request: HttpRequest): Option[Boolean] = {
      if (request.method == HttpMethods.GET && !isQueryStringPresent(request)) Some(true) else None
    }
  }

  object isPutObject {
    def unapply(request: HttpRequest): Option[Boolean] = {
      if (request.method == HttpMethods.PUT && !isQueryStringPresent(request)) Some(true) else None
    }
  }

  object isPostObject {
    def unapply(request: HttpRequest): Option[Boolean] = {
      if (request.method == HttpMethods.POST && !isQueryStringPresent(request)) Some(true) else None
    }
  }

  object isCreatObject {
    def unapply(request: HttpRequest): Option[Boolean] = request match {
      case isPutObject(_)  => Some(true)
      case isPostObject(_) => Some(true)
      case _               => None
    }
  }

  private def isQueryStringPresent(request: HttpRequest) = request.getUri().queryString(Charset.forName("UTF-8")).isPresent

}

