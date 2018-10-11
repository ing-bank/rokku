package com.ing.wbaa.airlock.proxy.data

case class UserName(value: String) extends AnyVal
case class UserAssumedGroup(value: String) extends AnyVal

case class UserRawJson(
    userName: String,
    userAssumedGroup: Option[String],
    accessKey: String,
    secretKey: String)

case class User(
    userName: UserName,
    userAssumedGroup: Option[UserAssumedGroup],
    accessKey: AwsAccessKey,
    secretKey: AwsSecretKey)

object User {
  def apply(userRawJson: UserRawJson): User =
    User(
      UserName(userRawJson.userName),
      userRawJson.userAssumedGroup.map(UserAssumedGroup),
      AwsAccessKey(userRawJson.accessKey),
      AwsSecretKey(userRawJson.secretKey)
    )
}
