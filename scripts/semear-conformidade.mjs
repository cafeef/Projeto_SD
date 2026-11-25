// Semeia uma sala extra antes do testador oficial.
//
// O teste de troca de sala (regras 9.10 e 5.6) procura OUTRA sala ativa que
// funcione no mesmo horario da reserva. Num banco em memoria recem-criado so
// existe a sala que o proprio testador cadastra, entao ele se pula -- e o
// caminho mais intrincado da EP-3, o das duas trancas, ficaria sem exercicio.
//
// A sala semeada funciona em T1, T2 e T3 de segunda a sabado, para servir de
// destino qualquer que seja o dia que o testador escolher.
import net from 'node:net';

const [, , ip, porta] = process.argv;
const socket = net.createConnection({ host: ip, port: Number(porta) });
let resto = '';
const fila = [];
socket.on('data', (b) => {
  resto += b.toString('utf8');
  let i;
  while ((i = resto.indexOf('\n')) >= 0) {
    const linha = resto.slice(0, i);
    resto = resto.slice(i + 1);
    const espera = fila.shift();
    if (espera) espera(JSON.parse(linha));
  }
});

const chamar = (req) => new Promise((ok) => {
  fila.push(ok);
  socket.write(JSON.stringify(req) + '\n');
});

await new Promise((ok) => socket.once('connect', ok));

const login = await chamar({ op: 'login', email: 'admin@admin.com', password: 'admin' });
if (login.status !== '200') {
  console.error('semeadura: login do admin falhou -', login.message);
  process.exit(0);   // nao derruba a conformidade por causa da semeadura
}
const slots = [];
for (let dia = 2; dia <= 7; dia++) slots.push(`${dia}T1`, `${dia}T2`, `${dia}T3`);

const sala = await chamar({
  op: 'create_room', token: login.token, name: 'Sala Destino Troca',
  capacity: '20', location: 'Bloco D', resources: [], slots,
});
console.log(sala.status === '201'
  ? `semeadura: sala ${sala.room_id} criada para o teste de troca de sala`
  : `semeadura: nao criou a sala - ${sala.message}`);

await chamar({ op: 'logout', token: login.token });
socket.end();
