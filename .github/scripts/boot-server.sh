#!/usr/bin/env bash
# Starts the dedicated server in the current directory, waits for it to finish loading, then stops it.
# Fails if the server exits early, never finishes loading, or did not load the vendingmachine mod.
set -euo pipefail

mkfifo console
./run.sh nogui < console > server.log 2>&1 &
server=$!
exec 3> console

for _ in $(seq 1 600); do
  if grep -q 'Done (' server.log; then break; fi
  if ! kill -0 "$server" 2>/dev/null; then break; fi
  sleep 1
done

if ! grep -q 'Done (' server.log; then
  tail -n 200 server.log
  echo 'Server did not finish loading.' >&2
  kill "$server" 2>/dev/null || true
  exit 1
fi

echo stop >&3
wait "$server"

if ! grep -q 'Loaded [0-9]* vending machine trades' server.log; then
  echo 'The vendingmachine mod did not load.' >&2
  exit 1
fi
echo 'Server booted with the vendingmachine mod.'
