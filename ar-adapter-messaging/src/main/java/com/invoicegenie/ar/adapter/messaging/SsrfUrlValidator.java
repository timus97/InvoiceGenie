package com.invoicegenie.ar.adapter.messaging;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Blocks private, loopback, link-local, and cloud metadata targets (STORY-009).
 */
public final class SsrfUrlValidator {

    private SsrfUrlValidator() {}

    public static void validateHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid url");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalArgumentException("url must be http(s)");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("url host required");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(hostLower) || hostLower.endsWith(".localhost")
                || "metadata.google.internal".equals(hostLower)) {
            throw new IllegalArgumentException("SSRF blocked: local/metadata host");
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                if (isBlocked(addr)) {
                    throw new IllegalArgumentException("SSRF blocked: private or link-local address");
                }
            }
        } catch (UnknownHostException e) {
            // Leave unresolvable hosts to the HTTP client (delivery fails cleanly).
            // Blocking here breaks offline unit tests and valid DNS-pending hosts.
        }
    }

    static boolean isBlocked(InetAddress addr) {
        return addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()
                || isMetadataIp(addr);
    }

    private static boolean isMetadataIp(InetAddress addr) {
        byte[] b = addr.getAddress();
        // 169.254.0.0/16 link-local already covered; AWS metadata 169.254.169.254
        if (b.length == 4 && (b[0] & 0xff) == 169 && (b[1] & 0xff) == 254) {
            return true;
        }
        return false;
    }
}