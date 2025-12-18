#!/bin/bash

# Test script for MCP Test Server
# This script runs various tests to verify the server is working correctly

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -f "$SCRIPT_DIR/TestMcpServer.class" ]; then
    echo "ERROR: TestMcpServer.class not found. Please run compile.sh first."
    exit 1
fi

echo "========================================="
echo "MCP Test Server - Test Suite"
echo "========================================="
echo ""

# Test 1: Initialize
echo "Test 1: Initialize"
echo "Request: {\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
RESULT=$(echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' | java -cp "$SCRIPT_DIR" TestMcpServer)
echo "Response: $RESULT"
if [[ $RESULT == *"test-mcp-server"* ]]; then
    echo "✓ PASSED"
else
    echo "✗ FAILED"
fi
echo ""

# Test 2: Tools List
echo "Test 2: List Tools"
echo "Request: Initialize + tools/list"
RESULT=$(cat <<EOF | java -cp "$SCRIPT_DIR" TestMcpServer
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
EOF
)
echo "Response (last line):"
echo "$RESULT" | tail -1
if [[ $RESULT == *"echo"* ]] && [[ $RESULT == *"write_file"* ]]; then
    echo "✓ PASSED"
else
    echo "✗ FAILED"
fi
echo ""

# Test 3: Echo Tool
echo "Test 3: Echo Tool"
echo "Request: Initialize + echo('Hello, MCP!')"
RESULT=$(cat <<EOF | java -cp "$SCRIPT_DIR" TestMcpServer
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"message":"Hello, MCP!"}}}
EOF
)
echo "Response (last line):"
echo "$RESULT" | tail -1
if [[ $RESULT == *"Echo: Hello, MCP"* ]]; then
    echo "✓ PASSED"
else
    echo "✗ FAILED"
fi
echo ""

# Test 4: Write File Tool
echo "Test 4: Write File Tool"
echo "Request: Initialize + write_file('test-suite.txt', 'Test content')"
RESULT=$(cat <<EOF | java -cp "$SCRIPT_DIR" TestMcpServer
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"write_file","arguments":{"filename":"test-suite.txt","content":"Test content from test suite"}}}
EOF
)
echo "Response (last line):"
echo "$RESULT" | tail -1
if [[ $RESULT == *"File written successfully"* ]]; then
    echo "✓ PASSED"
    # Extract file path and verify content
    FILE_PATH=$(echo "$RESULT" | grep -o '/.*test-suite.txt')
    if [ -n "$FILE_PATH" ] && [ -f "$FILE_PATH" ]; then
        CONTENT=$(cat "$FILE_PATH")
        if [[ $CONTENT == "Test content from test suite" ]]; then
            echo "  ✓ File content verified: $FILE_PATH"
        else
            echo "  ✗ File content mismatch"
        fi
    fi
else
    echo "✗ FAILED"
fi
echo ""

# Test 5: Ping
echo "Test 5: Ping"
echo "Request: {\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\",\"params\":{}}"
RESULT=$(echo '{"jsonrpc":"2.0","id":4,"method":"ping","params":{}}' | java -cp "$SCRIPT_DIR" TestMcpServer)
echo "Response: $RESULT"
if [[ $RESULT == *"\"result\""* ]]; then
    echo "✓ PASSED"
else
    echo "✗ FAILED"
fi
echo ""

echo "========================================="
echo "Test Suite Complete"
echo "========================================="
