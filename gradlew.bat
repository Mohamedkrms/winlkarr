@echo off

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute






























if %ERRORLEVEL% equ 0 exit /b 0:endexit /b 1:failif %ERRORLEVEL% equ 0 goto end"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar:execute
goto failecho location of your Java installation.echo Please set the JAVA_HOME variable in your environment to match theecho.echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.if exist "%JAVA_EXE%" goto executeset JAVA_EXE=%JAVA_HOME%\bin\java.exeset JAVA_HOME=%JAVA_HOME:"=%:findJavaFromJavaHome
goto failecho location of your Java installation.echo Please set the JAVA_HOME variable in your environment to match theecho.echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.echo.