package com.ing.wbaa.gargoyle.proxy.data

case class AwsAccessKey(value: String) extends AnyVal
case class AwsSessionToken(value: String) extends AnyVal

case class AwsRequestCredential(accessKey: AwsAccessKey, sessionToken: Option[AwsSessionToken])
