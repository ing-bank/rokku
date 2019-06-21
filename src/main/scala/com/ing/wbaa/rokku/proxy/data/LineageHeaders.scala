package com.ing.wbaa.rokku.proxy.data

import akka.http.scaladsl.model.{ ContentType, HttpMethod }

case class LineageHeaders(
    host: Option[String],
    bucket: String,
    pseduoDir: Option[String],
    bucketObject: Option[String],
    method: HttpMethod,
    contentType: ContentType,
    clientType: Option[String],
    queryParams: Option[String],
    copySource: Option[String],
    classifications: Option[Seq[String]],
    metadata: Option[Map[String, String]])
