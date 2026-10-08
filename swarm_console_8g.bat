@echo off
set "OLDPATH=%PATH%"
set "PATH=%PATH%;.\lib"

rem Heap/GC settings for operational use on a 32 GB RAM machine.
rem Increase -Xmx to 12g if Swarm still runs out of memory with many open traces.
set "JAVA_OPTS=-Xms512m -Xmx8g -XX:+UseG1GC"

java %JAVA_OPTS% -Duser.country=US -Duser.language=us -jar lib\Swarm.jar %*
set "PATH=%OLDPATH%"
