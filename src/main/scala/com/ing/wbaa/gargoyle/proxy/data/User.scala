package com.ing.wbaa.gargoyle.proxy.data

case class User(
    userName: String,
    userGroup: Option[String],
    accessKey: String,
    secretKey: String)
