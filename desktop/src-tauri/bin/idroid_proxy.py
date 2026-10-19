#!/usr/bin/env python3
import socket
import threading
import sys
import os
import time

def forward(src, dst):
    try:
        while True:
            data = src.recv(4096)
            if not data:
                break
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try:
            src.close()
        except Exception:
            pass
        try:
            dst.close()
        except Exception:
            pass

def parent_watcher(parent_pid):
    while True:
        time.sleep(1)
        try:
            os.kill(parent_pid, 0)
        except OSError:
            os._exit(0)

def main():
    listen_port = int(sys.argv[1]) if len(sys.argv) > 1 else 53100
    target_port = int(sys.argv[2]) if len(sys.argv) > 2 else 53102
    if len(sys.argv) > 3:
        try:
            ppid = int(sys.argv[3])
            threading.Thread(target=parent_watcher, args=(ppid,), daemon=True).start()
        except Exception:
            pass

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind(("0.0.0.0", listen_port))
    except Exception as e:
        print(f"[Idroid Proxy] Could not bind 0.0.0.0:{listen_port}: {e}", flush=True)
        sys.exit(1)

    server.listen(10)
    print(f"[Idroid Proxy] Relay active on 0.0.0.0:{listen_port} -> 127.0.0.1:{target_port}", flush=True)

    while True:
        try:
            client_sock, client_addr = server.accept()
            print(f"[Idroid Proxy] Connection accepted from {client_addr}", flush=True)
            try:
                target_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                target_sock.connect(("127.0.0.1", target_port))
                threading.Thread(target=forward, args=(client_sock, target_sock), daemon=True).start()
                threading.Thread(target=forward, args=(target_sock, client_sock), daemon=True).start()
            except Exception as e:
                print(f"[Idroid Proxy] Failed to connect to local target {target_port}: {e}", flush=True)
                client_sock.close()
        except Exception:
            break

if __name__ == "__main__":
    main()
