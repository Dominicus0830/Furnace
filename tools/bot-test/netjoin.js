// 접속 직후 청크 전송 점검: 스폰까지 걸린 시간·받은 청크 수·마지막 청크 시각·배치 패킷 순서를 센다.
const { makeBot, sleep } = require('./lib');
(async () => {
  const t0 = Date.now();
  const bot = makeBot('NetJoin', { plugins: { resource_pack: false } });
  let chunks = 0, lastChunkAt = 0, tSpawn = -1; const order = [];
  bot._client.on('packet', (d, m) => {
    if (m.name === 'map_chunk') { chunks++; lastChunkAt = Date.now(); }
    else if (m.name === 'chunk_batch_start') order.push('S');
    else if (m.name === 'chunk_batch_finished') order.push('F' + d.batchSize);
  });
  bot.once('spawn', () => { tSpawn = Date.now() - t0; });
  let prev = -1;
  await sleep(2000);
  const end = Date.now() + 20000; while (Date.now() < end) { await sleep(500); }
  console.log(`RESULT spawn=${tSpawn}ms lastChunk=${lastChunkAt - t0}ms chunks=${chunks} order=${order.join(',')}`);
  bot.quit(); await sleep(300); process.exit(0);
})().catch(e => { console.log('FAIL', e.message); process.exit(1); });
