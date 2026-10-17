#!/usr/bin/env bash
set -euo pipefail

echo "=============================================="
echo " TontOS: Running Local Subsystems (Dev Mode)"
echo "=============================================="

cargo test --workspace

echo "Starting TontOS core daemons in development mode..."
echo "1. Power Controller"
cargo run -p tont-powerd &
PID_POWER=$!

echo "2. App Lifecycle Daemon"
cargo run -p tont-appd &
PID_APPD=$!

echo "3. Radio Sync Scheduler"
cargo run -p tont-syncd &
PID_SYNC=$!

echo "4. Telephony Daemon"
cargo run -p tont-telephony &
PID_TEL=$!

echo "5. Sensor Daemon"
cargo run -p tont-sensors &
PID_SENS=$!

trap "kill $PID_POWER $PID_APPD $PID_SYNC $PID_TEL $PID_SENS 2>/dev/null || true" EXIT INT TERM

wait
