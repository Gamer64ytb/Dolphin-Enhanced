#!/usr/bin/env bash
# Usage: ./run_system_profile.sh /path/to/dolphin "optional args" duration_seconds output.csv
# Example: ./run_system_profile.sh ./Dolphin -b 60 output.csv

set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 /path/to/dolphin duration_seconds [-- <dolphin args> ] output.csv"
  echo "Or: $0 /path/to/dolphin duration_seconds output.csv"
  exit 1
fi

DOLPHIN_BIN="$1"
DURATION="$2"
shift 2

# remaining args are optional dolphin args; last arg if present and contains .csv is output
OUTPUT="profile_output.csv"
if [ "$#" -ge 1 ]; then
  LAST="${!#}"
  if [[ "$LAST" == *.csv ]]; then
    # treat last arg as output file
    OUTPUT="$LAST"
    # remove last arg from list
    set -- "${@:1:$(($#-1))}"
  fi
fi

DOLPHIN_ARGS=("$@")

if [ ! -x "$DOLPHIN_BIN" ]; then
  echo "Dolphin binary not found or not executable: $DOLPHIN_BIN"
  exit 2
fi

# Launch emulator in background
"$DOLPHIN_BIN" "${DOLPHIN_ARGS[@]}" &
DOLPHIN_PID=$!

echo "pid,timestamp,cpu_percent,mem_kb" > "$OUTPUT"
START_TS=$(date +%s)
END_TS=$((START_TS + DURATION))

while kill -0 "$DOLPHIN_PID" 2>/dev/null; do
  TS=$(date +%s.%3N)
  # ps -p pid -o %cpu,rss --no-headers
  read -r CPU RSS <<< $(ps -p "$DOLPHIN_PID" -o %cpu=,rss= || echo "0 0")
  if [ -z "$CPU" ]; then CPU=0; fi
  if [ -z "$RSS" ]; then RSS=0; fi
  echo "$DOLPHIN_PID,$TS,$CPU,$RSS" >> "$OUTPUT"

  if [ "$(date +%s)" -ge "$END_TS" ]; then
    echo "Reached duration; killing process $DOLPHIN_PID"
    kill "$DOLPHIN_PID" || true
    break
  fi
  sleep 0.1
done

# Wait for process to exit
wait "$DOLPHIN_PID" 2>/dev/null || true

echo "Profile written to $OUTPUT"