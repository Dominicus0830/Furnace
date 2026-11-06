// 피해 계산 단계 이벤트 봇 시험 — round9/probe/DamageProbe 를 넣은 서버에서. SRV=<서버 폴더> node round9/damage.js
// 결과는 "RESULT 이름 PASS|FAIL 근거" 줄. 기대값은 바닐라 식(철 가슴 방어력 6, 저항 I 20%, 흡수 체력 4)으로 손계산.
const fs = require('fs');
const { setup, chat, sleep, cmd, num } = require('../round2/u');
const SRV = process.env.SRV;
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
let logPos = 0;
function mark() { logPos = fs.statSync(SRV + '/server.log').size; }
function dmg() { return fs.readFileSync(SRV + '/server.log').subarray(logPos).toString('utf8').split('\n').filter(l => l.includes('[DMG] ')).map(l => l.slice(l.indexOf('[DMG] ') + 6)); }
const close = (a, b) => typeof a === 'number' && Math.abs(a - b) < 1e-3;

// CombatRules.getDamageAfterAbsorb(damage, armor, toughness=0)
function afterArmor(d, armor) { const f = 2; const f1 = Math.min(Math.max(armor - d / f, armor * 0.2), 20); return d * (1 - f1 / 25); }

async function reset(bot) {
  await chat(bot, '/effect clear @s', 300);
  await chat(bot, '/effect give @s minecraft:instant_health 1 10 true', 500);
  await chat(bot, '/effect give @s minecraft:resistance 1000 0 true', 300);
  await chat(bot, '/effect give @s minecraft:absorption 1000 0 true', 300);
  await sleep(1200);   // 무적 시간
}
async function hit(bot, type, amount) {
  mark();
  await chat(bot, `/damage @s ${amount} ${type}`, 900);
  return { health: await num(bot, '@s', 'Health'), abs: await num(bot, '@s', 'AbsorptionAmount'), line: dmg() };
}
async function dmgmode(bot, ...m) { await chat(bot, '/dmgmode ' + (m.length ? m.join(' ') : 'none'), 400); }

(async () => {
  const bot = await setup();
  for (const c of ['/gamerule doMobSpawning false', '/difficulty normal', '/gamerule naturalRegeneration false', '/gamemode survival', '/time set day', '/clear @s', '/tp @s 0 -60 0',
    '/item replace entity @s armor.chest with minecraft:iron_chestplate']) await chat(bot, c, 300);
  await sleep(1500);
  const armor = 6;
  await reset(bot);
  console.log('초기', await num(bot, '@s', 'Health'), await num(bot, '@s', 'AbsorptionAmount'));

  // 1. 읽기만(모드 없음) — 바닐라 식 그대로
  await dmgmode(bot);
  let r = await hit(bot, 'minecraft:cactus', 10);
  let a = afterArmor(10, armor), res = a * 0.8, lost = res - 4;
  result('단계 이벤트 읽기만: 체력이 바닐라 식대로', close(r.health, 20 - lost) && close(r.abs, 0) && r.line.length === 1, `health=${r.health} abs=${r.abs} ${JSON.stringify(r.line)} expected lost=${lost}`);
  result('이벤트 단계 값이 바닐라 식', r.line.length === 1 && r.line[0].includes(`armor=10.0->${a}`.slice(0, 14)) && /player minecraft:cactus/.test(r.line[0]), r.line[0]);

  // 2. 방어력 건너뛰기
  await reset(bot); await dmgmode(bot, 'noarmor');
  r = await hit(bot, 'minecraft:cactus', 10);
  lost = 10 * 0.8 - 4;
  result('ARMOR 건너뛰기: 방어력 없이 저항·흡수만', close(r.health, 20 - lost) && close(r.abs, 0), `health=${r.health} abs=${r.abs} ${JSON.stringify(r.line)}`);

  // 3. 방어력 절반
  await reset(bot); await dmgmode(bot, 'halfarmor');
  r = await hit(bot, 'minecraft:cactus', 10);
  const half = 10 - (10 - afterArmor(10, armor)) * 0.5;
  lost = half * 0.8 - 4;
  result('ARMOR 줄이는 양 절반', close(r.health, 20 - lost), `health=${r.health} expected=${20 - lost} ${JSON.stringify(r.line)}`);

  // 4. base 2배
  await reset(bot); await dmgmode(bot, 'double');
  r = await hit(bot, 'minecraft:cactus', 10);
  lost = afterArmor(20, armor) * 0.8 - 4;
  result('base 2배: 뒤 단계가 새 값으로 다시 계산', close(r.health, 20 - lost), `health=${r.health} expected=${20 - lost} ${JSON.stringify(r.line)}`);

  // 5. 흡수 무시
  await reset(bot); await dmgmode(bot, 'noabsorb');
  r = await hit(bot, 'minecraft:cactus', 10);
  lost = afterArmor(10, armor) * 0.8;
  result('ABSORPTION 0: 흡수 체력은 그대로 4, 체력이 전부 깎임', close(r.health, 20 - lost) && close(r.abs, 4), `health=${r.health} abs=${r.abs} ${JSON.stringify(r.line)}`);

  // 6. 방어력 우회 피해(generic)에 방어력 강제 적용
  await reset(bot); await dmgmode(bot);
  r = await hit(bot, 'minecraft:generic', 10);
  result('generic(bypasses_armor): 읽기만 하면 방어력 건너뜀', close(r.health, 20 - (10 * 0.8 - 4)) && /armor=\(skip\)/.test(r.line[0] ?? ''), `health=${r.health} ${JSON.stringify(r.line)}`);
  await reset(bot); await dmgmode(bot, 'forcearmor');
  r = await hit(bot, 'minecraft:generic', 10);
  result('forcearmor: generic 에도 방어력 적용', close(r.health, 20 - (afterArmor(10, armor) * 0.8 - 4)), `health=${r.health} ${JSON.stringify(r.line)}`);

  await dmgmode(bot);
  const fails = results.filter(x => !x[1]);
  console.log(fails.length ? `FAILED ${fails.length}/${results.length}` : `ALL PASS ${results.length}`);
  bot.quit(); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.log('ERR', e); process.exit(2); });
