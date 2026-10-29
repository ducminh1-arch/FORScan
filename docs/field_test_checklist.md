# CarScan Lite - Quy Trình Kiểm Thử Đọc Thực Tế Trên Xe (Field Test Checklist)

Tài liệu này hướng dẫn chi tiết các bước kiểm thử phần mềm CarScan Lite (Android & iOS) trên xe thật (Ford/Mazda) trong điều kiện an toàn tuyệt đối.

---

## 1. Nguyên Tắc An Toàn & Chuẩn Bị (Safety Preconditions)

> [!CAUTION]
> **CHẾ ĐỘ AN TOÀN TUYỆT ĐỐI (READ-ONLY ONLY)**:
> Trong suốt quá trình field test này, phần mềm chỉ thực hiện các thao tác **ĐỌC DỮ LIỆU** (Read-Only).
> **TUYỆT ĐỐI KHÔNG BẤM "CLEAR DTCS"** (Xóa lỗi) và **KHÔNG BẤM CHẠY CÁC TEST / SERVICE PROCEDURES** (để tránh xóa dữ liệu học thích ứng Adaptation / Readiness Monitors của xe).

### 1.1 Trạng Thái Xe
- **Khóa điện**: Bật sang vị trí **ON** (KOEO - Key On, Engine Off). Đèn báo trên bảng đồng hồ (IPC) sáng. **Động cơ KHÔNG nổ máy**.
- **Vị trí cần số**: Về số **P** (xe số tự động) hoặc **Mo (N)** (xe số sàn).
- **Phanh tay**: Kéo phanh tay an toàn.
- **Điện áp ắc quy**: Đo tối thiểu $\ge 12.0\text{V}$. Nếu ắc quy yếu ($< 11.8\text{V}$), cần kết nối bộ sạc đệm ắc quy ổn định trước khi test để tránh sụt áp khi xe bật các tải điện (quạt, đèn ban ngày).

### 1.2 Thiết Bị & Adapter
- **Adapter phần cứng khuyên dùng**:
  - OBDLink EX (USB - trên PC/Android OTG) hoặc OBDLink MX+ (Bluetooth - iOS/Android).
  - vLinker FS / FD+ (USB / BLE).
  - Hoặc ELM327 phiên bản có công tắc gạt HS/MS-CAN chuyển mạch cơ học.
- **Thiết bị điều khiển**:
  - Kết nối Wi-Fi adapter (thường là `192.168.0.10:35000`) hoặc ghép đôi Bluetooth BLE.
  - **Lưu ý**: Tắt 4G/5G (Mobile Data) trên điện thoại để hệ điều hành không tự ngắt kết nối mạng Wi-Fi của adapter khi phát hiện mạng Wi-Fi này không có Internet ra ngoài.

---

## 2. Các Bước Kiểm Thử Chi Tiết (Step-by-Step Field Test)

### Bước 1: Khởi Động & Nhận Diện Adapter (Adapter Identification)
1. Cắm adapter vào cổng OBD-II của xe (vị trí thường nằm dưới vô lăng bên trái).
2. Kiểm tra đèn nguồn LED trên adapter sáng ổn định.
3. Mở ứng dụng CarScan Lite, tắt chế độ Demo (Demo Mode: OFF).
4. Chọn giao thức kết nối (Wi-Fi hoặc Bluetooth) và bấm **Connect**.
5. **Kiểm tra thông tin nhận diện Adapter Profile**:
   - Nếu là OBDLink EX hoặc MX+: Profile hiển thị `OBDLINK_STN`, model `OBDLink EX` hoặc `OBDLink MX+`, cờ `hasMsCan: true`, `autoBusSwitch: true`.
   - Nếu là OBDLink LX hoặc CX: Profile hiển thị `OBDLINK_STN`, model `OBDLink LX/CX`, cờ `hasMsCan: false`. App hiển thị cảnh báo adapter không có phần cứng MS-CAN.
   - Nếu là vLinker: Profile hiển thị `VLINKER_FS`, cờ `verified: false`.
   - Nếu là ELM327 tiêu chuẩn: Profile hiển thị `ELM327_GENERIC`, cờ `autoBusSwitch: false`.
