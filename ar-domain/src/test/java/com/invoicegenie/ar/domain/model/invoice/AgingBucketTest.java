package com.invoicegenie.ar.domain.model.invoice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgingBucket")
class AgingBucketTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("should have all expected buckets")
        void shouldHaveAllExpectedBuckets() {
            AgingBucket[] buckets = AgingBucket.values();
            
            assertEquals(4, buckets.length);
            assertNotNull(AgingBucket.BUCKET_0_30);
            assertNotNull(AgingBucket.BUCKET_31_60);
            assertNotNull(AgingBucket.BUCKET_61_90);
            assertNotNull(AgingBucket.BUCKET_90_PLUS);
        }

        @Test
        @DisplayName("should have correct labels")
        void shouldHaveCorrectLabels() {
            assertEquals("0-30 Days", AgingBucket.BUCKET_0_30.getLabel());
            assertEquals("31-60 Days", AgingBucket.BUCKET_31_60.getLabel());
            assertEquals("61-90 Days", AgingBucket.BUCKET_61_90.getLabel());
            assertEquals("90+ Days", AgingBucket.BUCKET_90_PLUS.getLabel());
        }

        @Test
        @DisplayName("should have correct day ranges")
        void shouldHaveCorrectDayRanges() {
            assertEquals(0, AgingBucket.BUCKET_0_30.getMinDays());
            assertEquals(30, AgingBucket.BUCKET_0_30.getMaxDays());
            
            assertEquals(31, AgingBucket.BUCKET_31_60.getMinDays());
            assertEquals(60, AgingBucket.BUCKET_31_60.getMaxDays());
            
            assertEquals(61, AgingBucket.BUCKET_61_90.getMinDays());
            assertEquals(90, AgingBucket.BUCKET_61_90.getMaxDays());
            
            assertEquals(91, AgingBucket.BUCKET_90_PLUS.getMinDays());
            assertEquals(Integer.MAX_VALUE, AgingBucket.BUCKET_90_PLUS.getMaxDays());
        }
    }

    @Nested
    @DisplayName("fromDaysOverdue")
    class FromDaysOverdue {

        @Test
        @DisplayName("should return BUCKET_0_30 for 0 days")
        void shouldReturnBucket0_30ForZeroDays() {
            assertEquals(AgingBucket.BUCKET_0_30, AgingBucket.fromDaysOverdue(0));
        }

        @Test
        @DisplayName("should return BUCKET_0_30 for 30 days")
        void shouldReturnBucket0_30For30Days() {
            assertEquals(AgingBucket.BUCKET_0_30, AgingBucket.fromDaysOverdue(30));
        }

        @Test
        @DisplayName("should return BUCKET_31_60 for 31 days")
        void shouldReturnBucket31_60For31Days() {
            assertEquals(AgingBucket.BUCKET_31_60, AgingBucket.fromDaysOverdue(31));
        }

        @Test
        @DisplayName("should return BUCKET_31_60 for 60 days")
        void shouldReturnBucket31_60For60Days() {
            assertEquals(AgingBucket.BUCKET_31_60, AgingBucket.fromDaysOverdue(60));
        }

        @Test
        @DisplayName("should return BUCKET_61_90 for 61 days")
        void shouldReturnBucket61_90For61Days() {
            assertEquals(AgingBucket.BUCKET_61_90, AgingBucket.fromDaysOverdue(61));
        }

        @Test
        @DisplayName("should return BUCKET_61_90 for 90 days")
        void shouldReturnBucket61_90For90Days() {
            assertEquals(AgingBucket.BUCKET_61_90, AgingBucket.fromDaysOverdue(90));
        }

        @Test
        @DisplayName("should return BUCKET_90_PLUS for 91 days")
        void shouldReturnBucket90_PlusFor91Days() {
            assertEquals(AgingBucket.BUCKET_90_PLUS, AgingBucket.fromDaysOverdue(91));
        }

        @Test
        @DisplayName("should return BUCKET_90_PLUS for very large days")
        void shouldReturnBucket90_PlusForLargeDays() {
            assertEquals(AgingBucket.BUCKET_90_PLUS, AgingBucket.fromDaysOverdue(365));
        }
    }

    @Nested
    @DisplayName("Early Payment Eligibility")
    class EarlyPaymentEligibility {

        @Test
        @DisplayName("BUCKET_0_30 should be early payment eligible")
        void bucket0_30ShouldBeEarlyPaymentEligible() {
            assertTrue(AgingBucket.BUCKET_0_30.isEarlyPaymentEligible());
        }

        @Test
        @DisplayName("Other buckets should not be early payment eligible")
        void otherBucketsShouldNotBeEarlyPaymentEligible() {
            assertFalse(AgingBucket.BUCKET_31_60.isEarlyPaymentEligible());
            assertFalse(AgingBucket.BUCKET_61_90.isEarlyPaymentEligible());
            assertFalse(AgingBucket.BUCKET_90_PLUS.isEarlyPaymentEligible());
        }
    }
}
