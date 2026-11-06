// 시야 거리: 플러그인 setViewDistance 와 클라이언트 렌더 거리 변경이 움직이지 않아도 바로 반영되는지(청크 load/unload 수),
// setSimulationDistance 를 줄이면 시뮬레이션 거리 밖 엔티티가 멈추는지(공중에 소환한 아머 스탠드의 y)
const { setup, chat, sleep, ask } = require('../round2/u');
async function settle(c) { let last = -1, same = 0; while (same < 6) { await sleep(500); const v = c.loads + c.unloads; if (v === last) same++; else { same = 0; last = v; } } }
(async () => {
  const bot = await setup('Viewer');
  const c = { loads: 0, unloads: 0, have: new Set() };
  // setup 동안 이미 받은 청크도 센다(핸들러를 단 뒤에 온 것만 세면 가운데 청크가 빠진다)
  for (const col of bot.world.getColumns()) c.have.add(col.chunkX + ',' + col.chunkZ);
  bot._client.on('map_chunk', p => { c.loads++; c.have.add(p.x + ',' + p.z); });
  bot._client.on('unload_chunk', p => { c.unloads++; c.have.delete(p.chunkX + ',' + p.chunkZ); });
  await chat(bot, '/tp @s 8 -60 8', 500); await chat(bot, '/gamemode creative'); await settle(c);
  const maxR = () => Math.max(...[...c.have].map(s => s.split(',').map(Number)).map(([x, z]) => Math.max(Math.abs(x), Math.abs(z))));
  console.log('RESULT 처음: 청크', c.have.size, '반지름', maxR());
  await ask(bot, '/vd 2', /PROBE/); await settle(c);
  console.log('RESULT 플러그인 vd 2 (가만히): 청크', c.have.size, '반지름', maxR());
  await ask(bot, '/vd 10', /PROBE/); await settle(c);
  console.log('RESULT 플러그인 vd 10 (가만히): 청크', c.have.size, '반지름', maxR());
  bot.setSettings({ viewDistance: 3 }); await settle(c);
  console.log('RESULT 클라 렌더 3 (가만히): 청크', c.have.size, '반지름', maxR());
  bot.setSettings({ viewDistance: 12 }); await settle(c);
  console.log('RESULT 클라 렌더 12 (서버 10, 가만히): 청크', c.have.size, '반지름', maxR());
  // 시뮬레이션 거리
  const y = async n => { const m = await ask(bot, `/data get entity @e[type=armor_stand,tag=${n},limit=1] Pos[1]`, /data:|No entity|Found no/); const x = m && m.match(/data: (-?[\d.]+)/); return x ? +(+x[1]).toFixed(2) : m; };
  await chat(bot, '/kill @e[type=armor_stand]');
  await chat(bot, '/summon armor_stand 72 -40 8 {Tags:["near"]}'); await chat(bot, '/summon armor_stand 8 -40 8 {Tags:["home"]}'); await sleep(2500);
  console.log('RESULT sd 6(기본): 4칸 밖 청크 거리 4 스탠드 y', await y('near'), '제자리 y', await y('home'));
  await ask(bot, '/sd 2', /PROBE/); await sleep(1000);
  await chat(bot, '/summon armor_stand 72 -40 8 {Tags:["far"]}'); await chat(bot, '/summon armor_stand 8 -40 9 {Tags:["home2"]}'); await sleep(3000);
  console.log('RESULT sd 2: 청크 거리 4 스탠드 y', await y('far'), '(-40 이면 멈춤) 제자리 y', await y('home2'));
  await ask(bot, '/sd 6', /PROBE/); await sleep(3000);
  console.log('RESULT sd 6 로 되돌린 뒤 3초: 거리 4 스탠드 y', await y('far'));
  await ask(bot, '/vd 6', /PROBE/);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
