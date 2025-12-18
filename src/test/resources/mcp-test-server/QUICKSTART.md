# MCP Test Server - Quick Start Guide

## 1. Compile (One Time)

**Linux/macOS:**
```bash
cd src/test/resources/mcp-test-server
./compile.sh
```

**Windows:**
```batch
cd src\test\resources\mcp-test-server
compile.bat
```

## 2. Test (Optional)

**Linux/macOS:**
```bash
./test.sh
```

Should show all tests passing.

## 3. Configure in OllamAssist

1. Open IntelliJ IDEA
2. Go to: **Settings > Tools > OllamAssist > MCP Servers**
3. Click **Add**
4. Configure:

**Linux/macOS:**
```
Name: Test MCP Server
Transport: STDIO
Command: /full/path/to/src/test/resources/mcp-test-server/run.sh
Working Dir: /full/path/to/src/test/resources/mcp-test-server
```

**Windows:**
```
Name: Test MCP Server
Transport: STDIO
Command: C:\full\path\to\src\test\resources\mcp-test-server\run.bat
Working Dir: C:\full\path\to\src\test\resources\mcp-test-server
```

5. Click **OK**

## 4. Test in Chat

**Safe Tool Test:**
```
Use the echo tool to say "Hello World"
```
Expected: Should execute with minimal friction

**Dangerous Tool Test:**
```
Use write_file to create a file named test.txt with content "Testing"
```
Expected: Should prompt for approval

## Tools Available

- **echo**: Safe tool, returns input (tests approval flow)
- **write_file**: Has side-effects (tests denial/approval)

## Troubleshooting

**Server won't start?**
- Check Java 21+ installed: `java -version`
- Verify scripts are executable: `chmod +x *.sh`
- Check .class file exists: `ls TestMcpServer.class`

**No tools appearing?**
- Verify absolute paths in configuration
- Check IntelliJ logs: Help > Show Log
- Try manual test: `./run.sh` and type initialize request

**Permission denied?**
```bash
chmod +x compile.sh run.sh test.sh
```

## Files Location

All files are in: `src/test/resources/mcp-test-server/`

See **README.md** for complete documentation.
