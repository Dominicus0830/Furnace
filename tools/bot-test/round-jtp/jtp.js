// 조인 텔레포트 봇 시험: 봇마다 리스폰(차원)·위치 동기화·목적지 청크 도착을 기록한다. 사용: node jtp.js
const { makeBot, sleep } = require('../lib');

async function run(name, expect) {
  const bot = makeBot(name);
  const t0 = Date.now();
  const log = [];
  bot._client.on('respawn', p => log.push(`respawn dim=${p.worldName ?? p.dimension?.name ?? JSON.stringify(p.dimension)} keep=${p.copyMetadata}`));
  bot._client.on('position', p => log.push(`position ${p.x.toFixed(1)},${p.z.toFixed(1)} id=${p.teleportId}`));
  let done = false, chunkAt = null, err = null;
  bot.on('kicked', r => { err = 'kicked ' + JSON.stringify(r); });
  const deadline = Date.now() + 30000;
  await new Promise(res => bot.once('spawn', res));
  for (const step of expect) {
    while (Date.now() < deadline) {
      const col = bot.world.getColumn(step.cx, step.cz);
      if (col && bot.game.dimension === step.dim) { chunkAt = Date.now() - t0; break; }
      await sleep(50);
    }
    const pos = bot.entity.position;
    console.log(JSON.stringify({ name, step: step.label, dim: bot.game.dimension, pos: [pos.x.toFixed(1), pos.y.toFixed(1), pos.z.toFixed(1)],
      chunkMs: chunkAt, ok: chunkAt != null && bot.game.dimension === step.dim, err }));
    chunkAt = null;
  }
  console.log(JSON.stringify({ name, packets: log }));
  bot.quit();
  await sleep(500);
}

(async () => {
  await run('Neth1', [{ label: 'join->nether', dim: 'the_nether', cx: 6, cz: 6 }]);
  await run('Far1', [{ label: 'join->far', dim: 'overworld', cx: 187, cz: 187 }]);
  await run('Late1', [{ label: 'after-join->nether', dim: 'the_nether', cx: 6, cz: 6 }]);
  await run('Back1', [{ label: 'join->nether', dim: 'the_nether', cx: 6, cz: 6 }, { label: 'back->overworld-far', dim: 'overworld', cx: -126, cz: 62 }]);
  process.exit(0);
})();
