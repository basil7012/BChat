@echo off
set DIR=%~dp0
set CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
    echo Error: gradle-wrapper.jar not found at %CLASSPATH%
    exit /b 1
)
java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
