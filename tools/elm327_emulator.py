#!/usr/bin/env python3
"""
CarScan Lite - ELM327 / STN OBD2 & UDS TCP Emulator
Simulates an ELM327 Wi-Fi adapter (127.0.0.1:35000 or 192.168.0.10:35000) connected to a Ford / Mazda vehicle.

Supports:
- Standard AT commands (ATZ, ATE, ATL, ATS, ATH, ATSP, ATRV, ATSH, ATCRA, etc.)
- SAE J1979 OBD2 Modes (01, 03, 04, 07, 09)
- Multi-frame ISO-TP reassembly and formatted CAN responses (Mode 09 PID 02 VIN)
- Multi-module addressing (PCM 7E0/7E8, TCM 7E1/7E9, BCM 726/72E, ABS 760/768, APIM 7D0/7D8)
- UDS ISO 14229 services (0x10 DiagnosticSessionControl, 0x19 ReadDTC, 0x11 ECUReset, 0x31 RoutineControl)
"""

import sys
import socket
import threading
import time
import argparse
import logging
import re

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s'
)
logger = logging.getLogger("ELM327Emulator")

DEFAULT_VIN = "1FA6P8CF5H5123456" # Ford Mustang EcoBoost VIN
DEFAULT_BATTERY_VOLTAGE = 12.6

class VehicleState:
    def __init__(self, vin=DEFAULT_VIN):
        self.vin = vin
        self.battery_voltage = DEFAULT_BATTERY_VOLTAGE
        self.rpm = 850
        self.speed = 0
        self.ect = 90  # 90°C
        self.iat = 28  # 28°C
        self.map = 35  # 35 kPa
        self.throttle = 14.5  # %
        self.fuel_level = 72.0  # %
        self.dtcs = {
            "7E0": ["P0171", "P0300"],  # PCM (2 DTCs)
            "7E1": ["P0700"],           # TCM (1 DTC)
            "726": ["B11D909"],         # BCM (B11D9:09)
            "760": ["C003101"],         # ABS (C0031:01 Left Wheel Speed)
            "720": [],                  # IPC (Clean - 0 DTCs)
            "7D0": [],                  # APIM (Clean - 0 DTCs)
        }
        self.unresponsive_modules = {"736", "754"} # PAM, TCU simulate offline/no response
        self.simulate_nrc78 = True
        self.engine_running = False


    def get_dynamic_rpm(self):
        # Slightly fluctuate RPM like an idling engine
        import random
        jitter = random.randint(-20, 20)
        return max(750, min(950, self.rpm + jitter))


