// 수정 jar 재시험: setblock NBT, 경작지 밟기, /data get 뒤 어깨 앵무새 재접속, 비op 깊은 곳 채굴(SamplePlugin), 상인 타이머 저장
const { setup, chat, sleep, ask, near, uuidInts, makeBot, spawned, cmd } = require('./u');
const V = require('vec3');
(async () => {
  const t0 = Date.now();
  let bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamemode survival', '/kill @e[type=!player]', '/effect give @s resistance 1000 4 true', '/gamerule doMobSpawning true']) await chat(bot, c);
  // 1) setblock·fill NBT
  await chat(bot, '/setblock 3 -60 3 minecraft:chest{Items:[{Slot:3b,id:"minecraft:diamond",count:5}]}', 500);
  console.log('RESULT setblock 상자', await ask(bot, '/data get block 3 -60 3 Items[0].count', /block data|not a block|has the following/));
  await chat(bot, '/fill 5 -60 3 6 -60 3 minecraft:barrel{Items:[{Slot:0b,id:"minecraft:stone",count:7}]}', 500);
  console.log('RESULT fill 통', await ask(bot, '/data get block 6 -60 3 Items[0].count', /block data|not a block|has the following/));
  await chat(bot, '/setblock 3 -60 3 minecraft:stone', 800);
  console.log('RESULT 상자를 돌로 바꾼 뒤 바닥 아이템', near(bot, 'item').length);
  // 2) 경작지 밟기(5칸 낙하) ×3
  for (let t = 0; t < 3; t++) {
    const x = 10 + t * 2;
    await chat(bot, `/setblock ${x} -61 0 minecraft:farmland[moisture=7]`, 300);
    await chat(bot, `/tp @s ${x}.5 -55 0.5`, 2500);
    console.log('RESULT 낙하 뒤', t, bot.blockAt(new V(x, -61, 0))?.name);
  }
  // 3) 어깨 앵무새 → /data get entity @s → 재접속
  await chat(bot, '/tp @s 30.5 -60 0.5', 1000);
  await chat(bot, `/summon minecraft:parrot 31.5 -60 1.5 {Owner:${OWN},Variant:2}`, 6000);
  for (let i = 0; i < 10; i++) { const m = await ask(bot, '/data get entity @s ShoulderEntityLeft.Variant', /entity data|Found|has the following/); if (/: 2/.test(m)) break; await chat(bot, '/tp @e[type=parrot] @s', 200); await sleep(300); }
  console.log('RESULT 어깨', await ask(bot, '/data get entity @s ShoulderEntityLeft.Variant', /has the following|Found/));
  console.log('RESULT 전체 /data get 길이', (await ask(bot, '/data get entity @s', /has the following|Found/) || '').length);
  bot.quit(); await sleep(2000);
  bot = makeBot('Tester', { plugins: { resource_pack: false } }); await spawned(bot); await sleep(2500);
  console.log('RESULT 재접속 뒤 어깨', await ask(bot, '/data get entity @s ShoulderEntityLeft.Variant', /has the following|Found|not/));
  // 4) 비op 로 y=-61 캐기
  cmd('deop Tester'); await sleep(1000);
  const ab = []; bot.on('actionBar', m => ab.push(m.toString()));
  bot._client.on('system_chat', p => { if (p.isActionBar) ab.push(JSON.stringify(p.content)); });
  const target = bot.blockAt(bot.entity.position.offset(1, -1, 0).floored());
  console.log('RESULT 비op 캐기 대상', target.name, target.position.toString());
  await bot.dig(target, true).catch(e => console.log('dig', e.message)); await sleep(1500);
  console.log('RESULT 캔 뒤', bot.blockAt(target.position)?.name, '액션바', JSON.stringify(ab));
  cmd('op Tester'); await sleep(800);
  // 5) 상인 타이머: 접속 70초 넘긴 뒤 저장
  while (Date.now() - t0 < 80000) await sleep(1000);
  cmd('save-all flush'); await sleep(3000);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
