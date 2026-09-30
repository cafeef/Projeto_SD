@echo off
rem Inicia o servidor em modo console.
rem
rem Usa "java" e nao "javaw" de proposito: a janela de console precisa ficar
rem aberta, porque e nela que aparecem as mensagens JSON trocadas.
cd /d "%~dp0"
java -cp "bin;lib\gson-2.14.0.jar;lib\h2-2.5.250.jar" servidor.ServidorConsole %*
