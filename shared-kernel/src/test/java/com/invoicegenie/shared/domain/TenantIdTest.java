package com.invoicegenie.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantId Value Object")
class TenantIdTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create TenantId with valid UUID")
        void shouldCreateWithValidUuid() {
            UUID uuid = UUID.randomUUID();
            TenantId tenantId = new TenantId(uuid);
            
            assertEquals(uuid, tenantId.getValue());
        }

        @Test
        @DisplayName("should create TenantId using factory method with UUID")
        void shouldCreateWithFactoryMethodUuid() {
            UUID uuid = UUID.randomUUID();
            TenantId tenantId = TenantId.of(uuid);
            
            assertEquals(uuid, tenantId.getValue());
        }

        @Test
        @DisplayName("should create TenantId using factory method with String")
        void shouldCreateWithFactoryMethodString() {
            UUID uuid = UUID.randomUUID();
            TenantId tenantId = TenantId.of(uuid.toString());
            
            assertEquals(uuid, tenantId.getValue());
        }

        @Test
        @DisplayName("should throw when value is null")
        void shouldThrowWhenValueNull() {
            assertThrows(NullPointerException.class, () -> new TenantId(null));
        }

        @Test
        @DisplayName("should throw when string is not valid UUID")
        void shouldThrowWhenStringNotValidUuid() {
            assertThrows(IllegalArgumentException.class, () -> TenantId.of("not-a-uuid"));
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when same UUID")
        void shouldBeEqualWhenSameUuid() {
            UUID uuid = UUID.randomUUID();
            TenantId t1 = TenantId.of(uuid);
            TenantId t2 = TenantId.of(uuid);
            
            assertEquals(t1, t2);
            assertEquals(t1.hashCode(), t2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when different UUIDs")
        void shouldNotBeEqualWhenDifferentUuids() {
            TenantId t1 = TenantId.of(UUID.randomUUID());
            TenantId t2 = TenantId.of(UUID.randomUUID());
            
            assertNotEquals(t1, t2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            assertNotEquals(null, tenantId);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            assertEquals(tenantId, tenantId);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("should return UUID string representation")
        void shouldReturnUuidString() {
            UUID uuid = UUID.randomUUID();
            TenantId tenantId = TenantId.of(uuid);
            
            assertEquals(uuid.toString(), tenantId.toString());
        }
    }
}
