package com.ing.wbaa.gargoyle.proxy.data

import com.ing.wbaa.gargoyle.proxy.data.AccessType.AccessType


case class S3Request(
    path: String,
    accessType: AccessType,
    username: String,
    userGroups: Set[String]
)
