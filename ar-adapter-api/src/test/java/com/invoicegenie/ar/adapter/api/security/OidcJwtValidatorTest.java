package com.invoicegenie.ar.adapter.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OidcJwtValidator")
class OidcJwtValidatorTest {

    @Test
    @DisplayName("validates RS256 JWT against mocked JWKS")
    void validatesWithMockedJwks() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();

        String n = b64(pub.getModulus());
        String e = b64(pub.getPublicExponent());
        String jwks = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";

        long now = System.currentTimeMillis() / 1000L;
        String header = b64json("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"k1\"}");
        String payload = b64json("{\"iss\":\"https://idp.example/realms/ig\","
                + "\"sub\":\"user-1\","
                + "\"tenant_id\":\"00000000-0000-0000-0000-000000000001\","
                + "\"groups\":[\"AR_CONTROLLER\",\"clerk\"],"
                + "\"exp\":" + (now + 3600) + ",\"iat\":" + now + "}");
        String signingInput = header + "." + payload;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(priv);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
        String token = signingInput + "." + signature;

        OidcJwtValidator validator = new OidcJwtValidator(
                "https://idp.example/realms/ig",
                "none",
                "https://idp.example/jwks",
                "tenant_id",
                "groups",
                uri -> jwks,
                60_000L);

        Optional<ApiKeyRegistry.JwtClaims> claims = validator.validate("Bearer " + token);
        assertTrue(claims.isPresent());
        assertEquals("00000000-0000-0000-0000-000000000001", claims.get().tenantId());
        assertEquals("user-1", claims.get().subject());
        assertTrue(claims.get().roles().contains(ArRoles.AR_CONTROLLER));
        assertTrue(claims.get().roles().contains(ArRoles.AR_CLERK));
    }

    @Test
    @DisplayName("rejects wrong issuer")
    void rejectsWrongIssuer() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        String n = b64(pub.getModulus());
        String e = b64(pub.getPublicExponent());
        String jwks = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
        long now = System.currentTimeMillis() / 1000L;
        String header = b64json("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"k1\"}");
        String payload = b64json("{\"iss\":\"https://evil.example\","
                + "\"sub\":\"u\",\"tenant_id\":\"00000000-0000-0000-0000-000000000001\","
                + "\"exp\":" + (now + 3600) + "}");
        String signingInput = header + "." + payload;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(priv);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String token = signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());

        OidcJwtValidator validator = new OidcJwtValidator(
                "https://idp.example/realms/ig", "none", "https://idp.example/jwks",
                "tenant_id", "groups", uri -> jwks, 60_000L);
        assertTrue(validator.validate("Bearer " + token).isEmpty());
    }

    @Test
    void roleMapper() {
        assertEquals(ArRoles.TENANT_ADMIN, OidcRoleMapper.mapOne("admin"));
        assertEquals(ArRoles.AR_CLERK, OidcRoleMapper.mapOne("ROLE_AR_CLERK"));
        assertEquals(Set.of(ArRoles.AR_AUDITOR), OidcRoleMapper.mapRoles(Set.of("auditor", "unknown")));
    }

    private static String b64(BigInteger bi) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(toBytes(bi));
    }

    private static byte[] toBytes(BigInteger bi) {
        byte[] bytes = bi.toByteArray();
        if (bytes[0] == 0) {
            byte[] tmp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, tmp, 0, tmp.length);
            return tmp;
        }
        return bytes;
    }

    private static String b64json(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
