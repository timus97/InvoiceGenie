package com.invoicegenie.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EntityId")
class EntityIdTest {

    // Concrete implementation for testing
    private static class TestEntityId extends EntityId {
        public TestEntityId(UUID value) {
            super(value);
        }
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create EntityId with valid UUID")
        void shouldCreateWithValidUuid() {
            UUID uuid = UUID.randomUUID();
            TestEntityId entityId = new TestEntityId(uuid);
            
            assertEquals(uuid, entityId.getValue());
        }

        @Test
        @DisplayName("should throw when value is null")
        void shouldThrowWhenValueNull() {
            assertThrows(NullPointerException.class, () -> new TestEntityId(null));
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when same UUID and type")
        void shouldBeEqualWhenSameUuidAndType() {
            UUID uuid = UUID.randomUUID();
            TestEntityId id1 = new TestEntityId(uuid);
            TestEntityId id2 = new TestEntityId(uuid);
            
            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when different UUIDs")
        void shouldNotBeEqualWhenDifferentUuids() {
            TestEntityId id1 = new TestEntityId(UUID.randomUUID());
            TestEntityId id2 = new TestEntityId(UUID.randomUUID());
            
            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            TestEntityId entityId = new TestEntityId(UUID.randomUUID());
            assertNotEquals(null, entityId);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            TestEntityId entityId = new TestEntityId(UUID.randomUUID());
            assertEquals(entityId, entityId);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            UUID uuid = UUID.randomUUID();
            TestEntityId id1 = new TestEntityId(uuid);
            
            // Another concrete type
            class OtherEntityId extends EntityId {
                public OtherEntityId(UUID value) { super(value); }
            }
            OtherEntityId id2 = new OtherEntityId(uuid);
            
            assertNotEquals(id1, id2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("should return UUID string representation")
        void shouldReturnUuidString() {
            UUID uuid = UUID.randomUUID();
            TestEntityId entityId = new TestEntityId(uuid);
            
            assertEquals(uuid.toString(), entityId.toString());
        }
    }
}
