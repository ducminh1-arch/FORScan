#!/usr/bin/env python3
"""
CarScan Lite - Phase 0 Comprehensive Test & Validation Suite
Validates:
1. JSON Spec integrity (modules.json, pids.json, dtc.json, tests.json, services.json)
2. ELM327 TCP Emulator startup and AT command handling
3. Full Phase 0 Spike execution (ATZ -> ATE0 -> ATL0 -> ATS0 -> ATH1 -> ATSP6 -> ATRV -> 0902 VIN decoding)
4. ISO-TP Multi-frame reassembly and PID decoding
"""

import os
import sys
import json
import time
import socket
import threading
import unittest

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC_DIR = os.path.join(ROOT_DIR, "spec")
TOOLS_DIR = os.path.join(ROOT_DIR, "tools")
sys.path.insert(0, ROOT_DIR)

from tools.elm327_emulator import ELM327Server
import tools.spike_test as spike_test


class TestSpecIntegrity(unittest.TestCase):
    def test_modules_spec(self):
        path = os.path.join(SPEC_DIR, "modules.json")
        self.assertTrue(os.path.exists(path), "modules.json must exist")
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertIn("modules", data)
        self.assertGreaterEqual(len(data["modules"]), 5)
        # Check Ford PCM
        ford_pcm = next((m for m in data["modules"] if m["id"] == "FORD_PCM"), None)
        self.assertIsNotNone(ford_pcm)
        self.assertEqual(ford_pcm["txId"], "7E0")
        self.assertEqual(ford_pcm["rxId"], "7E8")

    def test_pids_spec(self):
        path = os.path.join(SPEC_DIR, "pids.json")
        self.assertTrue(os.path.exists(path))
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertIn("pids", data)
        rpm_pid = next((p for p in data["pids"] if p["id"] == "PID_010C"), None)
        self.assertIsNotNone(rpm_pid)
        self.assertEqual(rpm_pid["unit"], "RPM")

    def test_dtc_spec(self):
        path = os.path.join(SPEC_DIR, "dtc.json")
        self.assertTrue(os.path.exists(path))
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertIn("dtcs", data)
        b11d9 = next((d for d in data["dtcs"] if d["code"] == "B11D9"), None)
        self.assertIsNotNone(b11d9, "B11D9 Vehicle Battery DTC from screenshot must exist")

    def test_tests_spec(self):
        path = os.path.join(SPEC_DIR, "tests.json")
        self.assertTrue(os.path.exists(path))
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertIn("tests", data)
        koeo = next((t for t in data["tests"] if t["id"] == "TEST_FORD_PCM_KOEO"), None)
        self.assertIsNotNone(koeo)

    def test_services_spec(self):
        path = os.path.join(SPEC_DIR, "services.json")
        self.assertTrue(os.path.exists(path))
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertIn("services", data)
        reboot = next((s for s in data["services"] if s["id"] == "SERV_PCM_REBOOT"), None)
        self.assertIsNotNone(reboot)


class TestEmulatorAndSpike(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.port = 35002
        cls.server = ELM327Server(host="127.0.0.1", port=cls.port, vin="1FA6P8CF5H5123456")
        cls.thread = threading.Thread(target=cls.server.start, daemon=True)
        cls.thread.start()
        time.sleep(0.4)

    @classmethod
    def tearDownClass(cls):
        cls.server.stop()

    def test_full_spike_flow(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(("127.0.0.1", self.port))

        # 1. ATZ
        r = spike_test.send_command(sock, "ATZ")
        self.assertIn("ELM327", r)

        # 2. ATE0, ATL0, ATS0, ATH1, ATSP6
        self.assertIn("OK", spike_test.send_command(sock, "ATE0"))
        self.assertIn("OK", spike_test.send_command(sock, "ATL0"))
        self.assertIn("OK", spike_test.send_command(sock, "ATS0"))
        self.assertIn("OK", spike_test.send_command(sock, "ATH1"))
        self.assertIn("OK", spike_test.send_command(sock, "ATSP6"))

        # 3. ATRV
        v_resp = spike_test.send_command(sock, "ATRV")
        self.assertIn("12.6V", v_resp)

        # 4. Mode 09 PID 02 VIN query
        vin_resp = spike_test.send_command(sock, "0902")
        vin = spike_test.parse_vin(vin_resp)
        self.assertEqual(vin, "1FA6P8CF5H5123456")

        # 5. Live RPM query (010C)
        rpm_resp = spike_test.send_command(sock, "010C")
        self.assertTrue("410C" in rpm_resp or "41 0C" in rpm_resp)

        # 6. DTC query (03)
        dtc_resp = spike_test.send_command(sock, "03")
        self.assertTrue("43" in dtc_resp)

        sock.close()


if __name__ == "__main__":
    loader = unittest.defaultTestLoader
    suite = unittest.TestSuite()
    suite.addTest(loader.loadTestsFromTestCase(TestSpecIntegrity))
    suite.addTest(loader.loadTestsFromTestCase(TestEmulatorAndSpike))
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
