#!/usr/bin/env python3
"""
CarScan Lite - Phase 1 Live Wi-Fi TCP Emulator Test
Connects over real TCP socket (simulating Wi-Fi ELM327 adapter) to verify:
1. AT Init sequence and ATRV voltage reading
2. Broadcast ECU discovery (7DF -> 7E8 & 7E9)
3. Bitmask Supported PIDs discovery (0100, 0120, 0140)
4. Multi-PID batch query (01 0C 0D 05)
5. Mode 03 (Stored), Mode 07 (Pending), Mode 0A (Permanent) DTCs
6. Mode 04 (Clear DTCs) and verification of reset state
"""

import socket
import time
import threading
import unittest
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from tools.elm327_emulator import ELM327Server
import tools.spike_test as spike_test


class TestPhase1WiFiEmulator(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.port = 35003
        cls.server = ELM327Server(host="127.0.0.1", port=cls.port)
        cls.thread = threading.Thread(target=cls.server.start, daemon=True)
        cls.thread.start()
        time.sleep(0.4)

    @classmethod
    def tearDownClass(cls):
        cls.server.stop()

    def setUp(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect(("127.0.0.1", self.port))
        # Run init
        spike_test.send_command(self.sock, "ATZ")
        spike_test.send_command(self.sock, "ATE0")
        spike_test.send_command(self.sock, "ATL0")
        spike_test.send_command(self.sock, "ATS0")
        spike_test.send_command(self.sock, "ATH1")
        spike_test.send_command(self.sock, "ATSP6")

    def tearDown(self):
        self.sock.close()

    def test_01_atrv_battery_voltage(self):
        resp = spike_test.send_command(self.sock, "ATRV")
        self.assertIn("12.6V", resp, "ATRV must return direct port voltage")

    def test_02_broadcast_ecu_discovery(self):
        # Set broadcast header 7DF
        spike_test.send_command(self.sock, "ATSH7DF")
        resp = spike_test.send_command(self.sock, "0100")
        # Restore header to 7E0
        spike_test.send_command(self.sock, "ATSH7E0")

        self.assertIn("7E8", resp, "Broadcast must detect PCM (7E8)")
        self.assertIn("7E9", resp, "Broadcast must detect TCM (7E9)")

    def test_03_bitmask_supported_pids(self):
        r00 = spike_test.send_command(self.sock, "0100")
        self.assertIn("4100", r00)
        self.assertIn("BE3FB813", r00)

        r20 = spike_test.send_command(self.sock, "0120")
        self.assertIn("4120", r20)
        self.assertIn("80072001", r20)

        r40 = spike_test.send_command(self.sock, "0140")
        self.assertIn("4140", r40)

    def test_04_multi_pid_batch_query(self):
        # Request RPM (0C), Speed (0D), Coolant (05) in single command
        batch_resp = spike_test.send_command(self.sock, "010C0D05")
        self.assertTrue("410C" in batch_resp or "41 0C" in batch_resp)
        self.assertTrue("0D" in batch_resp)
        self.assertTrue("05" in batch_resp)

    def test_05_dtc_modes_and_clear(self):
        # Mode 03: Stored
        m03 = spike_test.send_command(self.sock, "03")
        self.assertIn("4302", m03, "Should have 2 stored DTCs (P0171, P0300)")

        # Mode 07: Pending
        m07 = spike_test.send_command(self.sock, "07")
        self.assertIn("4700", m07, "Mode 07 should have 0 pending DTCs")

        # Mode 0A: Permanent
        m0a = spike_test.send_command(self.sock, "0A")
        self.assertIn("4A00", m0a, "Mode 0A should have 0 permanent DTCs")

        # Mode 04: Clear DTCs
        m04 = spike_test.send_command(self.sock, "04")
        self.assertIn("44", m04, "Mode 04 positive response")

        # Verify Mode 03 is now 0 DTCs
        m03_after = spike_test.send_command(self.sock, "03")
        self.assertIn("4300", m03_after, "Mode 03 should return 0 codes after clear")


if __name__ == "__main__":
    loader = unittest.defaultTestLoader
    suite = loader.loadTestsFromTestCase(TestPhase1WiFiEmulator)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
