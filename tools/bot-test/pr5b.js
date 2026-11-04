const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const V = require('vec3');
(async () => {
  const bot = makeBot('Tester');
  await spawned(bot); await sleep(2000);
  let seq = 500;
  const leftClick = async (pos) => { const t = Date.now();
    bot._client.write('block_dig', { status: 0, location: pos, face: 1, sequence: seq++ }); bot._client.write('arm_animation', { hand: 0 });
    await sleep(150); bot._client.write('block_dig', { status: 1, location: pos, face: 1, sequence: seq++ });
    return await waitMsg(bot, /clicks in chunk|Too deep/, 3000, t); };
  await chat(bot, '/clear @s', 300); await chat(bot, '/give @s minecraft:stick', 500); await bot.equip(bot.inventory.items().find(i => i.name === 'stick'), 'hand');
  await chat(bot, '/tp @s 0.5 -60 0.5 0 0', 800);
  const target = new V(1, -60, 2);
  await chat(bot, '/setblock 1 -60 2 minecraft:stone', 400);
  await bot.lookAt(target.offset(0.5, 0.5, 0.5));
  for (let i = 0; i < 2; i++) console.log('RESULT 왼쪽 클릭', await leftClick(target));
  await chat(bot, '/gamemode creative', 600); await bot.equip(bot.inventory.items().find(i => i.name === 'stick'), 'hand');
  console.log('RESULT 크리에이티브 왼쪽 클릭', await leftClick(target)); await sleep(500);
  console.log('RESULT 크리에이티브 후 블록', bot.blockAt(target)?.name);
  await chat(bot, '/gamemode survival', 600);
  await bot.look(0, Math.PI / 2, true); await sleep(300);
  let t = Date.now(); bot.swingArm('right'); console.log('RESULT 하늘 보고 휘두르기', await waitMsg(bot, /entities within/, 3000, t));
  await bot.lookAt(target.offset(0.5, 0.5, 0.5), true); await sleep(300);
  t = Date.now(); bot.swingArm('right'); console.log('RESULT 블록 보고 휘두르기(안 떠야 함)', await waitMsg(bot, /entities within/, 1500, t));
  const cow = Object.values(bot.entities).find(x => x.name === 'cow');
  if (cow) { await bot.lookAt(cow.position.offset(0, 0.8, 0), true); await sleep(300); t = Date.now(); bot.swingArm('right'); console.log('RESULT 소 보고 휘두르기(안 떠야 함)', await waitMsg(bot, /entities within/, 1500, t), 'dist', cow.position.distanceTo(bot.entity.position).toFixed(2)); }
  // 스폰 위치
  await chat(bot, '/tp @s 0.5 -60 0.5 90 0', 800);  // MC yaw 90 = 서쪽(-x)
  t = Date.now(); bot.chat('/samplespawn'); await sleep(1500);
  console.log('RESULT 플레이어', bot.entity.position, 'MC yaw 90(서쪽) 뒤 좀비', Object.values(bot.entities).filter(x => x.name === 'zombie').map(z => z.position));
  await chat(bot, '/kill @e[type=zombie]', 300);
  console.log('RESULT 모든 메시지', JSON.stringify(bot.msgs.filter(m => /ab|actionbar/.test(m.pos)).map(m => m.m)));
  bot.quit(); await sleep(800); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
