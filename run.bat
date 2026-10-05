@echo off
REM Запуск эмулятора VFS на Kotlin (Swing GUI)
setlocal

cd /d "%~dp0"

if exist "gradlew.bat" (
    call gradlew.bat run
    goto :eof
)

where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
    call gradle run
    goto :eof
)

echo Ошибка: не найден ни gradlew.bat, ни gradle в PATH 1>&2
exit /b 1