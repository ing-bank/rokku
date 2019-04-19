package com.ing.wbaa.rokku.proxy.data

case class UserName(value: String) extends AnyVal
case class UserGroup(value: String) extends AnyVal

case class UserRawJson(
    userName: String,
    userGroups: Set[String],
    accessKey: String,
    secretKey: String)

case class User(
    userName: UserName,
    userGroups: Set[UserGroup],
    accessKey: AwsAccessKey,
    secretKey: AwsSecretKey)

object User {
  def apply(userRawJson: UserRawJson): User =
    User(
      UserName(userRawJson.userName),
      userRawJson.userGroups.map(UserGroup),
      AwsAccessKey(userRawJson.accessKey),
      AwsSecretKey(userRawJson.secretKey)
    )
}
