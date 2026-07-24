package com.invoicegenie.shared.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActorContextTest {

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Test
    void setAndCurrent() {
        ActorContext.Actor actor = ActorContext.Actor.of("clerk-1", "USER", "10.0.0.1", "junit");
        ActorContext.set(actor);
        assertTrue(ActorContext.current().isPresent());
        assertEquals("clerk-1", ActorContext.current().get().subject());
        assertEquals("USER", ActorContext.current().get().actorType());
        assertEquals("10.0.0.1", ActorContext.current().get().ipAddress());
        assertEquals("junit", ActorContext.current().get().userAgent());
        assertNotNull(ActorContext.current().get().actorId());
    }

    @Test
    void ofParsesUuidSubject() {
        UUID id = UUID.randomUUID();
        ActorContext.Actor actor = ActorContext.Actor.of(id.toString(), "API", null, null);
        assertEquals(id, actor.actorId());
        assertEquals("API", actor.actorType());
    }

    @Test
    void ofNullSubject() {
        ActorContext.Actor actor = ActorContext.Actor.of(null, null, null, null);
        assertNull(actor.actorId());
        assertEquals("USER", actor.actorType());
    }

    @Test
    void ofBlankSubject() {
        ActorContext.Actor actor = ActorContext.Actor.of("  ", "API", "1.1.1.1", "ua");
        assertNull(actor.actorId());
        assertEquals("API", actor.actorType());
    }

    @Test
    void systemActor() {
        ActorContext.Actor sys = ActorContext.Actor.system();
        assertEquals("SYSTEM", sys.actorType());
        assertEquals("system", sys.subject());
        assertNull(sys.actorId());
    }

    @Test
    void requireWhenEmptyReturnsSystem() {
        assertTrue(ActorContext.current().isEmpty());
        ActorContext.Actor a = ActorContext.require();
        assertEquals("SYSTEM", a.actorType());
    }

    @Test
    void requireWhenSetReturnsCurrent() {
        ActorContext.set(ActorContext.Actor.of("x", "API", null, null));
        assertEquals("x", ActorContext.require().subject());
    }

    @Test
    void clearRemovesActor() {
        ActorContext.set(ActorContext.Actor.system());
        ActorContext.clear();
        assertTrue(ActorContext.current().isEmpty());
    }

    @Test
    void setNullThrows() {
        assertThrows(NullPointerException.class, () -> ActorContext.set(null));
    }
}