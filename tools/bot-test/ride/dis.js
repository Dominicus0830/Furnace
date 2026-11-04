const { makeBot, spawned, cmd, sleep } = require('../lib');
(async () => {
  const bot = makeBot('Tester', { plugins: { resource_pack: false } });
  let vid = null; bot._client.on('set_passengers', p => { if (p.passengers.includes(bot.entity?.id)) vid = p.entityId; else if (p.entityId === vid) vid = null; });
  await spawned(bot); await sleep(3000);
  console.log('접속 시 탑승', vid != null);
  bot.dismount(); await sleep(2000);
  console.log('내린 뒤 탑승', vid != null);
  bot.quit(); await sleep(1500); process.exit(0);
})();
