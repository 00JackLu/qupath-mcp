#!/usr/bin/env bash
#
# Smoke test for the QuPath MCP extension's HTTP server.
#
# Starts nothing itself — it assumes QuPath is running with the MCP extension
# and the HTTP transport enabled (QUPATH_MCP_TRANSPORT=HTTP, auto-start on, or
# started manually). It exercises the server end to end:
#
#   1. GET  /health          -> {"status":"ok"}
#   2. POST /mcp  initialize  -> protocolVersion + serverInfo
#   3. POST /mcp  ping        -> empty result
#   4. POST /mcp  tools/list  -> non-empty list of qupath_* tools
#   5. POST /mcp  tools/call  -> a well-formed JSON-RPC response (result OR error)
#
# Only dependency is `curl`. Exit code is 0 if every check passes, 1 otherwise.
#
# Usage:
#   ./test-mcp-server.sh                 # uses defaults / env vars
#   HOST=127.0.0.1 PORT=9527 ./test-mcp-server.sh
#
set -u

HOST="${HOST:-${QUPATH_MCP_HOST:-127.0.0.1}}"
PORT="${PORT:-${QUPATH_MCP_PORT:-9527}}"
BASE="http://${HOST}:${PORT}"
MCP="${BASE}/mcp"

pass=0
fail=0

green() { printf '\033[32m%s\033[0m' "$1"; }
red()   { printf '\033[31m%s\033[0m' "$1"; }
dim()   { printf '\033[2m%s\033[0m' "$1"; }

ok()   { echo "  [$(green PASS)] $1"; pass=$((pass+1)); }
bad()  { echo "  [$(red FAIL)] $1"; [ -n "${2:-}" ] && echo "         $(dim "$2")"; fail=$((fail+1)); }

# POST a JSON-RPC message to /mcp and echo the response body.
rpc() {
    curl -s --max-time 10 \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        -X POST "$MCP" -d "$1"
}

echo "Testing QuPath MCP server at ${BASE}"
echo

# --- 0. Reachability -------------------------------------------------------
if ! curl -s --max-time 5 -o /dev/null "${BASE}/health"; then
    echo "  [$(red FAIL)] Cannot reach ${BASE}"
    echo
    echo "  The server is not responding. Make sure QuPath is running with the"
    echo "  MCP extension and HTTP transport enabled, e.g.:"
    echo "      QUPATH_MCP_TRANSPORT=HTTP QUPATH_MCP_PORT=${PORT} \\"
    echo "      QUPATH_MCP_AUTO_START=true ./gradlew run"
    exit 1
fi

# --- 1. Health -------------------------------------------------------------
health="$(curl -s --max-time 5 "${BASE}/health")"
case "$health" in
    *'"status":"ok"'*) ok "GET /health -> $health" ;;
    *) bad "GET /health" "unexpected body: $health" ;;
esac

# --- 2. initialize ---------------------------------------------------------
init="$(rpc '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"smoke-test","version":"0"}}}')"
case "$init" in
    *'"protocolVersion"'*'"serverInfo"'*|*'"serverInfo"'*'"protocolVersion"'*)
        ok "POST /mcp initialize -> $init" ;;
    *) bad "POST /mcp initialize" "unexpected body: $init" ;;
esac

# --- 3. ping ---------------------------------------------------------------
ping="$(rpc '{"jsonrpc":"2.0","id":2,"method":"ping"}')"
case "$ping" in
    *'"result"'*) ok "POST /mcp ping -> $ping" ;;
    *) bad "POST /mcp ping" "unexpected body: $ping" ;;
esac

# --- 4. tools/list ---------------------------------------------------------
tools="$(rpc '{"jsonrpc":"2.0","id":3,"method":"tools/list"}')"
tool_count="$(printf '%s' "$tools" | grep -o '"name"' | wc -l | tr -d ' ')"
case "$tools" in
    *'"qupath_list_objects"'*)
        ok "POST /mcp tools/list -> ${tool_count} tools registered" ;;
    *) bad "POST /mcp tools/list" "expected qupath_* tools, got: $tools" ;;
esac

# --- 5. tools/call ---------------------------------------------------------
# qupath_list_objects is read-only. Without an open image it may return a
# JSON-RPC error; either a result or a well-formed error proves the call was
# routed to a tool and executed, which is what we want to confirm here.
call="$(rpc '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"qupath_list_objects","arguments":{}}}')"
case "$call" in
    *'"result"'*) ok "POST /mcp tools/call qupath_list_objects -> result returned" ;;
    *'"error"'*)  ok "POST /mcp tools/call qupath_list_objects -> handled (JSON-RPC error, e.g. no image open)" ;;
    *) bad "POST /mcp tools/call" "no JSON-RPC result/error: $call" ;;
esac

# --- Summary ---------------------------------------------------------------
echo
echo "Result: $(green "${pass} passed"), $([ "$fail" -gt 0 ] && red "${fail} failed" || echo "0 failed")"
[ "$fail" -eq 0 ] || exit 1
