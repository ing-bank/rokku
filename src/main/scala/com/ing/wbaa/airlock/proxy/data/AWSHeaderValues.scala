package com.ing.wbaa.airlock.proxy.data

case class AWSHeaderValues(
    accessKey: Option[String],
    signedHeadersMap: Map[String, String],
    signature: Option[String],
    requestDate: Option[String],
    securityToken: Option[String],
    version: String,
    contentMD5: Option[String])
