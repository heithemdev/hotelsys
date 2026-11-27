@echo off
setlocal
if not defined JAVA_HOME for /f "tokens=2,*" %%A in ('reg query HKCU\Environment /v JAVA_HOME 2^>nul ^| findstr JAVA_HOME') do set "JAVA_HOME=%%B"
if not defined JAVA_HOME (
  echo Installez JDK 21 ou configurez JAVA_HOME.
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
pushd "%~dp0"
call mvnw.cmd -B -ntp javafx:run
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%
