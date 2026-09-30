#!/usr/bin/env bash
# Compila o projeto com --release 17.
#
# Por que 17 e nao a versao mais nova do JDK: bytecode gerado para um release
# mais novo NAO roda em JVM mais antiga (UnsupportedClassVersionError), e nao se
# sabe qual JDK a maquina da avaliacao tem. O 17 e LTS e roda de 17 em diante.
set -euo pipefail
cd "$(dirname "$0")"

CP="lib/gson-2.14.0.jar:lib/h2-2.5.250.jar"
rm -rf bin
mkdir -p bin
javac --release 17 -encoding UTF-8 -Xlint:all -cp "$CP" -d bin $(find src -name '*.java')
echo "Compilado em bin/ (release 17)."
