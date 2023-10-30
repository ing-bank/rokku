package com.ing.wbaa.rokku.proxy.security;

public interface AccessControl {
    String AUDIT_ENABLED_PARAM = "auditEnabled";

    void init();

    boolean isAccessAllowed(AccessControlRequest request);
}
