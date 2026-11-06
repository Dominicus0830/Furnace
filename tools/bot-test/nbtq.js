const { makeBot, spawned, chat, cmd, sleep } = require('./lib');
(async () => {
  const op = makeBot('Admin'); await spawned(op); await sleep(1000);
  op._client.on('nbt_query_response', p => console.log('RESULT 응답 tx', p.transactionId, 'nbt', p.nbt ? JSON.stringify(p.nbt).slice(0, 80) : p.nbt));
  await chat(op, '/summon minecraft:cow 2 -60 2 {NoAI:1b}', 800);
  await chat(op, '/setblock 0 -60 3 minecraft:chest', 500);
  const cow = Object.values(op.entities).find(e => e.name === 'cow');
  op._client.write('query_entity_nbt', { transactionId: 7, entityId: cow.id });
  op._client.write('query_block_nbt', { transactionId: 8, location: { x: 0, y: -60, z: 3 } });
  op._client.write('query_block_nbt', { transactionId: 9, location: { x: 0, y: -61, z: 0 } });
  await sleep(1500); op.quit(); await sleep(300); process.exit(0);
})();
