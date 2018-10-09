package com.ing.wbaa.gargoyle.proxy.data

case class AWSHeaderValues(
    accessKey: Option[String],
    signedHeaders: Option[String],
    signature: Option[String],
    contentSHA256: Option[String],
    requestDate: Option[String],
    securityToken: Option[String],
    version: String,
    contentMD5: Option[String])
