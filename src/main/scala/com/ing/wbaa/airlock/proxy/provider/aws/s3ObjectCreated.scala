package com.ing.wbaa.airlock.proxy.provider.aws

case class s3ObjectCreated(method: String = "*") {
  val value = s"s3:ObjectCreated:$method"
}
