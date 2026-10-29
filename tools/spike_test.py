#!/usr/bin/env python3
"""
CarScan Lite - Protocol Spike Test
Demonstrates the initialization sequence and Mode 09 PID 02 VIN extraction against the ELM327 emulator over TCP.
"""

import socket
import time
import re

def send_command(sock, cmd, timeout=3.0):
    sock.settimeout(timeout)
    print(f"\n[TX] -> '{cmd}'")
    sock.sendall((cmd + "\r").encode('utf-8'))
    
    buffer = ""
    start = time.time()
    while True:
        try:
            chunk = sock.recv(1024).decode('utf-8', errors='ignore')
            buffer += chunk
            if ">" in buffer:
                break
        except socket.timeout:
            print("[WARN] Socket timeout waiting for prompt '>'")
            break
        if time.time() - start > timeout:
            break
            
    print(f"[RX] <-\n{buffer.strip()}")
    # Strip prompt '>' and return lines
    cleaned = buffer.replace(">", "").strip()
    return cleaned

def parse_vin(response_text):
    """
    Parses ISO-TP multi-frame response for Mode 09 PID 02:
    Example input:
    7E8 10 14 49 02 01 31 46 41
    7E8 21 36 50 38 43 46 35 48
    7E8 22 35 31 32 33 34 35 36
    """
    lines = [line.strip() for line in response_text.replace('\r', '\n').split('\n') if line.strip()]
    hex_tokens = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        # Split into tokens if space separated, or chunk into 2-hex pairs if no spaces
        if ' ' in line:
            parts = line.split()
            if len(parts[0]) == 3 or len(parts[0]) == 8:
                parts = parts[1:]
        else:
            # 3-char header for 11-bit CAN (e.g. 7E8)
            header = line[:3]
            body = line[3:]
            parts = [body[i:i+2] for i in range(0, len(body), 2)]
            
        if not parts:
            continue
        
        # Check frame type in byte 0
        first_byte = int(parts[0], 16)
        frame_type = (first_byte >> 4) & 0xF
        if frame_type == 0:  # Single frame
            hex_tokens.extend(parts[1:])
        elif frame_type == 1:  # First frame: [1x xx] [len]
            # payload starts from index 2
            hex_tokens.extend(parts[2:])
        elif frame_type == 2:  # Consecutive frame: [2x]
            # payload starts from index 1
            hex_tokens.extend(parts[1:])
        else:
            hex_tokens.extend(parts)

    print(f"[DEBUG] Reassembled hex tokens: {hex_tokens}")
    # Payload should start with 49 02 01 (Mode 09 response, PID 02, 1 item)
    if len(hex_tokens) >= 3 and hex_tokens[0] == "49" and hex_tokens[1] == "02":
        vin_hex = hex_tokens[3:20]  # 17 bytes of VIN
        vin = "".join([chr(int(h, 16)) for h in vin_hex if 32 <= int(h, 16) <= 126])
        return vin
    return None

def main():
    host = "127.0.0.1"
    port = 35000
    print(f"Connecting to ELM327 Emulator at {host}:{port}...")
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect((host, port))
    
    time.sleep(0.1)
    
    # 1. Reset
    send_command(sock, "ATZ")
    time.sleep(0.2)
    
    # 2. Echo Off
    send_command(sock, "ATE0")
    
    # 3. Linefeeds Off
    send_command(sock, "ATL0")
    
    # 4. Spaces Off (or On, per spike: ATS0, then ATH1, ATSP6)
    send_command(sock, "ATS0")
    
    # 5. Headers On
    send_command(sock, "ATH1")
    
    # 6. Set Protocol 6 (ISO 15765-4 11-bit CAN 500k)
    send_command(sock, "ATSP6")
    
    # 7. Check Battery Voltage
    send_command(sock, "ATRV")
    
    # 8. Query Mode 09 PID 02 (VIN)
    vin_resp = send_command(sock, "0902")
    vin = parse_vin(vin_resp)
    print(f"\n==========================================")
    print(f"SPIKE RESULT: Decoded VIN = '{vin}'")
    print(f"==========================================")
    
    # 9. Query Live RPM (Mode 01 PID 0C)
    rpm_resp = send_command(sock, "010C")
    print(f"Live RPM raw response: {rpm_resp}")
    
    sock.close()

if __name__ == "__main__":
    main()
