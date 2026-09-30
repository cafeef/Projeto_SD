@echo off
rem Compila o projeto com --release 21.
rem
rem Por que 21 e nao a versao mais nova do JDK: bytecode gerado para um release
rem mais novo NAO roda em JVM mais antiga (UnsupportedClassVersionError), e nao se
rem sabe qual JDK a maquina da avaliacao tem. O 21 e LTS e roda em 21, 25 e 27.
setlocal
cd /d "%~dp0"

set "CP=lib\gson-2.14.0.jar;lib\h2-2.5.250.jar"

if exist bin rmdir /s /q bin
mkdir bin

rem javac nao aceita curinga recursivo: a lista de fontes vai num arquivo.
dir /s /b src\*.java > fontes.tmp
javac --release 21 -encoding UTF-8 -cp "%CP%" -d bin @fontes.tmp
set ERRO=%ERRORLEVEL%
del fontes.tmp

if not "%ERRO%"=="0" (
    echo.
    echo Falha na compilacao.
    rem pause para que a mensagem de erro sobreviva ao duplo clique, que
    rem fecharia a janela imediatamente.
    pause
    exit /b %ERRO%
)
echo Compilado em bin\ ^(release 21^).
endlocal