6. **Bật Raw Command Trace**:
   - Bật chức năng "Record Raw Trace" trong màn hình Log hoặc Settings.
7. **Hành động**: Vào màn hình **Log**, bấm xuất file trace đầu tiên: `trace_step1_adapter_init.txt`.

---

### Bước 2: Đọc Thông Tin Xe & Điện Áp (Vehicle Information & Voltage)
1. Điều hướng đến màn hình **Vehicle**.
2. **Kiểm tra số VIN**:
   - App gửi lệnh Mode 09 PID 02 (`0902`).
   - Kiểm tra số VIN 17 ký tự giải mã được có trùng khớp với số VIN dập trên kính lái hoặc sổ đăng kiểm hay không.
3. **Kiểm tra điện áp ắc quy**:
   - Đối chiếu điện áp đọc từ chân OBD-II (`ATRV`, thường 12.4V - 12.8V).
   - Đối chiếu điện áp ECU báo về qua Mode 01 PID 42 (nếu xe hỗ trợ).
4. **Kiểm tra danh sách ECU phản hồi**:
   - Kiểm tra các ECU trả lời broadcast `0100` (dải ID `7E8` - `7EF`).
5. **Hành động**: Xuất file trace: `trace_step2_vehicle_info.txt`.

---

### Bước 3: Đọc Dữ Liệu Sống & Ghi Log CSV (Live Data & CSV Logging)
1. Điều hướng đến màn hình **Dashboard** (Đồng hồ) hoặc **Table** (Bảng thông số).
2. Xác nhận app chỉ hiển thị các PID mà xe thực sự hỗ trợ (dựa trên bitmask `0100`, `0120`, `0140`).
3. Kiểm tra các giá trị đo được khi máy tắt (KOEO):
   - Vòng tua máy (Engine RPM): $0\text{ RPM}$.
   - Tốc độ xe (Vehicle Speed): $0\text{ km/h}$.
   - Nhiệt độ nước làm mát (ECT): nhiệt độ môi trường hiện tại (ví dụ $30^\circ\text{C} - 45^\circ\text{C}$).
4. **Kiểm tra ghi log CSV**:
   - Bấm nút **Start Log** (Bắt đầu ghi CSV).
   - Để app poll dữ liệu trong vòng 60 giây.
   - Bấm nút **Stop Log** (Dừng ghi).
5. **Kiểm tra file CSV trong màn hình Log**:
   - Mở màn hình **Log**: file CSV mới tạo phải có tên chứa timestamp (ví dụ `pid_log_20261026_143000.csv`).
   - Mở xem nội dung: kiểm tra định dạng dòng tiêu đề `timestamp,pid,name,value,unit` và dữ liệu các dòng tương ứng.
   - Kiểm tra dung lượng file (giới hạn xoay vòng 5MB).
6. **Hành động**: Xuất file trace: `trace_step3_live_data.txt`.

---

### Bước 4: Quét Mã Lỗi Đa Module (Multi-Module DTC Scan - UDS 0x19)
1. Điều hướng đến màn hình **Diagnostic Codes (Errors)**.
2. Bấm nút **Scan All Modules**.
3. **Giai đoạn 1: Quét các module trên HS-CAN (High-Speed CAN 500k)**:
   - App gửi lệnh UDS `19 02 AF` đến từng ECU (PCM `7E0`, ABS `760`, BCM `726`, IPC `720`, v.v.).
   - Quan sát xử lý phản hồi:
     - Nếu ECU trả về `7F 19 78` (NRC 0x78 ResponsePending): Driver tiếp tục chờ phản hồi mà không báo lỗi.
     - Nếu gói tin dài (nhiều DTC): Driver tự động gửi Flow Control `30 00 00` và ghép Consecutive Frames.
