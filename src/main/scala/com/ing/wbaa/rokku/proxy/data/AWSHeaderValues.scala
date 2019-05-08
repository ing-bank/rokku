package com.ing.wbaa.rokku.proxy.data

case class AWSHeaderValues(
  accessKey: Option[String],
  signedHeadersMap: Map[String, String],
  signature: Option[String],
  requestDate: Option[String],
  securityToken: Option[String],
  contentMD5: Option[String]
)
