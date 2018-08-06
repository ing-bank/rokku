package com.ing.wbaa.gargoyle.proxy.data

/**
  * @param bucket A None for bucket means this is an operation not targeted to a specific bucket (e.g. list buckets)
  * @param accessType The access type for this request, write includes actions like write/update/delete
  */
case class S3Request(
                      credential: AwsRequestCredential,
                      bucket: Option[String],
                      accessType: AccessType,
)
