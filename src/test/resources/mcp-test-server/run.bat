@echo off
REM Run the TestMcpServer
REM Requires Java 21 or higher

if not exist TestMcpServer.class (
    echo TestMcpServer.class not found. Please compile first using compile.bat
    exit /b 1
)

REM Run the server
java -cp . TestMcpServer
