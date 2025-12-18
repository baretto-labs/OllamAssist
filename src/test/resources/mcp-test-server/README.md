# MCP Test Server

A minimal Model Context Protocol (MCP) server implemented in pure Java (JDK only, no external dependencies) for testing the OllamAssist tool approval system.

## Features

- **Pure Java**: Uses only JDK 21 standard library classes
- **STDIO Transport**: JSON-RPC communication via stdin/stdout
- **Two Test Tools**:
  - `echo`: Safe tool that returns input arguments (for testing approval flow)
  - `write_file`: Tool with side-effects (for testing approval/denial)
- **MCP Protocol Compliant**: Implements MCP specification version 2024-11-05

## Quick Start

### 1. Compile the Server

**On Linux/macOS:**
```bash
chmod +x compile.sh
./compile.sh
```

**On Windows:**
```batch
compile.bat
```

**Or manually:**
```bash
javac TestMcpServer.java
```

### 2. Test the Server Manually

**On Linux/macOS:**
```bash
chmod +x run.sh
./run.sh
```

**On Windows:**
```batch
run.bat
```

The server will wait for JSON-RPC input on stdin. You can test it with:

**Linux/macOS:**
```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' | java -cp . TestMcpServer
```

**Windows:**
```batch
echo {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}} | java -cp . TestMcpServer
```

Expected response:
```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"test-mcp-server","version":"1.0.0"},"capabilities":{"tools":{"listChanged":false}}}}
```

### 3. Configure in OllamAssist

1. Open IntelliJ IDEA with OllamAssist plugin
2. Go to **Settings/Preferences > Tools > OllamAssist > MCP Servers**
3. Click **Add** to create a new MCP server configuration
4. Fill in the following:
   - **Name**: `Test MCP Server`
   - **Transport Type**: `STDIO`
   - **Command**: Use the full path to `run.sh` (Linux/macOS) or `run.bat` (Windows)
   - **Working Directory**: Full path to the `mcp-test-server` directory
   - **Environment Variables**: (leave empty)

**Example paths:**

**Linux/macOS:**
- Command: `/Users/mehdi/Workspaces/Labs/OllamAssist/src/test/resources/mcp-test-server/run.sh`
- Working Directory: `/Users/mehdi/Workspaces/Labs/OllamAssist/src/test/resources/mcp-test-server`

**Windows:**
- Command: `C:\Users\mehdi\Workspaces\Labs\OllamAssist\src\test\resources\mcp-test-server\run.bat`
- Working Directory: `C:\Users\mehdi\Workspaces\Labs\OllamAssist\src\test\resources\mcp-test-server`

5. Click **OK** to save

## Available Tools

### 1. echo

Returns the input message as-is. This is a safe tool with no side effects.

**Parameters:**
- `message` (string, required): Message to echo back

**Example:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "echo",
    "arguments": {
      "message": "Hello, MCP!"
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Echo: Hello, MCP!"
      }
    ],
    "isError": false
  }
}
```

**Testing Approval Flow:**
- This tool should be approved automatically or with minimal friction
- Use it to verify that the approval system allows safe operations

### 2. write_file

Writes content to a file in the temporary directory. This tool has side effects.

**Parameters:**
- `filename` (string, required): Name of the file to write
- `content` (string, required): Content to write to the file

**Example:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "write_file",
    "arguments": {
      "filename": "test.txt",
      "content": "This is a test file."
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "File written successfully: /tmp/mcp-test-server/test.txt"
      }
    ],
    "isError": false
  }
}
```

**Testing Approval Flow:**
- This tool should trigger approval prompts since it modifies the filesystem
- Use it to verify that the approval system blocks or requests confirmation for dangerous operations
- Files are written to `/tmp/mcp-test-server/` (or equivalent temp directory on your OS)
- Filenames are sanitized to prevent directory traversal attacks

## MCP Protocol Methods

### initialize

