const { setup, chat, sleep, ask, num, near, hold } = require('./u');
(async () => {
  const bot = await setup();
  const pk = {}; for (const n of ['damage_event', 'enter_combat_event', 'end_combat_event']) bot._client.on(n, p => { (pk[n] ??= []).push({ t: Date.now(), ...p }); });
  for (const c of ['/gamerule naturalRegeneration false', '/gamemode survival', '/clear @s', '/kill @e[type=!player]', '/effect clear @s']) await chat(bot, c);
  const hp = async () => num(bot, '@s', 'Health');
  console.log('RESULT 시작 체력', await hp(), bot.health);
  await chat(bot, '/tp @s 40.5 -50 40.5', 3000);
  console.log('RESULT 철퇴 없이 10칸 낙하 뒤', await hp(), bot.health);
  await chat(bot, '/effect give @s minecraft:instant_health 1 3', 800);
  await chat(bot, '/give @s minecraft:mace', 500); await hold(bot, 'mace');
  await chat(bot, '/summon minecraft:pig 30.5 -60 30.5 {NoAI:1b,Tags:["m"],Health:100f,attributes:[{id:"minecraft:generic.max_health",base:100d}]}', 800);
  await chat(bot, '/tp @s 30.5 -50 30.5 0 90', 300);
  console.log('RESULT 철퇴 전 체력', await hp());
  const mp = near(bot, 'pig')[0]; let at = false;
  for (let i = 0; i < 100 && !at; i++) { await sleep(25); if (bot.entity.position.y - (mp.position.y + 0.9) < 1.6) { bot.attack(mp); at = true; } }
  await sleep(1500);
  console.log('RESULT 철퇴 돼지 피해', 100 - await num(bot, '@e[type=pig,tag=m,limit=1]', 'Health'), '낙하 뒤 체력', await hp());
  // 전투 종료
  await chat(bot, '/tp @s 0 -60 0 0 0', 800); await chat(bot, '/clear @s', 200);
  await chat(bot, '/effect give @s minecraft:resistance 120 3 true', 300); await chat(bot, '/difficulty easy', 300);
  await chat(bot, '/summon minecraft:husk 0 -60 2 {PersistenceRequired:1b}', 300);
  await sleep(5000); await chat(bot, '/kill @e[type=husk]', 300); const tk = Date.now();
  await sleep(18000); console.log('RESULT enter', pk.enter_combat_event?.length, 'end_combat(죽인 뒤 ms)', JSON.stringify((pk.end_combat_event || []).map(p => ({ dt: p.t - tk, d: p.duration }))));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
