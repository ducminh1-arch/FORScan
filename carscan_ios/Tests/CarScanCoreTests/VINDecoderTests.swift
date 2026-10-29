import XCTest
@testable import CarScanCore

final class VINDecoderTests: XCTestCase {

    func testStandardModelYearDecoding() {
        XCTAssertEqual(VINDecoder.decodeModelYear(from: "1FA6P8CF5H5123456"), 2017)
        XCTAssertEqual(VINDecoder.decodeModelYear(from: "1FA6P8CF5J5123456"), 2018)
        XCTAssertEqual(VINDecoder.decodeModelYear(from: "1FA6P8CF5K5123456"), 2019)
        XCTAssertEqual(VINDecoder.decodeModelYear(from: "1FA6P8CF5L5123456"), 2020)
        XCTAssertEqual(VINDecoder.decodeModelYear(from: "1FA6P8CF5M5123456"), 2021)
        XCTAssertEqual(VINDecoder.decodeModelYear(from: "1FA6P8CF5N5123456"), 2022)
        XCTAssertEqual(VINDecoder.decodeModelYear(from: "1FA6P8CF5P5123456"), 2023)
        XCTAssertEqual(VINDecoder.decodeModelYear(from: "1FA6P8CF5R5123456"), 2024)
    }

    func testInvalidOrShortVin() {
        XCTAssertNil(VINDecoder.decodeModelYear(from: ""))
        XCTAssertNil(VINDecoder.decodeModelYear(from: "1FA6P8CF"))
        XCTAssertNil(VINDecoder.decodeModelYear(from: "1FA6P8CF5Z5123456"))
    }

    func testManufacturerDecoding() {
        XCTAssertEqual(VINDecoder.decodeMake(from: "1FA6P8CF5M5123456"), "Ford")
        XCTAssertEqual(VINDecoder.decodeMake(from: "WF0AXXWPGAA123456"), "Ford")
        XCTAssertEqual(VINDecoder.decodeMake(from: "JM1BM123456789012"), "Mazda")
    }
}

