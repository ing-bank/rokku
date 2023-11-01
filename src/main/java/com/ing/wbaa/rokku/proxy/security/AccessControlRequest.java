package com.ing.wbaa.rokku.proxy.security;

import java.util.List;
import java.util.Set;
public record AccessControlRequest(
    String user,
    Set<String> userGroups,
    String userRole,
    String path,
    String accessType,
    String action,
    String clientIpAddress,
    String remoteIpAddress,
    List<String> forwardedIpAddresses) {
}