4. **Giai đoạn 2: Quét các module trên MS-CAN (Medium-Speed CAN 125k)**:
   - **Với OBDLink EX / MX+**: App gửi lệnh `STP 53` (theo OBDLink FRPM rev F mục 8.6 trang 34). Đèn chỉ báo trên adapter chuyển sang MS-CAN, app quét tiếp các module HVAC `733`, RCM `737`, PAM `736`, v.v.
   - **Với vLinker FS**: App gửi `ATMSCAN` + `ATSPB`. Nếu adapter trả về `?`, app dừng quét bus đó ngay và hiển thị cảnh báo `"Bus switch failed"`.
   - **Với OBDLink LX / CX**: App phát hiện adapter không có phần cứng MS-CAN, tự động hiển thị thông báo bỏ qua MS-CAN, không gửi bất kỳ lệnh chuyển bus nào đến adapter, đánh dấu các module MS-CAN là không hỗ trợ.
   - **Với ELM327 công tắc cơ**: App hiện hộp thoại popup yêu cầu người dùng gạt công tắc phần cứng sang MS-CAN (chân 3/11) rồi mới tiếp tục.
5. **Kiểm tra phân loại kết quả trên màn hình**:
   - Module có mã lỗi: Hiển thị badge màu đỏ và danh sách mã DTC (ví dụ `P0171`, `B11D9:09`).
   - Module không có lỗi: Hiển thị badge màu xanh lá cây ("Successful DTC reading, no error codes").
   - Module không trang bị hoặc không phản hồi (`NO DATA`): Hiển thị badge màu xám ("No response / Offline"). **Tuyệt đối không hiển thị 0 lỗi cho module offline**.
6. **Hành động**: Xuất file trace: `trace_step4_dtc_scan.txt`.

---

### Bước 5: Kết Thúc Kiểm Thử & Thu Thập Dữ Liệu
1. Vào màn hình **Log**:
   - Bấm nút **Export / Share** để lưu tất cả file log CSV và 4 file Raw Command Trace ra ngoài (Google Drive, Email, hoặc bộ nhớ máy).
2. Bấm **Disconnect** để đóng kết nối với adapter.
3. Rút adapter khỏi giắc OBD-II của xe.
4. Tắt khóa điện xe về vị trí **OFF**.

---

## 3. Danh Mục Kiểm Tra Kết Quả (Pass / Fail Criteria)

| Hạng mục kiểm tra | Tiêu chuẩn ĐẠT (PASS) | Kết quả thực tế | Ghi chú |
|---|---|---|---|
| **1. Nhận diện Adapter** | Nhận diện đúng họ chip và model (EX/MX+ có MS-CAN, LX/CX không có MS-CAN). | [ ] PASS  [ ] FAIL | |
| **2. Đọc số VIN** | Đọc chính xác 17 ký tự VIN từ mode 09 PID 02 qua ISO-TP multi-frame. | [ ] PASS  [ ] FAIL | |
| **3. Đo điện áp** | Điện áp ATRV khớp với điện áp thực của ắc quy ($\pm 0.2\text{V}$). | [ ] PASS  [ ] FAIL | |
| **4. Live Data** | Chỉ hiển thị các PID xe hỗ trợ; dữ liệu cập nhật mượt mà; tốc độ poll ổn định. | [ ] PASS  [ ] FAIL | |
| **5. File Log CSV** | File ghi đúng header `timestamp,pid,name,value,unit`; xoay vòng khi đạt 5MB. | [ ] PASS  [ ] FAIL | |
| **6. Chuyển bus HS/MS-CAN** | Gửi đúng `STP 33` (HS) và `STP 53` (MS); nếu `?` thì dừng và báo "Bus switch failed". | [ ] PASS  [ ] FAIL | |
| **7. Phân biệt Offline** | Module không trả lời được đánh dấu "No response / Offline", không bị nhầm thành "0 lỗi". | [ ] PASS  [ ] FAIL | |
| **8. Raw Command Trace** | Ghi đầy đủ timestamp, chiều TX/RX của mọi lệnh gửi đi và phản hồi thô. | [ ] PASS  [ ] FAIL | |
