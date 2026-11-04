const { makeBot, spawned, chat, cmd, sleep } = require('./lib');
(async () => {
  const op = makeBot('Admin'), g = makeBot('Guest'); await spawned(op); await spawned(g); await sleep(2000);
  op._client.on('nbt_query_response', p => console.log('RESULT 응답 tx', p.transactionId, p.nbt ? 'nbt 있음' : p.nbt));
  await chat(op, '/tp Guest 2 -60 0', 1500);
  const cow = Object.values(op.entities).find(e => e.name === 'cow');
  const guest = Object.values(op.entities).find(e => e.username === 'Guest');
  console.log('cow', cow?.id, cow?.position, 'guest', guest?.id, 'self', op.entity.id);
  op._client.write('query_entity_nbt', { transactionId: 21, entityId: op.entity.id });
  op._client.write('query_entity_nbt', { transactionId: 22, entityId: guest.id });
  op._client.write('query_entity_nbt', { transactionId: 23, entityId: cow.id });
  await sleep(2000); op.quit(); g.quit(); await sleep(300); process.exit(0);
})();
