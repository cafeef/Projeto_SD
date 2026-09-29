#!/usr/bin/env bash
# Compila o projeto com --release 21.
#
# Por que 21 e nao a versao mais nova do JDK: bytecode gerado para um release
# mais novo NAO roda em JVM mais antiga (UnsupportedClassVersionError), e nao se
# sabe qual JDK a maquina da avaliacao tem. O 21 e LTS e roda em 21, 25 e 27.
set -euo pipefail
cd "$(dirname "$0")"

CP="lib/gson-2.14.0.jar:lib/h2-2.5.250.jar"
rm -rf bin
mkdir -p bin
javac --release 21 -encoding UTF-8 -Xlint:all -cp "$CP" -d bin $(find src -name '*.java')
echo "Compilado em bin/ (release 21)."
