package com.invoicegenie.shared.tenant;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Request-scoped actor identity for audit (STORY-012).
 * Populated by API filters from auth subject + client IP / User-Agent.
 */
public final class ActorContext {

    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

    private ActorContext() {}

    public static void set(Actor actor) {
        CURRENT.set(Objects.requireNonNull(actor, "actor"));
    }

    public static Optional<Actor> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Actor require() {
        Actor a = CURRENT.get();
        if (a == null) {
            return Actor.system();
        }
        return a;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Actor(
            UUID actorId,
            String actorType,
            String subject,
            String ipAddress,
            String userAgent
    ) {
        public static Actor system() {
            return new Actor(null, "SYSTEM", "system", null, null);
        }

        public static Actor of(String subject, String actorType, String ip, String ua) {
            UUID id = null;
            if (subject != null && !subject.isBlank()) {
                try {
                    id = UUID.fromString(subject.trim());
                } catch (IllegalArgumentException ignored) {
                    id = UUID.nameUUIDFromBytes(subject.trim().getBytes(StandardCharsets.UTF_8));
                }
            }
            return new Actor(id, actorType != null ? actorType : "USER", subject, ip, ua);
        }
    }
}