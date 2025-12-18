#!/bin/bash

# Compile the TestMcpServer
# Requires Java 21 or higher

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Compiling TestMcpServer..."
javac -d "$SCRIPT_DIR" "$SCRIPT_DIR/TestMcpServer.java"

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Class file created: $SCRIPT_DIR/TestMcpServer.class"
else
    echo "Compilation failed!"
    exit 1
fi