Initializes the MCP server and returns server capabilities.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {}
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "serverInfo": {
      "name": "test-mcp-server",
      "version": "1.0.0"
    },
    "capabilities": {
      "tools": {
        "listChanged": false
      }
    }
  }
}
```

### tools/list

Lists all available tools.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "echo",
        "description": "Returns the input message as-is. Safe tool for testing approval flow.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "message": {
              "type": "string",
              "description": "Message to echo back"
            }
          },
          "required": ["message"]
        }
      },
      {
        "name": "write_file",
        "description": "Writes content to a file in the temp directory. Has side-effects for testing approval/denial.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "filename": {
              "type": "string",
              "description": "Name of the file to write"
            },
            "content": {
              "type": "string",
              "description": "Content to write to the file"
            }
          },
          "required": ["filename", "content"]
        }
      }
    ]
  }
}
```

### tools/call

Executes a tool with the provided arguments.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "echo",
    "arguments": {
      "message": "Test"
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Echo: Test"
      }
    ],
    "isError": false
  }
}
```

### ping

Simple ping method for connection testing.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "ping",
  "params": {}
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {}
}
```

## Testing Scenarios

### Scenario 1: Test Safe Tool Approval

1. Configure the server in OllamAssist
2. In the chat, ask: "Use the echo tool to say hello"
3. Expected behavior:
   - Tool should be approved (automatically or with minimal prompt)
   - Response should contain "Echo: hello"

### Scenario 2: Test Dangerous Tool Approval

1. In the chat, ask: "Use the write_file tool to create a file named test.txt with content 'Hello World'"
2. Expected behavior:
   - Approval dialog should appear asking for confirmation
   - User can approve or deny the operation
   - If approved, file is created in `/tmp/mcp-test-server/test.txt`
   - If denied, operation is cancelled

### Scenario 3: Test Multiple Tool Calls

1. Ask: "Echo 'test1', then write a file named output.txt with 'test2', then echo 'test3'"
2. Expected behavior:
   - First echo should execute (approved)
   - Write operation should prompt for approval
   - Third echo should execute after approval/denial

## Troubleshooting

### Server doesn't start

- Ensure Java 21 or higher is installed: `java -version`
- Check that the script has execute permissions: `chmod +x run.sh`
- Verify the .class file exists: `ls TestMcpServer.class`

### No response from server

- Check that the command path is absolute in OllamAssist settings
- Verify the working directory exists
- Look for errors in IntelliJ IDEA logs: Help > Show Log in Finder/Explorer

### Tools not appearing

- Ensure the server initialized successfully
- Check that the `tools/list` method is being called after initialization
- Enable MCP debug logging in OllamAssist settings (if available)

## File Locations

- **Server source**: `TestMcpServer.java`
- **Compiled class**: `TestMcpServer.class` (after compilation)
- **Temporary files**: `/tmp/mcp-test-server/` (or OS equivalent)

## Requirements

- Java Development Kit (JDK) 21 or higher
- No external dependencies required
- Platform support:
  - **Linux/macOS**: Native bash script support
  - **Windows**: Native batch script support (or use WSL for bash scripts)

## Implementation Notes

This server implements a minimal subset of the MCP protocol sufficient for testing tool approval:

- **JSON Parser**: Simple recursive descent parser for basic JSON structures
- **JSON Serializer**: Basic serializer for Maps and Lists
- **STDIO Transport**: Reads from stdin, writes to stdout (one JSON object per line)
- **Security**: Filename sanitization prevents directory traversal attacks
- **Thread Safety**: Single-threaded design (adequate for testing)

For production use, consider using a full JSON library (e.g., Jackson, Gson) and the official MCP SDK.

## References

- [Model Context Protocol Specification](https://spec.modelcontextprotocol.io/)
- [MCP STDIO Transport](https://spec.modelcontextprotocol.io/specification/basic/transports/#stdio)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
