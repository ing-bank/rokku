package com.ing.wbaa.gargoyle.proxy.data

case class User(
    userId: String,
    secretKey: String,
    groups: Set[String],
    arn: String
)
