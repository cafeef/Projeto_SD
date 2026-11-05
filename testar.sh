#!/usr/bin/env bash
# Roda a bateria de testes automatizados do projeto.
#
#   ./testar.sh [porta]     porta padrao: 45123 (so para os testes, sobe e cai sozinho)
#
# Sobe um servidor com banco EM MEMORIA numa porta so dos testes, roda todas as
# suites contra ele e derruba no fim. O banco de verdade, em dados/, nao e
# tocado.
set -euo pipefail
cd "$(dirname "$0")"

PORTA="${1:-45123}"
CP_LIB="lib/gson-2.14.0.jar:lib/h2-2.5.250.jar"
LOG="$(mktemp -t servidor-teste-XXXXXX.log)"

echo "### Compilando"
./compilar.sh
mkdir -p bin-testes
javac --release 17 -encoding UTF-8 -cp "bin:$CP_LIB" -d bin-testes testes/*.java
echo

echo "### Validacao de campos (sem rede)"
java -cp "bin-testes:bin:$CP_LIB" VerificaValidador
echo

echo "### Mensagens exibidas na tela (sem rede)"
java -cp "bin-testes:bin:$CP_LIB" cliente.VerificaMensagens
echo

echo "### Requisicoes montadas pelo cliente (sem rede)"
java -cp "bin-testes:bin:$CP_LIB" cliente.VerificaCliente
echo

echo "### Banco de dados (H2 em memoria)"
java -cp "bin-testes:bin:$CP_LIB" VerificaBanco
echo

echo "### Subindo o servidor na porta $PORTA"

# Esperar a porta de uma rodada anterior ser liberada. Sem isto, duas execucoes
# seguidas se atropelam: o servidor novo nao consegue o bind, e a espera abaixo
# se daria por satisfeita com o servidor VELHO, que morre no meio da bateria.
for _ in $(seq 1 40); do
    (exec 3<>/dev/tcp/127.0.0.1/"$PORTA") 2>/dev/null || break
    exec 3<&- 3>&-
    sleep 0.25
done
if (exec 3<>/dev/tcp/127.0.0.1/"$PORTA") 2>/dev/null; then
    exec 3<&- 3>&-
    echo "ERRO: a porta $PORTA ja esta ocupada. Encerre o outro servidor,"
    echo "      ou rode com outra porta: ./testar.sh <porta>"
    exit 1
fi

java -cp "bin:$CP_LIB" servidor.ServidorConsole "$PORTA" --memoria > "$LOG" 2>&1 &
SRV=$!
trap 'kill "$SRV" 2>/dev/null || true' EXIT

pronto=0
for _ in $(seq 1 60); do
    # 'kill -0' so pergunta se o processo existe; nao manda sinal nenhum. Sem
    # esta checagem, um servidor que morreu ao subir deixaria a espera rodando
    # os 15 segundos inteiros antes de acusar.
    if ! kill -0 "$SRV" 2>/dev/null; then
        echo "ERRO: o servidor morreu ao subir. Log:" ; cat "$LOG" ; exit 1
    fi
    if (exec 3<>/dev/tcp/127.0.0.1/"$PORTA") 2>/dev/null; then
        exec 3<&- 3>&- ; pronto=1 ; break
    fi
    sleep 0.25
done
if [ "$pronto" -ne 1 ]; then
    echo "ERRO: o servidor nao subiu. Log:" ; cat "$LOG" ; exit 1
fi
echo

echo "### Transporte e erros de protocolo"
java -cp "bin-testes:bin:$CP_LIB" VerificaProtocolo "$PORTA"
echo

echo "### Operacoes da EP-1 (register, login, logout, read/update/delete_user)"
java -cp "bin-testes:bin:$CP_LIB" VerificaOperacoes "$PORTA"
echo

echo "### Telas e conexao do cliente"
java -cp "bin-testes:bin:$CP_LIB" VerificaInterfaces "$PORTA"
echo

echo "### Tela do cliente, dirigida por cliques"
java -cp "bin-testes:bin:$CP_LIB" cliente.VerificaTelaDoCliente "$PORTA"
echo

echo "### Tres clientes simultaneos (regra 1.1)"
pids=()
for n in 1 2 3; do
    java -cp "bin-testes:bin:$CP_LIB" VerificaProtocolo "$PORTA" > "$LOG.par$n" 2>&1 &
    pids+=("$!")
done
falhou=0
n=1
for p in "${pids[@]}"; do
    if wait "$p"; then
        echo "  OK    cliente $n: $(grep -o '[0-9]* passaram, [0-9]* falharam' "$LOG.par$n")"
    else
        echo "  FALHA cliente $n"; cat "$LOG.par$n"; falhou=1
    fi
    n=$((n + 1))
done
conexoes=$(grep -c 'conexao aceita' "$LOG" || true)
echo "  servidor atendeu $conexoes conexoes em threads separadas"
rm -f "$LOG".par*

echo
if [ "$falhou" -eq 0 ]; then
    echo "### TUDO PASSOU"
else
    echo "### HOUVE FALHAS"; exit 1
fi
