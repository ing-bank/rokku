package com.ing.wbaa.gargoyle.proxy.data

case class UserName(value: String) extends AnyVal
case class UserAssumedGroup(value: String) extends AnyVal

case class UserRawJson(
    userName: String,
    userGroup: Option[String],
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
      userRawJson.userGroup.map(UserAssumedGroup),
      AwsAccessKey(userRawJson.accessKey),
      AwsSecretKey(userRawJson.secretKey)
    )
}
