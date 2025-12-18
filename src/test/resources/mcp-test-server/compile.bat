@echo off
REM Compile the TestMcpServer
REM Requires Java 21 or higher

echo Compiling TestMcpServer...
javac TestMcpServer.java

if %errorlevel% equ 0 (
    echo Compilation successful!
    echo Class file created: TestMcpServer.class
) else (
    echo Compilation failed!
    exit /b 1
)
