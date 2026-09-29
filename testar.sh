#!/usr/bin/env bash
# Roda a bateria de testes automatizados do projeto.
#
#   ./testar.sh [porta]     porta padrao: 45123 (so para os testes, sobe e cai sozinho)
#
# Testa o que ja existe: enquadramento das mensagens (Framing) e validacao de
# campos (Validador). As operacoes do protocolo -- register, login, read_user,
# update_user, delete_user, logout -- ainda NAO estao implementadas.
set -euo pipefail
cd "$(dirname "$0")"

PORTA="${1:-45123}"
CP_LIB="lib/gson-2.14.0.jar:lib/h2-2.5.250.jar"
LOG="$(mktemp -t servidor-teste-XXXXXX.log)"

echo "### Compilando"
./compilar.sh
mkdir -p bin-testes
javac --release 21 -encoding UTF-8 -cp "bin:$CP_LIB" -d bin-testes testes/*.java
echo

echo "### Validacao de campos (sem rede)"
java -cp "bin-testes:bin:$CP_LIB" VerificaValidador
echo

echo "### Banco de dados (H2 em memoria)"
java -cp "bin-testes:bin:$CP_LIB" VerificaBanco
echo

echo "### Subindo o servidor na porta $PORTA"
java -cp "bin:$CP_LIB" servidor.ServidorConsole "$PORTA" --memoria > "$LOG" 2>&1 &
SRV=$!
trap 'kill "$SRV" 2>/dev/null || true' EXIT

pronto=0
for _ in $(seq 1 60); do
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
