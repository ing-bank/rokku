package com.ing.wbaa.rokku.proxy.security;

import java.util.List;
import java.util.Set;
//val IP_CLIENT_PARAM_NAME = "ip-client"
//val IP_REMOTE_PARAM_NAME = "ip-remote"
//val IP_FORWARDED_FOR_PARAM_NAME = "ip-forwarded_for"
//val USER_AGENT_PARAM_NAME = "user-agent"
public record AccessControlRequest(
    String user,
    Set<String> userGroups,
    String userRole,
    String path,
    String accessType,
    String action,
    String clientIpAddress,
    String remoteIpAddress,
    List<String> forwardedIpAddresses,
    String userAgent) {
}
