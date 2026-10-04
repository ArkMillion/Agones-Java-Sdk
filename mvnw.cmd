@ECHO OFF
SETLOCAL
SET "BASE_DIR=%~dp0"
SET "BASE_DIR=%BASE_DIR:~0,-1%"
SET "WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper"
SET "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
SET "WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

IF NOT EXIST "%WRAPPER_JAR%" (
    IF NOT EXIST "%WRAPPER_DIR%" MKDIR "%WRAPPER_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
    IF ERRORLEVEL 1 EXIT /B 1
)

IF DEFINED JAVA_HOME (
    SET "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) ELSE (
    SET "JAVA_CMD=java.exe"
)

"%JAVA_CMD%" "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
EXIT /B %ERRORLEVEL%
