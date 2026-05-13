package com.app.common.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatScoreTest {

    @Test
    fun zero() {
        assertEquals("0", 0L.formatScore())
        assertEquals("0", 0.formatScore())
    }

    @Test
    fun under_thousand_no_separator() {
        assertEquals("1", 1L.formatScore())
        assertEquals("42", 42L.formatScore())
        assertEquals("999", 999L.formatScore())
    }

    @Test
    fun thousands() {
        assertEquals("1,000", 1_000L.formatScore())
        assertEquals("12,345", 12_345L.formatScore())
        assertEquals("999,999", 999_999L.formatScore())
    }

    @Test
    fun millions_and_above() {
        assertEquals("1,000,000", 1_000_000L.formatScore())
        assertEquals("123,456,789", 123_456_789L.formatScore())
    }

    @Test
    fun negatives_have_leading_minus() {
        assertEquals("-1", (-1L).formatScore())
        assertEquals("-1,234", (-1_234L).formatScore())
        assertEquals("-1,000,000", (-1_000_000L).formatScore())
    }

    @Test
    fun int_overload_matches_long() {
        assertEquals(1_234L.formatScore(), 1_234.formatScore())
        assertEquals((-1).toLong().formatScore(), (-1).formatScore())
    }

    @Test
    fun max_long_is_grouped() {
        assertEquals("9,223,372,036,854,775,807", Long.MAX_VALUE.formatScore())
    }
}
