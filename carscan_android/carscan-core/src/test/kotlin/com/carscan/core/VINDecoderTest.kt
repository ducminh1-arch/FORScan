package com.carscan.core

import com.carscan.core.protocol.VINDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VINDecoderTest {

    @Test
    fun testStandardModelYearDecoding() {
        // Standard ISO 3779 10th character mapping:
        // H = 2017
        assertEquals(2017, VINDecoder.decodeModelYear("1FA6P8CF5H5123456"))
        // J = 2018
        assertEquals(2018, VINDecoder.decodeModelYear("1FA6P8CF5J5123456"))
        // K = 2019
        assertEquals(2019, VINDecoder.decodeModelYear("1FA6P8CF5K5123456"))
        // L = 2020
        assertEquals(2020, VINDecoder.decodeModelYear("1FA6P8CF5L5123456"))
        // M = 2021
        assertEquals(2021, VINDecoder.decodeModelYear("1FA6P8CF5M5123456"))
        // N = 2022
        assertEquals(2022, VINDecoder.decodeModelYear("1FA6P8CF5N5123456"))
        // P = 2023
        assertEquals(2023, VINDecoder.decodeModelYear("1FA6P8CF5P5123456"))
        // R = 2024
        assertEquals(2024, VINDecoder.decodeModelYear("1FA6P8CF5R5123456"))
    }

    @Test
    fun testInvalidOrShortVin() {
        assertNull(VINDecoder.decodeModelYear(""))
        assertNull(VINDecoder.decodeModelYear("1FA6P8CF")) // only 8 chars
        assertNull(VINDecoder.decodeModelYear("1FA6P8CF5Z5123456")) // Z is invalid in ISO 3779
    }

    @Test
    fun testManufacturerDecoding() {
        assertEquals("Ford", VINDecoder.decodeMake("1FA6P8CF5M5123456"))
        assertEquals("Ford", VINDecoder.decodeMake("WF0AXXWPGAA123456"))
        assertEquals("Mazda", VINDecoder.decodeMake("JM1BM123456789012"))
    }
}
