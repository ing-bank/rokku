package com.ing.wbaa.gargoyle.proxy.data

case class AWSHeaderValues(
    accessKey: String,
    signedHeaders: String,
    signature: String,
    contentSHA256: Option[String],
    requestDate: Option[String],
    securityToken: String,
    version: String,
    contentMD5: Option[String])
