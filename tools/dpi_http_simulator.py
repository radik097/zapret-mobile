#!/usr/bin/env python3
import argparse
import json
import socket
import time
from pathlib import Path


BLOCKED_HOST = b"blocked.example"
ALLOW_BODY = b"dpi-split-proof"
BLOCK_BODY = b"dpi-blocked"


def main() -> int:
    parser = argparse.ArgumentParser(description="Deterministic local HTTP DPI simulator.")
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--report", required=True)
    parser.add_argument("--ready", required=True)
    parser.add_argument("--timeout", type=float, default=45.0)
    args = parser.parse_args()

    report_path = Path(args.report)
    ready_path = Path(args.ready)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    ready_path.parent.mkdir(parents=True, exist_ok=True)

    started = time.monotonic()
    chunks = []
    decision = "no_connection"
    first_chunk_has_complete_blocked_host = False
    full_request = b""
    skipped_connections = 0

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listener.bind(("127.0.0.1", args.port))
        listener.listen(1)
        listener.settimeout(1.0)
        ready_path.write_text("ready", encoding="utf-8")

        deadline = time.monotonic() + args.timeout
        while time.monotonic() < deadline:
            try:
                conn, _addr = listener.accept()
            except socket.timeout:
                continue

            with conn:
                conn.settimeout(1.0)
                try:
                    first = conn.recv(4096)
                except socket.timeout:
                    skipped_connections += 1
                    continue
                if not first:
                    skipped_connections += 1
                    continue

                now = time.monotonic()
                full_request += first
                chunks.append(chunk_report(1, first, now - started))
                first_chunk_has_complete_blocked_host = BLOCKED_HOST in first

                read_deadline = time.monotonic() + 2.0
                while b"\r\n\r\n" not in full_request:
                    if time.monotonic() >= read_deadline:
                        break
                    try:
                        data = conn.recv(4096)
                    except socket.timeout:
                        continue
                    if not data:
                        break
                    full_request += data
                    chunks.append(chunk_report(len(chunks) + 1, data, time.monotonic() - started))

                if first_chunk_has_complete_blocked_host:
                    decision = "blocked_unsplit"
                    body = BLOCK_BODY
                    status = b"451 Blocked By Local DPI"
                else:
                    decision = "allowed_split"
                    body = ALLOW_BODY
                    status = b"200 OK"

                response = (
                    b"HTTP/1.1 "
                    + status
                    + b"\r\nContent-Type: text/plain\r\nConnection: close\r\nContent-Length: "
                    + str(len(body)).encode("ascii")
                    + b"\r\n\r\n"
                    + body
                )
                conn.sendall(response)
                break

        if not chunks:
            write_report(
                report_path,
                args.port,
                chunks,
                decision,
                first_chunk_has_complete_blocked_host,
                full_request,
                f"timeout; skipped_connections={skipped_connections}",
            )
            return 2

    write_report(
        report_path,
        args.port,
        chunks,
        decision,
        first_chunk_has_complete_blocked_host,
        full_request,
        None if skipped_connections == 0 else f"skipped_connections={skipped_connections}",
    )
    return 0 if decision == "allowed_split" else 1


def chunk_report(index: int, data: bytes, elapsed: float) -> dict:
    return {
        "index": index,
        "len": len(data),
        "elapsed_ms": round(elapsed * 1000, 3),
        "ascii": data.decode("iso-8859-1", errors="replace"),
        "contains_complete_blocked_host": BLOCKED_HOST in data,
    }


def write_report(
    path: Path,
    port: int,
    chunks: list,
    decision: str,
    first_chunk_has_complete_blocked_host: bool,
    full_request: bytes,
    error: str | None,
) -> None:
    report = {
        "port": port,
        "decision": decision,
        "expected_decision": "allowed_split",
        "passed": decision == "allowed_split",
        "first_chunk_has_complete_blocked_host": first_chunk_has_complete_blocked_host,
        "chunk_count": len(chunks),
        "chunks": chunks,
        "full_request_contains_blocked_host": BLOCKED_HOST in full_request,
        "full_request_ascii": full_request.decode("iso-8859-1", errors="replace"),
        "error": error,
    }
    path.write_text(json.dumps(report, ensure_ascii=True, indent=2), encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
