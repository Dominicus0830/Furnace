// 바람 구슬 5틱 규칙: 생긴 직후 5틱 안에 때리면 튕겨 나가지 않고, 그 뒤에 때리면 시선 방향(AIM)으로 튕긴다.
// 소환한 구슬도 같은 카운터(noDeflectTicks=5)를 쓰므로 소환 직후/1초 뒤로 나눠 본다. 직접 던진 구슬은 던지자마자 때려 본다.
const { setup, chat, sleep, ask, hold } = require('../round2/u');
const V = require('vec3');
(async () => {
  const bot = await setup('Windy');
  for (const c of ['/gamemode survival', '/difficulty peaceful', '/kill @e[type=!player]', '/tp @s 0.5 -60 0.5 0 0', '/effect give @s resistance 1000 4 true', '/clear @s']) await chat(bot, c);
  await sleep(800);
  const motion = async tag => { const m = await ask(bot, `/data get entity @e[type=wind_charge,tag=${tag},limit=1] Motion[0]`, /data:|No entity|Found no/); const x = m && m.match(/data: (-?[\d.E-]+)/); return x ? (+x[1]).toFixed(3) : m; };
  async function trial(tag, waitMs) {
    let target = null; const spawnedAt = {};
    const onSpawn = e => { if (e.name === 'wind_charge' && !target) { target = e; spawnedAt.t = Date.now(); } };
    bot.on('entitySpawn', onSpawn);
    bot.chat(`/summon wind_charge 0.5 -58.9 2.5 {Tags:["${tag}"],Motion:[0.0,0.0,0.01],power:[0.0,0.0,0.0]}`);
    const end = Date.now() + 3000; while (!target && Date.now() < end) await sleep(5);
    bot.removeListener('entitySpawn', onSpawn);
    if (!target) return console.log('RESULT', tag, '구슬 안 보임');
    await bot.look(Math.PI / 2, 0, true);   // 서쪽(-x) 을 본다
    if (waitMs) await sleep(waitMs);
    const dt = Date.now() - spawnedAt.t;
    bot.attack(target); await sleep(150);
    console.log('RESULT', tag, `생긴 뒤 ${dt}ms 에 때림 → Motion[0]`, await motion(tag), '(음수로 커지면 튕김)');
  }
  await trial('early', 0);
  await chat(bot, '/kill @e[type=wind_charge]', 300);
  await trial('late', 1000);
  await chat(bot, '/kill @e[type=wind_charge]', 300);
  // 직접 던지고 때리기: 동쪽(+x)으로 던지고 서쪽(-x)을 본 채 때린다. 봇은 투사체 위치를 늦게 받으므로(갱신 10틱) 판정은 서버 Motion[0] 으로
  await chat(bot, '/give @s wind_charge 4'); await hold(bot, 'wind_charge');
  for (const [label, waitMs] of [['직접 던지고 바로', 0], ['직접 던지고 3틱(150ms) 뒤', 150], ['대조: 직접 던지고 8틱(400ms) 뒤', 400]]) {
    await chat(bot, '/kill @e[type=wind_charge]', 300); await chat(bot, '/tp @s 0.5 -60 0.5 -90 0', 600);
    let mine = null; const on = e => { if (e.name === 'wind_charge' && !mine) mine = e; }; bot.on('entitySpawn', on);
    await bot.look(-Math.PI / 2, 0.05, true);
    const t0 = Date.now(); bot.activateItem(); bot.deactivateItem();
    while (!mine && Date.now() - t0 < 2000) await sleep(2);
    bot.removeListener('entitySpawn', on);
    if (!mine) { console.log('RESULT', label, '구슬 안 보임'); continue; }
    await bot.look(Math.PI / 2, 0, true); if (waitMs) await sleep(waitMs);
    const dt = Date.now() - t0; bot.attack(mine); await sleep(100);
    const m = await ask(bot, '/data get entity @e[type=wind_charge,limit=1] Motion[0]', /data:|No entity|Found no/);
    console.log('RESULT', label, `${dt}ms 에 때림 → Motion[0]`, m && (m.match(/data: (-?[\d.E-]+)/) || [, m])[1], '(양수=그대로 동쪽, 음수=튕김)');
  }
  await sleep(1500);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
