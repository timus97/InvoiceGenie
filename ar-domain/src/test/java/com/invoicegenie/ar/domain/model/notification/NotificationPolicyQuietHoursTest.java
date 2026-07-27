package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationPolicy quiet hours (PP-010)")
class NotificationPolicyQuietHoursTest {

    private final TenantId tenantId = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    @DisplayName("null quiet hours means never quiet")
    void nullQuietHours() {
        NotificationPolicy p = NotificationPolicy.defaults(tenantId);
        assertFalse(p.isInQuietHours(Instant.now()));
        assertEquals(Instant.parse("2026-07-27T12:00:00Z"),
                p.nextQuietHoursEnd(Instant.parse("2026-07-27T12:00:00Z")));
    }

    @Test
    @DisplayName("same-day window: 22:00-06:00 spans midnight")
    void spansMidnight() {
        NotificationPolicy p = NotificationPolicy.defaults(tenantId);
        // 22:00 = 1320, 06:00 = 360
        p.update(true, true, false, true, true, 3, true, null, null, null,
                1320, 360, "UTC", false);

        Instant evening = ZonedDateTime.of(LocalDate.of(2026, 7, 27), LocalTime.of(23, 0), ZoneId.of("UTC")).toInstant();
        Instant morningQuiet = ZonedDateTime.of(LocalDate.of(2026, 7, 28), LocalTime.of(3, 0), ZoneId.of("UTC")).toInstant();
        Instant daytime = ZonedDateTime.of(LocalDate.of(2026, 7, 28), LocalTime.of(10, 0), ZoneId.of("UTC")).toInstant();

        assertTrue(p.isInQuietHours(evening));
        assertTrue(p.isInQuietHours(morningQuiet));
        assertFalse(p.isInQuietHours(daytime));

        Instant resume = p.nextQuietHoursEnd(evening);
        ZonedDateTime rz = resume.atZone(ZoneId.of("UTC"));
        assertEquals(6, rz.getHour());
        assertEquals(28, rz.getDayOfMonth());
    }

    @Test
    @DisplayName("same-day window 09:00-17:00")
    void sameDayWindow() {
        NotificationPolicy p = NotificationPolicy.defaults(tenantId);
        // 09:00=540, 17:00=1020
        p.update(true, true, false, true, true, 3, true, null, null, null,
                540, 1020, "UTC", false);

        Instant mid = ZonedDateTime.of(LocalDate.of(2026, 7, 27), LocalTime.of(12, 0), ZoneId.of("UTC")).toInstant();
        Instant early = ZonedDateTime.of(LocalDate.of(2026, 7, 27), LocalTime.of(8, 0), ZoneId.of("UTC")).toInstant();
        assertTrue(p.isInQuietHours(mid));
        assertFalse(p.isInQuietHours(early));

        Instant end = p.nextQuietHoursEnd(mid);
        assertEquals(17, end.atZone(ZoneId.of("UTC")).getHour());
    }

    @Test
    @DisplayName("attachPdfOnIssue and timezone defaults")
    void flagsAndTimezone() {
        NotificationPolicy p = NotificationPolicy.defaults(tenantId);
        assertFalse(p.isAttachPdfOnIssue());
        assertEquals("UTC", p.getTimezone());
        p.update(true, true, false, true, true, 3, true, null, null, null,
                null, null, "America/New_York", true);
        assertTrue(p.isAttachPdfOnIssue());
        assertEquals("America/New_York", p.getTimezone());
    }

    @Test
    @DisplayName("invalid quiet minute rejected")
    void invalidMinute() {
        NotificationPolicy p = NotificationPolicy.defaults(tenantId);
        assertThrows(IllegalArgumentException.class, () ->
                p.update(true, true, false, true, true, 3, true, null, null, null,
                        2000, 100, "UTC", false));
    }
}
