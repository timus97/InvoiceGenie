package com.invoicegenie.ar.adapter.api.security;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Lightweight OIDC resource-server JWT validator using JWKS (PP-020).
 *
 * <p>Supports RS256 JWTs from an external issuer. No quarkus-oidc dependency —
 * production-shaped and unit-testable via injectable JWKS fetcher.
 */
public final class OidcJwtValidator {

    public interface JwksFetcher {
        String fetch(String jwksUri) throws Exception;
    }

    private final String issuer;
    private final String audience;
    private final String jwksUri;
    private final String tenantClaim;
    private final String rolesClaim;
    private final JwksFetcher jwksFetcher;
    private final long cacheTtlMillis;

    private volatile CachedJwks cachedJwks;

    public OidcJwtValidator(String issuer, String audience, String jwksUri,
                            String tenantClaim, String rolesClaim) {
        this(issuer, audience, jwksUri, tenantClaim, rolesClaim, defaultFetcher(), 300_000L);
    }

    public OidcJwtValidator(String issuer, String audience, String jwksUri,
                            String tenantClaim, String rolesClaim,
                            JwksFetcher jwksFetcher, long cacheTtlMillis) {
        this.issuer = normalize(issuer);
        this.audience = normalize(audience);
        this.jwksUri = normalize(jwksUri);
        this.tenantClaim = tenantClaim == null || tenantClaim.isBlank() ? "tenant_id" : tenantClaim.trim();
        this.rolesClaim = rolesClaim == null || rolesClaim.isBlank() ? "groups" : rolesClaim.trim();
        this.jwksFetcher = jwksFetcher != null ? jwksFetcher : defaultFetcher();
        this.cacheTtlMillis = Math.max(5_000L, cacheTtlMillis);
    }

    public boolean isConfigured() {
        return !jwksUri.isEmpty() && !issuer.isEmpty();
    }

    public Optional<ApiKeyRegistry.JwtClaims> validate(String authorizationHeader) {
        if (!isConfigured() || authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        String raw = authorizationHeader.trim();
        if (raw.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            raw = raw.substring(7).trim();
        }
        String[] parts = raw.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(pad(parts[0])), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(pad(parts[1])), StandardCharsets.UTF_8);
            String alg = extractString(headerJson, "alg");
            if (alg == null || !"RS256".equalsIgnoreCase(alg)) {
                return Optional.empty();
            }
            String kid = extractString(headerJson, "kid");

            String iss = extractString(payloadJson, "iss");
            if (iss == null || !issuerEquals(iss, issuer)) {
                return Optional.empty();
            }
            if (!audience.isEmpty()) {
                if (!audienceMatches(payloadJson, audience)) {
                    return Optional.empty();
                }
            }
            Long exp = extractLong(payloadJson, "exp");
            if (exp != null && exp < (System.currentTimeMillis() / 1000L)) {
                return Optional.empty();
            }

            PublicKey key = resolveKey(kid);
            if (key == null) {
                // force refresh once on miss
                invalidateCache();
                key = resolveKey(kid);
            }
            if (key == null) {
                return Optional.empty();
            }
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getUrlDecoder().decode(pad(parts[2]));
            if (!sig.verify(signatureBytes)) {
                return Optional.empty();
            }

            String tenantId = extractString(payloadJson, tenantClaim);
            if (tenantId == null || tenantId.isBlank()) {
                tenantId = extractString(payloadJson, "tenant_id");
            }
            if (tenantId == null || tenantId.isBlank()) {
                tenantId = extractString(payloadJson, "tenantId");
            }
            if (tenantId == null || tenantId.isBlank()) {
                return Optional.empty();
            }
            String sub = extractString(payloadJson, "sub");
            Set<String> roles = OidcRoleMapper.mapRoles(
                    extractStringArray(payloadJson, rolesClaim),
                    extractStringArray(payloadJson, "roles"),
                    extractStringArray(payloadJson, "groups"));
            return Optional.of(new ApiKeyRegistry.JwtClaims(
                    tenantId, sub != null ? sub : "oidc-subject", roles));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private PublicKey resolveKey(String kid) throws Exception {
        List<Jwk> keys = loadJwks();
        for (Jwk jwk : keys) {
            if (kid == null || kid.isBlank() || kid.equals(jwk.kid)) {
                if ("RSA".equalsIgnoreCase(jwk.kty)) {
                    return jwk.toRsaPublicKey();
                }
            }
        }
        return null;
    }

    private List<Jwk> loadJwks() throws Exception {
        CachedJwks c = cachedJwks;
        long now = System.currentTimeMillis();
        if (c != null && (now - c.fetchedAt) < cacheTtlMillis) {
            return c.keys;
        }
        String body = jwksFetcher.fetch(jwksUri);
        List<Jwk> keys = parseJwks(body);
        cachedJwks = new CachedJwks(keys, now);
        return keys;
    }

    void invalidateCache() {
        cachedJwks = null;
    }

    static List<Jwk> parseJwks(String body) {
        List<Jwk> out = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        // Minimal parser: find each {"kty":...} object under keys array
        int keysIdx = body.indexOf("\"keys\"");
        if (keysIdx < 0) {
            return out;
        }
        int arrStart = body.indexOf('[', keysIdx);
        int arrEnd = body.lastIndexOf(']');
        if (arrStart < 0 || arrEnd <= arrStart) {
            return out;
        }
        String arr = body.substring(arrStart + 1, arrEnd);
        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < arr.length(); i++) {
            char ch = arr.charAt(i);
            if (ch == '{') {
                if (depth == 0) {
                    objStart = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String obj = arr.substring(objStart, i + 1);
                    String kty = extractString(obj, "kty");
                    String kid = extractString(obj, "kid");
                    String n = extractString(obj, "n");
                    String e = extractString(obj, "e");
                    if (kty != null && n != null && e != null) {
                        out.add(new Jwk(kid, kty, n, e));
                    }
                    objStart = -1;
                }
            }
        }
        return out;
    }

