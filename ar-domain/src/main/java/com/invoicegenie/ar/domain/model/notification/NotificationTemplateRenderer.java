package com.invoicegenie.ar.domain.model.notification;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe {{variable}} template renderer. Unknown variables become empty string.
 * Does not evaluate expressions — substitution only.
 */
public final class NotificationTemplateRenderer {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private NotificationTemplateRenderer() {}

    public static String render(String template, Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        if (vars == null || vars.isEmpty()) {
            return VAR.matcher(template).replaceAll("");
        }
        Matcher m = VAR.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String value = vars.getOrDefault(key, "");
            if (value == null) {
                value = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