class ELM327Session:
    def __init__(self, client_socket, client_address, vehicle_state):
        self.socket = client_socket
        self.address = client_address
        self.vehicle = vehicle_state
        self.echo = True
        self.linefeeds = True
        self.spaces = True
        self.headers = False
        self.protocol = "6"  # ISO 15765-4 (CAN 11/500)
        self.current_header = "7E0"  # Default PCM request header
        self.buffer = ""
        self.forced_error = None


    def run(self):
        logger.info(f"Client connected from {self.address}")
        # Send initial ELM327 prompt if needed or wait for ATZ
        try:
            while True:
                data = self.socket.recv(1024)
                if not data:
                    break
                self.buffer += data.decode('utf-8', errors='ignore')
                while '\r' in self.buffer or '\n' in self.buffer:
                    match = re.search(r'[\r\n]', self.buffer)
                    if not match:
                        break
                    idx = match.start()
                    line = self.buffer[:idx].strip()
                    self.buffer = self.buffer[idx+1:]
                    if line:
                        self.process_command(line)
        except ConnectionResetError:
            pass
        except Exception as e:
            logger.error(f"Error handling client {self.address}: {e}")
        finally:
            self.socket.close()
            logger.info(f"Client disconnected: {self.address}")

    def send_response(self, text):
        eol = "\r\n" if self.linefeeds else "\r"
        prompt = ">"
        response = f"{text}{eol}{prompt}"
        self.socket.sendall(response.encode('utf-8'))
        logger.debug(f"TX -> {repr(response)}")

    def process_command(self, cmd):
        clean_cmd = cmd.upper().replace(" ", "")
        logger.info(f"RX <- '{cmd}' (normalized: '{clean_cmd}')")

        # Echo if enabled
        # Note: in real ELM327, echo happens character by character or line by line.

        # --- AT & ST Adapter Commands ---
        if clean_cmd.startswith("AT") or clean_cmd.startswith("ST"):
            self.handle_at_command(clean_cmd)
            return

        # --- OBD2 / UDS Commands ---
        self.handle_obd_command(clean_cmd)

    def handle_at_command(self, cmd):
        if cmd == "ATZ" or cmd == "ATWS":
            # Reset
            self.echo = True
            self.linefeeds = True
            self.spaces = True
            self.headers = False
            self.current_header = "7E0"
            self.send_response("\r\rELM327 v1.5")
        elif cmd == "ATE0":
            self.echo = False
            self.send_response("OK")
        elif cmd == "ATE1":
            self.echo = True
            self.send_response("OK")
        elif cmd == "ATL0":
            self.linefeeds = False
            self.send_response("OK")
        elif cmd == "ATL1":
            self.linefeeds = True
            self.send_response("OK")
        elif cmd == "ATS0":
            self.spaces = False
            self.send_response("OK")
        elif cmd == "ATS1":
            self.spaces = True
            self.send_response("OK")
        elif cmd == "ATH0":
            self.headers = False
            self.send_response("OK")
        elif cmd == "ATH1":
            self.headers = True
            self.send_response("OK")
        elif cmd.startswith("ATSP"):
            proto = cmd[4:]
            self.protocol = proto if proto else "6"
            self.send_response("OK")
        elif cmd == "ATDP":
            self.send_response("ISO 15765-4 (CAN 11/500)")
        elif cmd == "ATDPN":
            self.send_response("6")
        elif cmd == "ATRV":
            self.send_response(f"{self.vehicle.battery_voltage:.1f}V")
        elif cmd.startswith("ATSH"):
            hdr = cmd[4:]
            self.current_header = hdr
            logger.info(f"Header set to: {self.current_header}")
            self.send_response("OK")
        elif cmd.startswith("ATCRA"):
            self.send_response("OK")
        elif cmd.startswith("ATPB"):
            self.send_response("OK")
        elif cmd.startswith("ATST"):
            self.send_response("OK")
        elif cmd == "ATD":
            # Restore defaults
            self.send_response("OK")
        elif cmd == "ATI":
            self.send_response("ELM327 v1.5")
        elif cmd == "STI":
            self.send_response("STN2120 v5.1.0 (OBDLink EX)")
        elif cmd.startswith("STP"):
            self.send_response("OK")
        elif cmd == "ATHSCAN" or cmd == "ATMSCAN":
            self.send_response("OK")
        elif cmd.startswith("ATERR"):
            err_type = cmd[5:]
            if "STOPPED" in err_type: self.forced_error = "STOPPED"
            elif "SEARCHING" in err_type: self.forced_error = "SEARCHING..."
            elif "UNABLE" in err_type: self.forced_error = "UNABLE TO CONNECT"
            elif "CANERROR" in err_type or "CAN ERROR" in err_type: self.forced_error = "CAN ERROR"
            elif "BUSBUSY" in err_type or "BUS BUSY" in err_type: self.forced_error = "BUS BUSY"
            elif "NODATA" in err_type or "NO DATA" in err_type: self.forced_error = "NO DATA"
            else: self.forced_error = None
            self.send_response("OK")
        elif cmd == "AT@1":
            self.send_response("OBDLink MX+")
        else:
            # Default OK for unsupported AT harmless query
            self.send_response("OK")

    def handle_obd_command(self, cmd):
        if self.forced_error:
            self.send_response(self.forced_error)
            return

        # Resolve response CAN ID based on current header
        # Standard: 7E0 -> 7E8, 7E1 -> 7E9, 726 -> 72E, 760 -> 768, 7D0 -> 7D8, 720 -> 728
        tx_id = self.current_header.upper()
        if tx_id in self.vehicle.unresponsive_modules:
            self.send_response("NO DATA")
            return

        rx_map = {
            "7E0": "7E8",
            "7E1": "7E9",
            "726": "72E",
            "760": "768",
            "7D0": "7D8",
            "720": "728",
            "736": "73E",
            "737": "73F",
            "754": "75C"
        }
        rx_id = rx_map.get(tx_id, "7E8")

        # Mode 01: Live Data (Single or Multi-PID)
        if cmd.startswith("01"):
            raw_pids = cmd[2:]
            if len(raw_pids) == 2:
                self.handle_mode_01(rx_id, raw_pids, tx_id)
            elif len(raw_pids) > 2 and len(raw_pids) % 2 == 0:
                pids = [raw_pids[i:i+2] for i in range(0, len(raw_pids), 2)]
                self.handle_mode_01_multi(rx_id, pids, tx_id)
            else:
                self.send_response("NO DATA")
            return

        # Mode 03: Request Stored DTCs
        if cmd == "03":
            self.handle_mode_03(rx_id, tx_id)
            return

        # Mode 04: Clear DTCs (OBD2 standard)
        if cmd == "04":
            self.handle_mode_04(rx_id, tx_id)
            return

        # Mode 14: Clear Diagnostic Information (UDS individual module)
        if cmd.startswith("14"):
            self.handle_mode_14(rx_id, tx_id, cmd)
            return


        # Mode 07: Request Pending DTCs
        if cmd == "07":
            self.handle_mode_07(rx_id)
            return

        # Mode 0A: Request Permanent DTCs
        if cmd == "0A":
            self.format_and_send(rx_id, ["4A", "00"])
            return

        # Mode 09: Vehicle Information (VIN, etc.)
        if cmd.startswith("09"):
            sub_pid = cmd[2:4]
            self.handle_mode_09(rx_id, sub_pid)
            return

        # Mode 22: UDS ReadDataByIdentifier
        if cmd.startswith("22"):
            did = cmd[2:]
            self.handle_mode_22(rx_id, did)
            return

        # Mode 19: UDS ReadDTCInformation
        if cmd.startswith("19"):
            self.handle_mode_19(rx_id, tx_id, cmd)
            return

        # Mode 31: UDS RoutineControl
        if cmd.startswith("31"):
            self.handle_mode_31(rx_id, cmd)
            return

        # Mode 11: UDS ECUReset
        if cmd.startswith("11"):
            self.handle_mode_11(rx_id, cmd)
            return

        # Mode 3E: UDS TesterPresent
        if cmd.startswith("3E"):
            self.format_and_send(rx_id, ["5E", "00"])
            return

        # Mode 10: UDS DiagnosticSessionControl
        if cmd.startswith("10"):
            sub = cmd[2:4] if len(cmd) >= 4 else "01"
            self.format_and_send(rx_id, ["50", sub, "00", "32", "01", "F4"])
            return

        # Default
        self.send_response("NO DATA")

    def get_pid_data_tokens(self, pid):
        if pid == "00":
            return ["BE", "3F", "B8", "13"]
        elif pid == "20":
            return ["80", "07", "20", "01"]
        elif pid == "40":
            return ["7E", "D0", "00", "00"]
        elif pid == "60":
            return ["00", "00", "00", "00"]
        elif pid == "0C":
            rpm = self.vehicle.get_dynamic_rpm()
            val = int(rpm * 4)
            return [f"{(val >> 8) & 0xFF:02X}", f"{val & 0xFF:02X}"]
        elif pid == "0D":
            return [f"{int(self.vehicle.speed):02X}"]
        elif pid == "05":
            return [f"{int(self.vehicle.ect + 40):02X}"]
        elif pid == "0F":
            return [f"{int(self.vehicle.iat + 40):02X}"]
        elif pid == "0B":
            return [f"{int(self.vehicle.map):02X}"]
        elif pid == "10":
            val = int(4.5 * 100)
            return [f"{(val >> 8) & 0xFF:02X}", f"{val & 0xFF:02X}"]
        elif pid == "11":
            return [f"{int(self.vehicle.throttle * 255.0 / 100.0):02X}"]
        elif pid == "04":
            return [f"{int(22.0 * 255.0 / 100.0):02X}"]
        elif pid == "2F":
            return [f"{int(self.vehicle.fuel_level * 255.0 / 100.0):02X}"]
        elif pid == "42":
            val = int(self.vehicle.battery_voltage * 1000)
            return [f"{(val >> 8) & 0xFF:02X}", f"{val & 0xFF:02X}"]
        elif pid == "46":
            return [f"{int(25 + 40):02X}"]
        return None

    def handle_mode_01(self, rx_id, pid, tx_id="7E0"):
        if pid == "00" and tx_id == "7DF":
            # Broadcast response from both PCM (7E8) and TCM (7E9)
            eol = "\r\n" if self.linefeeds else "\r"
            l1 = self._format_line("7E8", ["06", "41", "00", "BE", "3F", "B8", "13"]) if self.headers else "41 00 BE 3F B8 13"
            l2 = self._format_line("7E9", ["06", "41", "00", "80", "00", "00", "01"]) if self.headers else "41 00 80 00 00 01"
            self.send_response(f"{l1}{eol}{l2}")
            return

        tokens = self.get_pid_data_tokens(pid)
        if tokens is not None:
            self.format_and_send(rx_id, ["41", pid] + tokens)
        else:
            self.send_response("NO DATA")

    def handle_mode_01_multi(self, rx_id, pids, tx_id="7E0"):
        combined_payload = ["41"]
        for p in pids:
            data = self.get_pid_data_tokens(p)
            if data is not None:
                combined_payload.append(p)
                combined_payload.extend(data)
        if len(combined_payload) > 1:
            self.format_and_send(rx_id, combined_payload)
        else:
            self.send_response("NO DATA")

    def handle_mode_03(self, rx_id, tx_id):
        dtc_list = self.vehicle.dtcs.get(tx_id, [])
        if not dtc_list:
            # 43 00 (0 codes)
            self.format_and_send(rx_id, ["43", "00"])
            return

        # Encode 2 DTCs per standard format:
        # P0171 -> 01 71, P0300 -> 03 00
        encoded = ["43", f"{len(dtc_list):02X}"]
        for code in dtc_list[:3]:
            # Convert standard alphanumeric DTC to 2 bytes
            # P0171: P=00 (0000 0001 0111 0001)
            first_char = code[0].upper()
            digit1 = int(code[1])
            type_bits = {"P": 0, "C": 1, "B": 2, "U": 3}.get(first_char, 0)
            high_nibble = (type_bits << 2) | ((digit1 >> 2) & 0x3) # or standard (type << 6) | (digit1 << 4)
            # Standard SAE J2012 encoding:
            # Byte 0: [Type 2 bits: 00=P, 01=C, 10=B, 11=U][Digit1 2 bits: 00=0, 01=1, 10=2, 11=3] [Digit2 4 bits]
            b0 = (type_bits << 6) | (digit1 << 4) | int(code[2], 16)
            b1 = int(code[3:5], 16)
            encoded.extend([f"{b0:02X}", f"{b1:02X}"])

        self.format_and_send(rx_id, encoded)

    def handle_mode_04(self, rx_id, tx_id):
        # Clear DTCs
        self.vehicle.dtcs[tx_id] = []
        self.format_and_send(rx_id, ["44"])

    def handle_mode_07(self, rx_id):
        # Pending DTCs
        self.format_and_send(rx_id, ["47", "00"])

    def handle_mode_09(self, rx_id, pid):
        if pid == "02":
            # VIN Request (17 chars: e.g. 1FA6P8CF5H5123456)
            # Response in ISO-TP format
            vin_bytes = [f"{ord(c):02X}" for c in self.vehicle.vin]
            # Payload = 49 02 01 (VIN count 1) + 17 ASCII bytes = 20 bytes total
            payload = ["49", "02", "01"] + vin_bytes
            total_len = len(payload) # 20 bytes (0x14)

            # In ISO-TP multi-frame:
            # First Frame: 10 14 [first 6 bytes of payload]
            # Consecutive Frame 1: 21 [next 7 bytes]
            # Consecutive Frame 2: 22 [next 7 bytes]
            ff_data = ["10", f"{total_len:02X}"] + payload[0:6]
            cf1_data = ["21"] + payload[6:13]
            cf2_data = ["22"] + payload[13:20]

            if self.headers:
                lines = [
                    self._format_line(rx_id, ff_data),
                    self._format_line(rx_id, cf1_data),
                    self._format_line(rx_id, cf2_data)
                ]
            else:
                # ATH0 mode
                lines = [
                    f"0{total_len:02X}",
                    "0: " + " ".join(payload[0:6]),
                    "1: " + " ".join(payload[6:13]),
                    "2: " + " ".join(payload[13:20])
                ]

            eol = "\r\n" if self.linefeeds else "\r"
            res = eol.join(lines)
            self.send_response(res)
        elif pid == "00":
            # Supported PIDs in Mode 09
            self.format_and_send(rx_id, ["49", "00", "00", "00", "00", "04"]) # PID 02 supported
        else:
            self.send_response("NO DATA")

    def handle_mode_22(self, rx_id, did):
        # Mode 22: ReadDataByIdentifier
        if did == "028C":
            # Cylinder Head Temp (CHT: 95°C -> ((95+40)*4)=540 -> 0x021C)
            self.format_and_send(rx_id, ["62", "02", "8C", "02", "1C"])
        elif did == "1E1C":
            # Transmission Fluid Temp (TFT: 82°C -> ((82+40)*16)=1952 -> 0x07A0)
            self.format_and_send(rx_id, ["62", "1E", "1C", "07", "A0"])
        elif did == "0367":
            # Battery SOC (85%)
            self.format_and_send(rx_id, ["62", "03", "67", "55"])
        else:
            self.send_response("NO DATA")

    def handle_mode_14(self, rx_id, tx_id, cmd):
        # UDS Mode 14: ClearDiagnosticInformation (e.g. 14 FF FF FF)
        if self.vehicle.simulate_nrc78:
            nrc78_line = self._format_line(rx_id, ["03", "7F", "14", "78"]) if self.headers else self._format_data_only(["7F", "14", "78"])
            eol = "\r\n" if self.linefeeds else "\r"
            self.socket.sendall(f"{nrc78_line}{eol}".encode('utf-8'))
            time.sleep(0.3)

        if tx_id in self.vehicle.dtcs:
            self.vehicle.dtcs[tx_id] = []
        self.format_and_send(rx_id, ["54"])

    def handle_mode_19(self, rx_id, tx_id, cmd):
        # UDS Mode 19: ReadDTCInformation
        if tx_id == "760" and self.vehicle.simulate_nrc78:
            nrc78_line = self._format_line(rx_id, ["03", "7F", "19", "78"]) if self.headers else self._format_data_only(["7F", "19", "78"])
            eol = "\r\n" if self.linefeeds else "\r"
            self.socket.sendall(f"{nrc78_line}{eol}".encode('utf-8'))
            time.sleep(0.3)

        dtc_list = self.vehicle.dtcs.get(tx_id, [])
        if not dtc_list:
            # Positive response 59 02 FF (0 DTCs returned)
            self.format_and_send(rx_id, ["59", "02", "FF"])
            return

        res = ["59", "02", "FF"]
        for dtc in dtc_list:
            if dtc.startswith("B11D9"):
                res.extend(["91", "D9", "09", "2F"])
            elif dtc.startswith("B311D"):
                res.extend(["B1", "1D", "09", "2F"])
            elif dtc.startswith("C0031"):
                res.extend(["40", "31", "01", "2F"])
            elif dtc == "P0171":
                res.extend(["01", "71", "00", "2F"])
            elif dtc == "P0300":
                res.extend(["03", "00", "00", "2F"])
            elif dtc == "P0700":
                res.extend(["07", "00", "00", "2F"])
            else:
                res.extend(["01", "00", "00", "28"])
        self.format_and_send(rx_id, res)

    def handle_mode_31(self, rx_id, cmd):
        # Mode 31 RoutineControl: 31 01 [routine_id] -> returns 71 01 [routine_id] 00 (Success)
        sub = cmd[2:4] if len(cmd) >= 4 else "01"
        routine = cmd[4:8] if len(cmd) >= 8 else "0000"
        self.format_and_send(rx_id, ["71", sub, routine[:2], routine[2:], "00"])

    def handle_mode_11(self, rx_id, cmd):
        # Mode 11 ECUReset: 11 01 -> returns 51 01
        sub = cmd[2:4] if len(cmd) >= 4 else "01"
        self.format_and_send(rx_id, ["51", sub])

    def format_and_send(self, rx_id, byte_tokens):
        length = len(byte_tokens)
        if length <= 7:
            # Single Frame (SF)
            sf_data = [f"{length:02X}"] + byte_tokens
            line = self._format_line(rx_id, sf_data) if self.headers else self._format_data_only(byte_tokens)
            self.send_response(line)
        else:
            # Multi-Frame ISO-TP
            total_len = length
            ff_data = ["10", f"{total_len:02X}"] + byte_tokens[0:6]
            cf_list = []
            seq = 1
            idx = 6
            while idx < total_len:
                chunk = byte_tokens[idx:idx+7]
                cf_code = f"2{seq:1X}"
                cf_list.append([cf_code] + chunk)
                seq = (seq + 1) & 0x0F
                idx += len(chunk)

            eol = "\r\n" if self.linefeeds else "\r"
            if self.headers:
                lines = [self._format_line(rx_id, ff_data)]
                for cf in cf_list:
                    lines.append(self._format_line(rx_id, cf))
                self.send_response(eol.join(lines))
            else:
                lines = [f"0{total_len:02X}"]
                lines.append("0: " + " ".join(byte_tokens[0:6]))
                for i, cf in enumerate(cf_list):
                    lines.append(f"{i+1}: " + " ".join(cf[1:]))
                self.send_response(eol.join(lines))


    def _format_line(self, header, tokens):
        if self.spaces:
            return f"{header} " + " ".join(tokens)
        else:
            return f"{header}" + "".join(tokens)

    def _format_data_only(self, tokens):
        if self.spaces:
            return " ".join(tokens)
        else:
            return "".join(tokens)


