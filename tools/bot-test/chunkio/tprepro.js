// 재접속 직후 /tp 실패 재현용: park = 멀리 세워 두고 나감, jump = 접속 직후 원점으로 /tp.
const { makeBot, spawned, cmd, sleep } = require('../lib');
(async () => {
  const bot = makeBot('ChunkIo');
  await spawned(bot);
  await sleep(1000);
  cmd('op ChunkIo');
  await sleep(500);
  bot.chat('/gamemode creative');
  if (process.argv[2] === 'park') { bot.chat('/tp @s -99 160 70'); await sleep(4000); }
  else { bot.chat('/tp @s 0 200 0'); await sleep(7000); console.log('pos', bot.entity.position.toString()); }
  bot.quit(); await sleep(500); process.exit(0);
})();
