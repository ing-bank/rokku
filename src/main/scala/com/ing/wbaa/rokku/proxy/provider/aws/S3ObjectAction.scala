package com.ing.wbaa.rokku.proxy.provider.aws

sealed trait S3ObjectAction {
  val value: String
}

case class s3ObjectCreated(method: String = "*") extends S3ObjectAction {
  val value = s"s3:ObjectCreated:$method"
}
case class s3ObjectRemoved(method: String = "*") extends S3ObjectAction {
  val value = s"s3:ObjectRemoved:$method"
}

case class s3ObjectAudit(method: String = "*") extends S3ObjectAction {
  val value = method
}
