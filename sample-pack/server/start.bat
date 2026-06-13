@echo off
REM Arc API Showcase server launcher.
REM Requires JDK 21. Edit JAVA below if your JDK lives elsewhere.

set "JAVA=C:\Users\wagwa\.jdks\corretto-21.0.10\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

"%JAVA%" -Xms1G -Xmx2G -jar server.jar nogui
pause
