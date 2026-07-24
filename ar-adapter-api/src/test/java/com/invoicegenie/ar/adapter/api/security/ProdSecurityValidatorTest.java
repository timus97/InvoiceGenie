package com.invoicegenie.ar.adapter.api.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProdSecurityValidatorTest {

    @Test
    void nonProdProfileSkipsValidation() throws Exception {
        ProdSecurityValidator v = new ProdSecurityValidator();
        set(v, "profile", "dev");
        set(v, "securityEnabled", false);
        set(v, "datasourcePassword", "ar");
        set(v, "datasourceUsername", "ar");
        set(v, "mode", "api-key");
        set(v, "apiKeys", "none");
        set(v, "jwtSecret", "none");
        assertDoesNotThrow(() -> invokeStart(v));
    }

    @Test
    void prodRejectsSecurityOff() throws Exception {
        ProdSecurityValidator v = baseProd();
        set(v, "securityEnabled", false);
        assertThrows(IllegalStateException.class, () -> invokeStart(v));
    }

    @Test
    void prodRejectsDefaultPassword() throws Exception {
        ProdSecurityValidator v = baseProd();
        set(v, "datasourcePassword", "ar");
        set(v, "datasourceUsername", "ar");
        assertThrows(IllegalStateException.class, () -> invokeStart(v));
    }

    @Test
    void prodRejectsDemoApiKey() throws Exception {
        ProdSecurityValidator v = baseProd();
        set(v, "apiKeys", "dev-local-key:00000000-0000-0000-0000-000000000001");
        assertThrows(IllegalStateException.class, () -> invokeStart(v));
    }

    @Test
    void prodAcceptsStrongConfig() throws Exception {
        ProdSecurityValidator v = baseProd();
        assertDoesNotThrow(() -> invokeStart(v));
    }

    private static ProdSecurityValidator baseProd() throws Exception {
        ProdSecurityValidator v = new ProdSecurityValidator();
        set(v, "profile", "prod");
        set(v, "securityEnabled", true);
        set(v, "mode", "api-key");
        set(v, "apiKeys", "prod-key-a1b2c3d4e5f6:00000000-0000-0000-0000-000000000001");
        set(v, "jwtSecret", "none");
        set(v, "datasourceUsername", "ar_app");
        set(v, "datasourcePassword", "S3cure-P@ssw0rd-NotDemo!");
        return v;
    }

    private static void invokeStart(ProdSecurityValidator v) throws Exception {
        Method m = ProdSecurityValidator.class.getDeclaredMethod("onStart", io.quarkus.runtime.StartupEvent.class);
        m.setAccessible(true);
        try {
            m.invoke(v, new Object[]{null});
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = ProdSecurityValidator.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}