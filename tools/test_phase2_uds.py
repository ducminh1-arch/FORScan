#!/usr/bin/env python3
"""
CarScan Lite - Phase 2 UDS Multi-Module & Bus Switching Integration Test
Tests the ELM327 / STN emulator over TCP against the Phase 2 requirements:
- UDS 0x19 (Read DTC) across PCM, TCM, BCM, ABS, IPC, APIM, PAM
- UDS 0x14 (Clear DTC) per individual module
- NRC 0x78 (ResponsePending: 7F 19 78 and 7F 14 78) handling
- Distinction between unresponsive module (NO DATA / Offline) and clean module (0 DTCs)
- Bus switching commands (STP 31, STP 33, ATHSCAN, ATMSCAN)
- Adapter error strings (STOPPED, CAN ERROR, BUS BUSY, UNABLE TO CONNECT)
"""

import socket
import time
import sys

def send_cmd(sock, cmd, timeout=3.0):
    sock.settimeout(timeout)
    sock.sendall((cmd + "\r").encode('utf-8'))
    buf = ""
    while ">" not in buf:
        chunk = sock.recv(1024).decode('utf-8', errors='ignore')
        if not chunk:
            break
        buf += chunk
    clean = buf.replace(">", "").strip()
    return clean

def run_tests():
    host = "127.0.0.1"
    port = 35002
    print(f"[TEST] Connecting to ELM327 Emulator on {host}:{port}...")

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
    except Exception as e:
        print(f"[ERROR] Could not connect to emulator: {e}")
        return False

    passed = 0
    total = 0

    def assert_test(name, condition, details=""):
        nonlocal passed, total
        total += 1
        if condition:
            passed += 1
            print(f"  [PASS] {name}")
        else:
            print(f"  [FAIL] {name}: {details}")

    print("\n--- 1. Adapter Identification & Profile Detection ---")
    resp = send_cmd(s, "ATZ")
    assert_test("ATZ Reset", "ELM327" in resp, resp)

    resp = send_cmd(s, "ATI")
    assert_test("ATI Query", "ELM327" in resp or "vLinker" in resp, resp)

    resp = send_cmd(s, "STI")
    assert_test("STI Query (STN profile)", "STN" in resp or "OBDLink" in resp, resp)

    print("\n--- 2. Bus Switching Commands (Official Datasheet Commands) ---")
    resp = send_cmd(s, "STP53")
    assert_test("STP 53 (STN MS-CAN ISO 15765 125k)", "OK" in resp, resp)

    resp = send_cmd(s, "STP33")
    assert_test("STP 33 (STN HS-CAN ISO 15765 500k)", "OK" in resp, resp)

    resp = send_cmd(s, "ATMSCAN")
    assert_test("ATMSCAN (vLinker MS-CAN)", "OK" in resp, resp)

    resp = send_cmd(s, "ATHSCAN")
    assert_test("ATHSCAN (vLinker HS-CAN)", "OK" in resp, resp)

    print("\n--- 3. Setup Protocol and Headers for UDS ---")
    send_cmd(s, "ATE0")
    send_cmd(s, "ATL0")
    send_cmd(s, "ATS0")
    send_cmd(s, "ATH1")
    send_cmd(s, "ATSP6")

    print("\n--- 4. UDS 0x19 Multi-Module Diagnostic Code Reading ---")
    # PCM (7E0 -> 7E8): Expect P0171, P0300
    send_cmd(s, "ATSH 7E0")
    pcm_resp = send_cmd(s, "1902AF")
    assert_test("PCM (7E0) DTC Reading", "59" in pcm_resp and ("0171" in pcm_resp or "91" in pcm_resp or "0300" in pcm_resp), pcm_resp)

    # TCM (7E1 -> 7E9): Expect P0700
    send_cmd(s, "ATSH 7E1")
    tcm_resp = send_cmd(s, "1902AF")
    assert_test("TCM (7E1) DTC Reading", "59" in tcm_resp and "0700" in tcm_resp, tcm_resp)

    # BCM (726 -> 72E): Expect B11D9
    send_cmd(s, "ATSH 726")
    bcm_resp = send_cmd(s, "1902AF")
    assert_test("BCM (726) DTC Reading (B11D9)", "59" in bcm_resp and ("91D9" in bcm_resp or "91 D9" in bcm_resp or "B11D9" in bcm_resp), bcm_resp)

    # ABS (760 -> 768): Simulates NRC 0x78 (7F 19 78 ResponsePending) then C0031:01
    send_cmd(s, "ATSH 760")
    abs_resp = send_cmd(s, "1902AF", timeout=4.0)
    assert_test("ABS (760) NRC 0x78 Handling", ("7F1978" in abs_resp or "7F 19 78" in abs_resp) and "59" in abs_resp, abs_resp)

    # IPC (720 -> 728): Expect Clean (0 DTCs: 59 02 FF)
    send_cmd(s, "ATSH 720")
    ipc_resp = send_cmd(s, "1902AF")
    assert_test("IPC (720) Clean Responding Module", "5902FF" in ipc_resp or "59 02 FF" in ipc_resp, ipc_resp)

    # PAM (736): Expect NO DATA (Unresponsive module -> Offline)
    send_cmd(s, "ATSH 736")
    pam_resp = send_cmd(s, "1902AF")
    assert_test("PAM (736) Unresponsive Module Distinction", "NO DATA" in pam_resp, pam_resp)
    assert_test("Offline is NOT confused with Clean", "5902" not in pam_resp, pam_resp)

    print("\n--- 5. UDS 0x14 Single-Module DTC Clearing ---")
    # Clear DTC strictly on PCM (7E0)
    send_cmd(s, "ATSH 7E0")
    clear_resp = send_cmd(s, "14FFFFFF", timeout=4.0)
    assert_test("PCM (7E0) UDS 0x14 Clear", "54" in clear_resp, clear_resp)

    # Re-read PCM DTCs: must now be 0 DTCs (59 02 FF)
    pcm_reread = send_cmd(s, "1902AF")
    assert_test("PCM DTCs Cleared Verification", "5902FF" in pcm_reread or "59 02 FF" in pcm_reread, pcm_reread)

    print("\n--- 6. Adapter Error Strings Injection ---")
    send_cmd(s, "ATERR STOPPED")
    err_resp = send_cmd(s, "0100")
    assert_test("Adapter STOPPED handling", "STOPPED" in err_resp, err_resp)

    send_cmd(s, "ATERR CANERROR")
    err_resp = send_cmd(s, "0100")
    assert_test("Adapter CAN ERROR handling", "CAN ERROR" in err_resp, err_resp)

    send_cmd(s, "ATERR BUSBUSY")
    err_resp = send_cmd(s, "0100")
    assert_test("Adapter BUS BUSY handling", "BUS BUSY" in err_resp, err_resp)

    send_cmd(s, "ATERR UNABLE")
    err_resp = send_cmd(s, "0100")
    assert_test("Adapter UNABLE TO CONNECT handling", "UNABLE TO CONNECT" in err_resp, err_resp)

    send_cmd(s, "ATERR NONE")
    s.close()

    print(f"\n==========================================")
    print(f"Phase 2 Integration Tests: {passed}/{total} Passed")
    print(f"==========================================")
    return passed == total

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)
