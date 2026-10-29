# CarScan Lite - OBD-II, UDS & CAN Adapter Protocol Specification

## 1. Adapter Identification & Profile Detection

CarScan Lite detects adapter capabilities dynamically during the initialization phase using standard and proprietary AT/ST commands:

| Step | Command | Expected Output | Recognized Family | Bus Switching Mode |
|---|---|---|---|---|
| 1 | `ATZ` / `ATWS` | Reset banner | Generic ELM327 | None (or manual switch) |
| 2 | `ATI` | `ELM327 v...` or `vLinker...` | ELM327 or vLinker | Check Step 3 & 4 |
| 3 | `STI` | `STN...` or `OBDLink...` | STN11xx / STN21xx | Electronic auto-switching (`STP`) |
| 4 | `AT@1` | `OBDLink MX+` / `vLinker...` | OBDLink / vLinker | Electronic auto-switching |

---

### 1.1 Hardware Profiles & Specification Reference (`spec/adapters.json`)

#### 1.1.1 OBDLink STN (`OBDLINK_STN`) — VERIFIED
- **Nhận diện họ chip**: Lệnh `STI` trả về chuỗi chứa `STN` hoặc `OBDLink`.
- **Nhận diện model phần cứng**: Lệnh `STDI` (hoặc `AT@1`) trả về định danh model chi tiết:
  - **OBDLink EX** (USB) & **OBDLink MX / MX+** (Bluetooth): Trang bị đầy đủ phần cứng thu phát HS-CAN và MS-CAN (chân 3/11) với mạch chuyển bus điện tử tự động (`hasMsCan: true`, `autoBusSwitch: true`).
  - **OBDLink LX** (Bluetooth) & **OBDLink CX** (BLE): Chỉ hỗ trợ HS-CAN (chân 6/14), **không có mạch thu phát MS-CAN** trên chân 3/11 (`hasMsCan: false`). App sẽ hiển thị thông báo rõ ràng cho người dùng và tuyệt đối không gửi lệnh chuyển bus sang MS-CAN khi kết nối với các adapter này.
