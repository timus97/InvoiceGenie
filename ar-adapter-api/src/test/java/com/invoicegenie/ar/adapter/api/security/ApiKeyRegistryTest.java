package com.invoicegenie.ar.adapter.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiKeyRegistry")
class ApiKeyRegistryTest {

    @Test
    @DisplayName("parses key:tenant pairs")
    void parsesPairs() {
        ApiKeyRegistry reg = new ApiKeyRegistry(
                "k1:00000000-0000-0000-0000-000000000001,k2=00000000-0000-0000-0000-000000000002");
        assertEquals(2, reg.size());
        assertEquals("00000000-0000-0000-0000-000000000001", reg.resolveTenant("k1").orElseThrow());
        assertEquals("00000000-0000-0000-0000-000000000002", reg.resolveTenant("k2").orElseThrow());
        assertTrue(reg.resolveTenant("missing").isEmpty());
    }

    @Test
    @DisplayName("validates HS256 JWT with tenant_id")
    void validatesJwt() throws Exception {
        String secret = "test-secret-key-which-is-long-enough";
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64("{\"tenant_id\":\"00000000-0000-0000-0000-000000000001\",\"sub\":\"user-1\"}");
        String signingInput = header + "." + payload;
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        String token = signingInput + "." + sig;

        Optional<ApiKeyRegistry.JwtClaims> claims = ApiKeyRegistry.validateHs256Jwt("Bearer " + token, secret);
        assertTrue(claims.isPresent());
        assertEquals("00000000-0000-0000-0000-000000000001", claims.get().tenantId());
        assertEquals("user-1", claims.get().subject());
    }

    @Test
    @DisplayName("rejects bad JWT signature")
    void rejectsBadSig() {
        assertTrue(ApiKeyRegistry.validateHs256Jwt("a.b.c", "secret").isEmpty());
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
