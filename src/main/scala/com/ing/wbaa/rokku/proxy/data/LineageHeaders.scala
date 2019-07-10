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
    classifications: Map[ClassificationFor, Seq[String]],
    metadata: Option[Map[String, String]])

sealed class ClassificationFor
case class BucketClassification() extends ClassificationFor
case class DirClassification() extends ClassificationFor
case class ObjectClassification() extends ClassificationFor
case class UnknownClassification() extends ClassificationFor
