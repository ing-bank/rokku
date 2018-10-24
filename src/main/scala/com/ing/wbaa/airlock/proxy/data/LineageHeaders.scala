package com.ing.wbaa.airlock.proxy.data

import akka.http.scaladsl.model.{ ContentType, HttpMethod }

case class LineageHeaders(
    host: Option[String],
    bucket: String,
    bucketObject: Option[String],
    method: HttpMethod,
    contentType: ContentType,
    clientType: Option[String],
    queryParams: Option[String],
    copySource: Option[String])
