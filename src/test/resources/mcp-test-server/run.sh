#!/bin/bash

# Run the TestMcpServer
# Requires Java 21 or higher

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -f "$SCRIPT_DIR/TestMcpServer.class" ]; then
    echo "TestMcpServer.class not found. Please compile first using compile.sh"
    exit 1
fi

# Run the server
java -cp "$SCRIPT_DIR" TestMcpServer
