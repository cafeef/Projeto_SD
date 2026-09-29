#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
java -cp "bin:lib/gson-2.14.0.jar:lib/h2-2.5.250.jar" cliente.ClienteGUI "$@"
