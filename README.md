# CarScan Lite (Android)

OBD-II & UDS Diagnostic Application for Ford and Mazda vehicles (Android / Kotlin / Jetpack Compose).

## Phase 0 Milestones
- [x] Shared protocol specification in `spec/` (`modules.json`, `pids.json`, `dtc.json`, `tests.json`, `services.json`).
- [x] `MockTransport` simulating full ELM327 protocol and ISO-TP multi-frame responses.
- [x] `WiFiTransport` using Java Sockets TCP (`192.168.0.10:35000` or custom) with socket `soTimeout` and deadline loop.
- [ ] `BtClassicTransport` (Bluetooth RFCOMM SPP) & `BleTransport` (GATT) - Chưa hoàn thành (Mã nguồn đã triển khai kèm bộ chọn thiết bị & xin quyền runtime, nhưng chưa được kiểm chứng trên adapter phần cứng thật; chỉ đánh dấu hoàn thành sau khi test thực tế).
- [x] `ELM327Driver` implementing the initialization sequence: `ATZ` -> `ATE0` -> `ATL0` -> `ATS0` -> `ATH1` -> `ATSP6` -> `ATRV`.
- [x] `ISOTPHandler` parsing Single Frame (SF), First Frame (FF), Consecutive Frames (CF), and decoding multi-frame VIN (Mode 09 PID 02).
- [x] `OBD2Service` & `UDSService` for live data, DTC scanning, and UDS routines.
- [x] Protocol documentation in `docs/protocol.md`.
- [x] Unit and integration tests in `carscan-core/src/test/kotlin`.

## Architecture
```text
UI (Jetpack Compose)
 └─ ViewModel
     └─ Domain: VehicleSession, DtcScanner, LiveDataPoller, Logger, ServiceRunner
         └─ Protocol: OBD2 (Mode 01/02/03/04/07/09), UDS (0x10/0x14/0x19/0x22/0x31/0x3E), ISO-TP
             └─ Adapter driver: ELM327Driver (AT init, parser, timeout, retry)
                 └─ Transport: WiFiTransport | BleTransport | BtClassicTransport | MockTransport
```

## Running with Android Studio / Gradle
Open the `carscan_android` project in Android Studio, or run tests with Gradle:
```bash
./gradlew test
```

# CarScan Lite (iOS)

OBD-II & UDS Diagnostic Application for Ford and Mazda vehicles (iOS / Swift).

## Phase 0 Milestones
- [x] Shared protocol specification in `spec/` (`modules.json`, `pids.json`, `dtc.json`, `tests.json`, `services.json`).
- [x] `MockTransport` simulating full ELM327 protocol and ISO-TP multi-frame responses.
- [x] `WiFiTransport` using Apple Network framework (`NWConnection`).
- [ ] `BleTransport` CoreBluetooth GATT interface - Chưa làm / Chưa hoàn thành (Chỉ đánh dấu hoàn thành sau khi triển khai và kiểm chứng trên thiết bị thật).
- [x] `ELM327Driver` implementing the initialization sequence: `ATZ` -> `ATE0` -> `ATL0` -> `ATS0` -> `ATH1` -> `ATSP6` -> `ATRV`.
- [x] `ISOTPHandler` parsing Single Frame (SF), First Frame (FF), Consecutive Frames (CF), and decoding multi-frame VIN (Mode 09 PID 02).
- [x] `OBD2Service` & `UDSService` for live data, DTC scanning, and UDS routines.
- [x] Protocol documentation in `docs/protocol.md`.
- [x] Comprehensive unit tests for driver, ISO-TP, PID decoders, and integration.

## Architecture
```text
UI (SwiftUI)
 └─ ViewModel
     └─ Domain: VehicleSession, DtcScanner, LiveDataPoller, Logger, ServiceRunner
         └─ Protocol: OBD2 (Mode 01/02/03/04/07/09), UDS (0x10/0x14/0x19/0x22/0x31/0x3E), ISO-TP
             └─ Adapter driver: ELM327Driver (AT init, parser, timeout, retry)
                 └─ Transport: WiFiTransport | BleTransport | MockTransport
```

## Running Tests
Run with Xcode or `swift test` (macOS/Linux):
```bash
swift test
```
# CarScan Lite (iOS)

OBD-II & UDS Diagnostic Application for Ford and Mazda vehicles (iOS / Swift).

## Phase 0 Milestones
- [x] Shared protocol specification in `spec/` (`modules.json`, `pids.json`, `dtc.json`, `tests.json`, `services.json`).
- [x] `MockTransport` simulating full ELM327 protocol and ISO-TP multi-frame responses.
- [x] `WiFiTransport` using Apple Network framework (`NWConnection`).
- [ ] `BleTransport` CoreBluetooth GATT interface - Chưa làm / Chưa hoàn thành (Chỉ đánh dấu hoàn thành sau khi triển khai và kiểm chứng trên thiết bị thật).
- [x] `ELM327Driver` implementing the initialization sequence: `ATZ` -> `ATE0` -> `ATL0` -> `ATS0` -> `ATH1` -> `ATSP6` -> `ATRV`.
- [x] `ISOTPHandler` parsing Single Frame (SF), First Frame (FF), Consecutive Frames (CF), and decoding multi-frame VIN (Mode 09 PID 02).
- [x] `OBD2Service` & `UDSService` for live data, DTC scanning, and UDS routines.
- [x] Protocol documentation in `docs/protocol.md`.
- [x] Comprehensive unit tests for driver, ISO-TP, PID decoders, and integration.

## Architecture
```text
UI (SwiftUI)
 └─ ViewModel
     └─ Domain: VehicleSession, DtcScanner, LiveDataPoller, Logger, ServiceRunner
         └─ Protocol: OBD2 (Mode 01/02/03/04/07/09), UDS (0x10/0x14/0x19/0x22/0x31/0x3E), ISO-TP
             └─ Adapter driver: ELM327Driver (AT init, parser, timeout, retry)
                 └─ Transport: WiFiTransport | BleTransport | MockTransport
```

## Running Tests
Run with Xcode or `swift test` (macOS/Linux):
```bash
swift test
```
