package com.invoicegenie.ar.adapter.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoginUserRegistry")
class LoginUserRegistryTest {

    @Test
    @DisplayName("parses user:password:tenant:roles")
    void parsesUsers() {
        LoginUserRegistry reg = new LoginUserRegistry(
                "clerk:secret:00000000-0000-0000-0000-000000000001:AR_CLERK|AR_AUDITOR,"
                        + "admin:admin-pass:00000000-0000-0000-0000-000000000001:TENANT_ADMIN");
        assertEquals(2, reg.size());
        var clerk = reg.authenticate("clerk", "secret").orElseThrow();
        assertEquals("00000000-0000-0000-0000-000000000001", clerk.tenantId());
        assertTrue(clerk.roles().contains(ArRoles.AR_CLERK));
        assertTrue(clerk.roles().contains(ArRoles.AR_AUDITOR));
        assertTrue(reg.authenticate("admin", "admin-pass").isPresent());
        assertTrue(reg.authenticate("clerk", "wrong").isEmpty());
        assertTrue(reg.authenticate("missing", "x").isEmpty());
    }

    @Test
    @DisplayName("defaults role to AR_CLERK when omitted")
    void defaultRole() {
        LoginUserRegistry reg = new LoginUserRegistry(
                "u:p:00000000-0000-0000-0000-000000000001");
        var u = reg.authenticate("u", "p").orElseThrow();
        assertEquals(1, u.roles().size());
        assertTrue(u.roles().contains(ArRoles.AR_CLERK));
    }
}
