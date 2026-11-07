// 원인 사슬(B4) 봇 시험 — round14/probe/CauseProbe 를 넣은 서버에서. 결과는 "RESULT 이름 PASS|FAIL 근거" 줄.
// SRV=<서버 폴더> node round14/cause.js
const fs = require('fs');
const V = require('vec3');
const { setup, chat, sleep, hold } = require('../round2/u');
const SRV = process.env.SRV;
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
let logPos = 0;
function mark() { logPos = fs.statSync(SRV + '/server.log').size; }
function causes(re) {
  const s = fs.readFileSync(SRV + '/server.log').subarray(logPos).toString('utf8');   // 바이트 위치(로그에 한글이 있다)
  return s.split('\n').filter(l => l.includes('[CAUSE] ') && re.test(l)).map(l => l.slice(l.indexOf('[CAUSE] ') + 8).trim());
}
const has = (list, line) => list.includes(line);
async function waitFor(re, n, ms) { const end = Date.now() + ms; while (Date.now() < end) { if (causes(re).length >= n) return; await sleep(200); } }

(async () => {
  const bot = await setup();
  for (const c of ['/gamerule doMobSpawning false', '/gamerule doDaylightCycle false', '/time set day', '/difficulty easy', '/gamerule naturalRegeneration false',
    '/gamemode creative', '/clear @s', '/fill 0 -60 -8 40 -50 12 air', '/tp @s 4 -60 1']) await chat(bot, c);
  await sleep(1000);

  // ---------------------------------------------------------------- 플레이어 → TNT → TNT → 폭발(+ 피해)
  await chat(bot, '/setblock 4 -60 4 minecraft:tnt', 200);
  await chat(bot, '/setblock 4 -60 6 minecraft:tnt', 200);
  await chat(bot, '/summon minecraft:cow 6 -60 5 {NoAI:1b}', 300);
  await chat(bot, '/give @s minecraft:flint_and_steel', 300);
  await chat(bot, '/gamemode survival', 300);
  await hold(bot, 'flint_and_steel');
  mark();
  await bot.lookAt(new V(4.5, -59.5, 4.5));
  await bot.activateBlock(bot.blockAt(new V(4, -60, 4)), new V(0, 0, -1));
  await sleep(300);
  await chat(bot, '/tp @s 4 -60 -3', 200);
  await waitFor(/EntityExplodeEvent/, 2, 8000);
  await sleep(500);
  const tnt = causes(/minecraft:tnt \|/);
  const me = 'minecraft:player:ignite=Tester';
  result('점화된 TNT 스폰 사슬 = 켠 플레이어', has(tnt, `EntitySpawnEvent minecraft:tnt | ${me}`), JSON.stringify(tnt));
  result('첫 폭발 사슬 = TNT ← 플레이어', has(tnt, `EntityExplodeEvent minecraft:tnt | minecraft:tnt:explode < ${me}`), JSON.stringify(tnt));
  result('휘말린 TNT 스폰 사슬 = 앞 폭발 ← 플레이어', has(tnt, `EntitySpawnEvent minecraft:tnt | minecraft:tnt:explode < ${me}`), JSON.stringify(tnt));
  result('둘째 폭발 사슬 = TNT ← TNT ← 플레이어', has(tnt, `EntityExplodeEvent minecraft:tnt | minecraft:tnt:explode < minecraft:tnt:explode < ${me}`), JSON.stringify(tnt));
  const dmg = causes(/EntityDamageEvent/);
  result('폭발 피해(소) 사슬에 켠 플레이어', dmg.some(l => /^EntityDamageEvent minecraft:cow minecraft:\w*explosion \| minecraft:tnt:damage < (minecraft:tnt:explode < )?minecraft:player:ignite=Tester$/.test(l)), JSON.stringify(dmg));
  result('폭발 피해(플레이어) 사슬에 켠 플레이어', dmg.some(l => /^EntityDamageEvent minecraft:player minecraft:\w*explosion \| minecraft:tnt:damage < minecraft:player:ignite=Tester$/.test(l)), JSON.stringify(dmg));
  await chat(bot, '/gamemode creative', 300);
  await chat(bot, '/kill @e[type=minecraft:cow]', 300);

  // ---------------------------------------------------------------- 레드스톤
  mark();
  await chat(bot, '/setblock 20 -60 4 minecraft:tnt', 200);
  await chat(bot, '/setblock 21 -60 4 minecraft:redstone_block', 200);
  await waitFor(/EntityExplodeEvent/, 1, 6000);
  const rs = causes(/minecraft:tnt \|/);
  result('레드스톤 점화 사슬 = 그 TNT 칸', has(rs, 'EntitySpawnEvent minecraft:tnt | minecraft:tnt:redstone') && has(rs, 'EntityExplodeEvent minecraft:tnt | minecraft:tnt:explode < minecraft:tnt:redstone'), JSON.stringify(rs));

  // ---------------------------------------------------------------- 발사기
  mark();
  await chat(bot, '/setblock 30 -60 4 minecraft:dispenser[facing=up]{Items:[{Slot:0b,id:"minecraft:tnt",count:1}]}', 300);
  await chat(bot, '/setblock 31 -60 4 minecraft:redstone_block', 200);
  await waitFor(/EntityExplodeEvent/, 1, 6000);
  const ds = causes(/minecraft:tnt \|/);
  result('발사기 TNT 사슬 = 발사기', has(ds, 'EntitySpawnEvent minecraft:tnt | minecraft:dispenser:dispense') && has(ds, 'EntityExplodeEvent minecraft:tnt | minecraft:tnt:explode < minecraft:dispenser:dispense'), JSON.stringify(ds));

  // ---------------------------------------------------------------- 눈덩이(투사체 → 쏜 플레이어)
  await chat(bot, '/tp @s 10 -60 -4 0 0', 300);
  await chat(bot, '/summon minecraft:cow 10 -60 -1 {NoAI:1b}', 300);
  await chat(bot, '/give @s minecraft:snowball 4', 300);
  await hold(bot, 'snowball');
  await sleep(500);
  mark();
  await bot.lookAt(new V(10, -59.3, -1));
  bot.activateItem();
  await waitFor(/EntityDamageEvent minecraft:cow/, 1, 4000);
  const sb = causes(/snowball/);
  result('눈덩이 발사 사슬 = 던진 플레이어', has(sb, 'ProjectileLaunchEvent minecraft:snowball | minecraft:player:shoot=Tester'), JSON.stringify(sb));
  result('눈덩이 피해 사슬 = 눈덩이 ← 던진 플레이어', sb.some(l => /^EntityDamageEvent minecraft:cow minecraft:thrown \| minecraft:snowball:damage < minecraft:player:shoot=Tester$/.test(l)), JSON.stringify(sb));

  // ---------------------------------------------------------------- 블록 부수기
  await chat(bot, '/setblock 12 -60 -4 minecraft:dirt', 300);
  mark();
  await bot.dig(bot.blockAt(new V(12, -60, -4)));
  await sleep(500);
  const br = causes(/BlockBreakEvent/);
  result('BlockBreakEvent 사슬 = 부순 플레이어', has(br, 'BlockBreakEvent minecraft:dirt | minecraft:player:break=Tester'), JSON.stringify(br));

  const fail = results.filter(r => !r[1]).length;
  console.log(`SUMMARY ${results.length - fail}/${results.length}`);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