    private static boolean issuerEquals(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        String a = actual.endsWith("/") ? actual.substring(0, actual.length() - 1) : actual;
        String b = expected.endsWith("/") ? expected.substring(0, expected.length() - 1) : expected;
        return a.equals(b);
    }

    private static boolean audienceMatches(String payloadJson, String expected) {
        String single = extractString(payloadJson, "aud");
        if (single != null && expected.equals(single)) {
            return true;
        }
        Set<String> arr = extractStringArray(payloadJson, "aud");
        return arr.contains(expected);
    }

    private static JwksFetcher defaultFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return uri -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("JWKS fetch failed: HTTP " + resp.statusCode());
            }
            return resp.body();
        };
    }

    private static String normalize(String v) {
        if (v == null) {
            return "";
        }
        String t = v.trim();
        if (t.isEmpty() || "none".equalsIgnoreCase(t) || "-".equals(t)) {
            return "";
        }
        return t;
    }

    private static String pad(String s) {
        int mod = s.length() % 4;
        if (mod == 0) {
            return s;
        }
        return s + "====".substring(mod);
    }

    static String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        int start = i + 1;
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }

    static Long extractLong(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) {
            j++;
        }
        if (j == i) {
            return null;
        }
        try {
            return Long.parseLong(json.substring(i, j));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Set<String> extractStringArray(String json, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (json == null || key == null) {
            return out;
        }
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return out;
        }
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) {
            return out;
        }
        int bracket = json.indexOf('[', colon + 1);
        // also allow single string
        int quote = json.indexOf('"', colon + 1);
        if (bracket < 0 || (quote >= 0 && quote < bracket)) {
            String single = extractString(json, key);
            if (single != null && !single.isBlank()) {
                out.add(single);
            }
            return out;
        }
        int end = json.indexOf(']', bracket + 1);
        if (end < 0) {
            return out;
        }
        String body = json.substring(bracket + 1, end);
        int i = 0;
        while (i < body.length()) {
            int q1 = body.indexOf('"', i);
            if (q1 < 0) {
                break;
            }
            int q2 = body.indexOf('"', q1 + 1);
            if (q2 < 0) {
                break;
            }
            String v = body.substring(q1 + 1, q2).trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
            i = q2 + 1;
        }
        return out;
    }

    record CachedJwks(List<Jwk> keys, long fetchedAt) {}

    static final class Jwk {
        final String kid;
        final String kty;
        final String n;
        final String e;

        Jwk(String kid, String kty, String n, String e) {
            this.kid = kid;
            this.kty = kty;
            this.n = n;
            this.e = e;
        }

        RSAPublicKey toRsaPublicKey() throws Exception {
            byte[] nBytes = Base64.getUrlDecoder().decode(pad(n));
            byte[] eBytes = Base64.getUrlDecoder().decode(pad(e));
            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);
            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        }
    }
}
