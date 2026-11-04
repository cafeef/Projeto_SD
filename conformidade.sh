#!/usr/bin/env bash
#
# Roda o testador oficial do protocolo contra o nosso servidor.
#
# Fica separado de ./testar.sh de proposito: os testes de ./testar.sh rodam so
# com o JDK e as bibliotecas de lib/, sem instalar nada, e e isso que garante
# que o projeto roda na maquina do professor. Este script depende do Node e do
# repositorio do protocolo, entao e um extra -- se faltar algum dos dois, ele
# avisa e sai sem reclamar.
#
#   ./conformidade.sh [porta]
#
# Onde esta o repositorio do protocolo (sobrescreva com a variavel PROTOCOLO):
PROTOCOLO="${PROTOCOLO:-$(cd "$(dirname "$0")/.." && pwd)/protocolo-reserva-salas}"
PORTA="${1:-12345}"

set -u
cd "$(dirname "$0")" || exit 1

TESTADOR="$PROTOCOLO/scripts/testar-servidor.mjs"
if ! command -v node > /dev/null 2>&1; then
    echo "Node nao encontrado -- o testador oficial precisa dele. Pulando."
    exit 0
fi
if [ ! -f "$TESTADOR" ]; then
    echo "Testador nao encontrado em $TESTADOR"
    echo "Clone o repositorio do protocolo ou aponte PROTOCOLO para ele."
    exit 0
fi

./compilar.sh || exit 1

# --memoria: o testador cria e remove contas, entao nao vale sujar o banco de
# arquivo nem deixar o resultado depender do que sobrou da rodada anterior.
./run-servidor-console.sh "$PORTA" --memoria > /tmp/conformidade-servidor.log 2>&1 &
SERVIDOR=$!
trap 'kill $SERVIDOR 2> /dev/null' EXIT

for _ in $(seq 20); do
    grep -q "escutando" /tmp/conformidade-servidor.log 2> /dev/null && break
    sleep 0.5
done
if ! grep -q "escutando" /tmp/conformidade-servidor.log 2> /dev/null; then
    echo "O servidor nao subiu. Log:"
    cat /tmp/conformidade-servidor.log
    exit 1
fi

node "$TESTADOR" 127.0.0.1 "$PORTA"