- **Tài liệu tham chiếu chính thức**:
  - *OBDLink Family Reference and Programming Manual (FRPM) rev F*, mục 8.6 "Set Protocol (STP)", trang 34.
  - Link tải chính thức: [https://www.scantool.net/scantool/downloads/234/stn1100-frpm.pdf](https://www.scantool.net/scantool/downloads/234/stn1100-frpm.pdf)
- **Tóm tắt đặc tả lệnh STP theo mục 8.6, trang 34**:
  Lệnh `STP h` trực tiếp kích hoạt preset giao thức phần cứng tương ứng với mã `h` mà không thực hiện dò tìm tự động như lệnh `ATSP`:
  - `STP 31`: Preset giao thức ISO 11898 (CAN thô, 11-bit ID, tốc độ 500 kbps trên chân OBD 6/14). Chế độ này không tự động ghép các frame ISO-TP (multi-frame).
  - `STP 33`: Preset giao thức ISO 15765-4 (11-bit ID, tốc độ 500 kbps trên chân OBD 6/14). Chip tự động quản lý Flow Control và ghép các gói tin Consecutive Frame cho mạng **HS-CAN**. Đây là lệnh tiêu chuẩn mà CarScan Lite gửi khi làm việc với HS-CAN.
  - `STP 51`: Preset giao thức ISO 11898 (CAN thô, 11-bit ID, tốc độ 125 kbps trên chân OBD 3/11 phụ). Không tự động ghép frame ISO-TP.
  - `STP 53`: Preset giao thức ISO 15765-4 (11-bit ID, tốc độ 125 kbps trên chân OBD 3/11 phụ). Chip tự động điều khiển đường chuyển mạch bên trong sang chân 3/11, tự động xử lý Flow Control và ghép frame ISO-TP cho mạng **MS-CAN**. Đây là lệnh tiêu chuẩn mà CarScan Lite gửi khi chuyển sang MS-CAN.

#### 1.1.2 vLinker Series (`VLINKER_FS`) — UNVERIFIED
- **Nhận diện**: Lệnh `ATI` trả về chuỗi chứa `vLinker` hoặc `Vgate`.
- **Trạng thái**: `verified: false` (**Chưa được kiểm chứng qua tài liệu OEM chính thức**).
- **Kiểm tra tài liệu**:
  - Tra cứu tài liệu chính thức từ Vgate (Chendu Icar Technology Co., Ltd. tại [https://www.vgatemall.com/downloadcenter](https://www.vgatemall.com/downloadcenter)): Không có tài liệu nào công bố cú pháp chính thức của các lệnh `ATHSCAN` hay `ATMSCAN`. Các lệnh này bắt nguồn từ phân tích dịch ngược trong cộng đồng.
- **Quy tắc an toàn trong code**:
  - Đánh dấu rõ `unverified` trong `spec/adapters.json` và trong comment mã nguồn driver.
  - Nếu adapter phản hồi ký tự `?` (lệnh không hỗ trợ) hoặc gặp lỗi timeout, driver **không được tiếp tục** như thể đã chuyển bus thành công, mà phải ném ngoại lệ `BusSwitchFailedException` và UI hiển thị cảnh báo `"Bus switch failed"`.

#### 1.1.3 ELM327 Generic (`ELM327_GENERIC`) — VERIFIED (Chuyển mạch cơ học)
- **Nhận diện**: Lệnh `ATI` trả về `ELM327 v1.x` hoặc `v2.x`.
- **Tài liệu tham chiếu**: *ELM Electronics ELM327 Data Sheet*, mục "CAN (ISO 15765-4) Interface" ([https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf)).
- **Tóm tắt cơ chế chuyển bus**:
  Chip ELM327 tiêu chuẩn chỉ có 1 bộ thu phát CAN kết nối cố định vào chân 6 và 14 của giắc OBD-II. Chip không có multiplexer tích hợp bên trong để chuyển mạch sang các chân khác. Để giao tiếp với mạng Ford MS-CAN (chân 3/11), adapter phải được độ thêm công tắc gạt vật lý DPDT bên ngoài đảo dây CAN High / CAN Low.
  Khi cần chuyển bus, app hiển thị thông báo yêu cầu người dùng gạt công tắc phần cứng sang MS-CAN (chân 3/11) hoặc HS-CAN (chân 6/14), kèm theo lệnh thiết lập giao thức `ATSPB` (cho MS-CAN 125k) hoặc `ATSP6` (cho HS-CAN 500k).

---

### 1.2 Bus Switching Failure Specification
When switching between CAN buses (e.g., from HS-CAN to MS-CAN or vice versa):
1. **Rejection Detection**: If any command in the bus switching sequence returns `?`, `BUS INIT: ERROR`, `CAN ERROR`, or times out:
   - The driver throws `BusSwitchFailedException("Bus switch failed")`.
   - The driver **MUST NOT** update its `currentBus` state.
2. **UI & Scan Halting**:
   - The UI / ViewModel catches `BusSwitchFailedException`.
   - The UI displays the exact alert message: **`"Bus switch failed"`**.
   - The multi-module DTC scan / service procedure **MUST HALT IMMEDIATELY** for that bus. Under no circumstances should the app proceed to poll modules on the new bus as if switching had succeeded.

---

## 2. Adapter Artifacts & Error Strings Handling

The driver explicitly detects adapter error and status strings, preventing false positive data parsing:

| String Pattern | Meaning | Driver Handling |
|---|---|---|
| `STOPPED` | Adapter aborted transmission or wait loop | Retry command with backoff, abort if repeated |
| `SEARCHING...` | Adapter attempting automatic protocol detection | Wait for prompt `>` or timeout |
| `UNABLE TO CONNECT` | No CAN bus traffic or wrong baud rate | Trigger connection alert; verify ignition and adapter pins |
| `NO DATA` | Addressed ECU did not reply (timed out) | Return empty response / module offline. **Do not** display as 0 errors |
| `CAN ERROR` | Bus error (framing, bit stuffing, or ACK missing) | Log error, reset protocol state via `ATPC` or `ATSP` |
| `BUS BUSY` | High CAN bus utilization or dominant bus state | Delay 50ms and retry |
| `BUFFER FULL` | Multi-PID request or response overflowed RX buffer | Trigger adaptive fallback: drop PID batch size (e.g. 6 -> 4 -> 2 -> 1) |
| `?` | Command syntax unrecognized by adapter | Reject command or trigger `BusSwitchFailedException` |

---

## 3. UDS Diagnostics (ISO 14229) & Session Policy

### 3.1 Strict Safety and Read-Only Rules
- **Default Session Only (`0x10 0x01`)**:
  All read-only diagnostic operations (DTC read, PID polling) execute solely within the default diagnostic session.
- **NO Security Access (`0x27`)**:
  Security access is prohibited. No authentication seeds or keys are requested.
- **NO Flash / As-Built / EEPROM Writes**:
  No configuration writes or calibration downloads are permitted.

### 3.2 UDS Service 0x19: ReadDTCInformation
- **Command**: `19 02 AF`
  - Subfunction `0x02`: `reportDTCByStatusMask`
  - Status Mask `0xAF`:
    - Bit 0: TestFailed
    - Bit 1: TestFailedThisMonitoringCycle
    - Bit 2: PendingDTC
    - Bit 3: ConfirmedDTC
    - Bit 5: TestNotCompletedSinceLastClear
    - Bit 7: WarningIndicatorRequested
- **Positive Response**: `59 02 [AvailabilityMask] [DTC_Byte0] [DTC_Byte1] [DTC_Byte2] [StatusByte] ...`
  - 3-byte DTC representation + 1-byte status mask per DTC.

### 3.3 UDS Service 0x14: ClearDiagnosticInformation
- **Command**: `14 FF FF FF` (Clears all DTC groups for the target module).
- **Execution Policy**:
  - Never broadcast to functional address (`7DF`).
  - Executed **strictly per individual module** (e.g., target `7E0` for PCM, `726` for BCM).
  - Requires user confirmation dialog explaining that DTC clearing resets readiness monitors and adaptation memories.

### 3.4 Unresponsive Module vs. No Error Codes
- **Unresponsive / Offline Module**:
  If a module fails to answer (`NO DATA`, timeout, or bus error):
  - Mark status as `No Response / Offline`.
  - **NEVER** display "No error codes" for an unresponsive module.
- **No Error Codes**:
  Only displayed when a module returns a valid positive response (e.g. `59 02 FF` with zero DTC records).

---

## 4. ISO-TP Transport Layer (ISO 15765-2)

### 4.1 Frame Types
1. **Single Frame (SF - Type 0)**:
   - Data length: 1 to 7 bytes.
   - Format: `[0x0 | DL] [Data 1..7]`
2. **First Frame (FF - Type 1)**:
   - Total payload length: 8 to 4095 bytes.
   - Format: `[0x1 | DL_High] [DL_Low] [Data 1..6]`
3. **Consecutive Frame (CF - Type 2)**:
   - Sequence number: 0 to 15 (cycles `21`, `22`, ... `2F`, `20`).
   - Format: `[0x2 | SN] [Data 1..7]`
4. **Flow Control (FC - Type 3)**:
   - Flow Status (`0x0` = ContinueToSend, `0x1` = Wait, `0x2` = Overflow).
   - Block Size (BS) and Separation Time (STmin).

### 4.2 Negative Response Code 0x78 (`7F xx 78` ResponsePending)
- When an ECU returns `7F [SID] 78` (NRC 0x78: `requestCorrectlyReceived-ResponsePending`):
  - **This is NOT an error.**
  - The driver resets its response timeout timer and continues awaiting the pending positive response from the ECU.