class ELM327Server:
    def __init__(self, host="0.0.0.0", port=35000, vin=DEFAULT_VIN):
        self.host = host
        self.port = port
        self.vehicle_state = VehicleState(vin=vin)
        self.server_socket = None
        self.is_running = False

    def start(self):
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind((self.host, self.port))
        self.server_socket.listen(5)
        self.is_running = True
        logger.info(f"ELM327 Emulator listening on {self.host}:{self.port} (Simulated VIN: {self.vehicle_state.vin})")

        try:
            while self.is_running:
                client_sock, client_addr = self.server_socket.accept()
                session = ELM327Session(client_sock, client_addr, self.vehicle_state)
                t = threading.Thread(target=session.run, daemon=True)
                t.start()
        except KeyboardInterrupt:
            logger.info("Stopping ELM327 Emulator...")
        finally:
            self.stop()

    def stop(self):
        self.is_running = False
        if self.server_socket:
            try:
                self.server_socket.close()
            except Exception:
                pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="ELM327 TCP Emulator for CarScan Lite")
    parser.add_argument("--host", default="0.0.0.0", help="Host address to bind (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=35000, help="Port to bind (default: 35000)")
    parser.add_argument("--vin", default=DEFAULT_VIN, help=f"Simulated vehicle VIN (default: {DEFAULT_VIN})")
    args = parser.parse_args()

    server = ELM327Server(host=args.host, port=args.port, vin=args.vin)
    server.start()
