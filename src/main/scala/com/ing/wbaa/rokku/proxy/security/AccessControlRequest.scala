package com.ing.wbaa.rokku.proxy.security

//val IP_CLIENT_PARAM_NAME = "ip-client"
//val IP_REMOTE_PARAM_NAME = "ip-remote"
//val IP_FORWARDED_FOR_PARAM_NAME = "ip-forwarded_for"
//val USER_AGENT_PARAM_NAME = "user-agent"
case class AccessControlRequest(
    user: String,
    userGroups: Set[String],
    userRole: String,
    path: String,
    accessType: String,
    action: String,
    clientIpAddress: String,
    remoteIpAddress: String,
    forwardedIpAddresses: Set[String],
    userAgent: String = null
)
