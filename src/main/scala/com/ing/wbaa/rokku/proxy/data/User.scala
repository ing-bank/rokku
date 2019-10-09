package com.ing.wbaa.rokku.proxy.data

case class UserName(value: String) extends AnyVal
case class UserGroup(value: String) extends AnyVal
case class UserAssumeRole(value: String)

case class UserRawJson(
    userName: String,
    userGroups: Option[Set[String]],
    accessKey: String,
    secretKey: String,
    userRole: Option[String])

case class User(
    userName: UserName,
    userGroups: Set[UserGroup],
    accessKey: AwsAccessKey,
    secretKey: AwsSecretKey,
    userRole: UserAssumeRole)

object User {

  def apply(userRawJson: UserRawJson): User = userRawJson.userRole match {
    case Some(role) =>
      User(
        UserName(userRawJson.userName),
        Set.empty.map(UserGroup),
        AwsAccessKey(userRawJson.accessKey),
        AwsSecretKey(userRawJson.secretKey),
        UserAssumeRole(role)
      )
    case _ =>
      User(
        UserName(userRawJson.userName),
        userRawJson.userGroups.getOrElse(Set.empty).map(UserGroup),
        AwsAccessKey(userRawJson.accessKey),
        AwsSecretKey(userRawJson.secretKey),
        UserAssumeRole("")
      )
  }

}
