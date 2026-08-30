package com.strings.app.domain.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OtpDetectorTest {
    private lateinit var detector: OtpDetector

    @Before
    fun setUp() {
        detector = OtpDetector()
    }

    @Test
    fun detectsCodeLeadingWithKeyword() {
        assertEquals("123456", detector.detect("123456 is your OTP for login. Do not share."))
    }

    @Test
    fun detectsVerificationCode() {
        assertEquals("4821", detector.detect("Your verification code is 4821."))
    }

    @Test
    fun detectsCodeAfterColon() {
        assertEquals("928172", detector.detect("OTP: 928172. Valid for 10 minutes."))
    }

    @Test
    fun returnsNullWhenNoKeyword() {
        assertNull(detector.detect("Your package 12345 has been delivered."))
    }

    @Test
    fun ignoresBankAmountWithoutKeyword() {
        assertNull(
            detector.detect(
                "Rs 2,500.00 debited from a/c **1234 on 06-Jun. Avl Bal: Rs 45230"
            )
        )
    }

    @Test
    fun picksCodeNearestKeywordNotAmount() {
        val body: String = "Use code 778812 to verify. This is unrelated to Rs 50000 spent."
        assertEquals("778812", detector.detect(body))
    }

    @Test
    fun ignoresAmountAdjacentToCurrencyEvenWithKeyword() {
        val body: String = "Your OTP is 314159. Ignore Rs 99999 mentioned here."
        assertEquals("314159", detector.detect(body))
    }

    @Test
    fun returnsNullForBlankBody() {
        assertNull(detector.detect("   "))
    }

    @Test
    fun ignoresShortDigitGroups() {
        assertNull(detector.detect("Your code is 12."))
    }

    @Test
    fun ignoresKeywordSubstringInsideWord() {
        assertNull(
            detector.detect(
                "Your shopping voucher order has been placed. For support, call 1800 570 1955."
            )
        )
    }

    @Test
    fun ignoresGroupedSupportNumberEvenWithKeyword() {
        val body: String = "Your code is 4821. For help dial 1800 570 1955 anytime."
        assertEquals("4821", detector.detect(body))
    }
}
