// 철퇴 인챈트: 밀도(낙하 블록당 +0.5×레벨), 관통(방어 효율 -0.15×레벨, 내려치기 아니어도), 돌풍(내려친 뒤 위로 튕김)
const { setup, chat, sleep, num, near, hold } = require('../round2/u');
const ench = e => e ? `[enchantments={levels:{"minecraft:${e[0]}":${e[1]}}}]` : '';
(async () => {
  const bot = await setup('Macer');
  for (const c of ['/gamemode survival', '/gamerule doMobSpawning false', '/difficulty easy', '/gamerule naturalRegeneration false', '/kill @e[type=!player]', '/effect give @s resistance 600 4 true']) await chat(bot, c);
  async function give(e) { await chat(bot, '/clear @s', 200); await chat(bot, `/give @s minecraft:mace${ench(e)}`, 500); await hold(bot, 'mace'); }
  // 1) 관통: 땅에서 그냥 때리기, 다이아 갑옷 4벌(방어 20+좀비 기본 2, 강인 8) 좀비. 기대: 철퇴 6 → 갑옷만 1.2, 관통 IV 4.8(갑옷 효율 0.8−0.6)
  for (const [label, e, x] of [['관통 없음', null, 0], ['관통 IV', ['breach', 4], 6]]) {
    await give(e); await chat(bot, `/tp @s ${x + 0.5} -60 0.5 0 0`, 600);
    await chat(bot, `/summon zombie ${x + 0.5} -60 2.5 {NoAI:1b,Tags:["b${x}"],Health:100f,attributes:[{id:"minecraft:generic.max_health",base:100d}],ArmorItems:[{id:"diamond_boots",count:1},{id:"diamond_leggings",count:1},{id:"diamond_chestplate",count:1},{id:"diamond_helmet",count:1}]}`, 800);
    const z = near(bot, 'zombie')[0]; await bot.lookAt(z.position.offset(0, 1, 0)); await sleep(2200); bot.attack(z); await sleep(700);
    console.log('RESULT', label, '좀비(다이아 4벌) 피해', +(100 - await num(bot, `@e[type=zombie,tag=b${x},limit=1]`, 'Health')).toFixed(3), label === '관통 없음' ? '(기대 1.2)' : '(기대 4.8)');
  }
  await chat(bot, '/kill @e[type=zombie]', 300);
  // 2) 밀도: 같은 높이에서 떨어지며 돼지 내려치기
  async function smash(label, e, x) {
    await give(e);
    await chat(bot, `/summon pig ${x + 0.5} -60 30.5 {NoAI:1b,Tags:["s${x}"],Health:200f,attributes:[{id:"minecraft:generic.max_health",base:200d}]}`, 800);
    bot.physicsEnabled = false; await chat(bot, `/tp @s ${x + 0.5} -50 30.5 0 90`, 800); bot.physicsEnabled = true;   // 같은 높이에서 정지 상태로 떨어뜨린다
    const p = near(bot, 'pig')[0]; let ay = null;
    for (let i = 0; i < 200 && ay === null; i++) { await sleep(10); if (bot.entity.position.y - (p.position.y + 0.9) < 1.5) { ay = bot.entity.position.y; bot.attack(p); } }
    const ys = []; for (let i = 0; i < 30; i++) { await sleep(50); ys.push(bot.entity.position.y); }
    const dmg = 200 - await num(bot, `@e[type=pig,tag=s${x},limit=1]`, 'Health');
    // 바닐라 1.21.1 Player.attack: (철퇴 6 + getAttackDamageBonus) × 치명타 1.5 (cmx 바이트코드: 보너스를 더한 뒤 1.5 를 곱한다)
    console.log('RESULT', label, `-50 에서 떨어져 y ${ay.toFixed(2)} 에서 때림, 피해 ${dmg.toFixed(2)} → 보너스 ${(dmg / 1.5 - 6).toFixed(2)}`, `때린 뒤 최고 y ${Math.max(...ys).toFixed(2)}`);
    for (let i = 0; i < 100 && !bot.entity.onGround; i++) await sleep(100);   // 돌풍으로 뜬 뒤 착지할 때까지(공중에서 tp 하면 낙하 거리가 이어진다)
    await chat(bot, '/kill @e[type=pig]', 300); await sleep(1500);
  }
  await smash('밀도 없음', null, 0);
  await smash('밀도 V', ['density', 5], 6);
  await smash('돌풍 III', ['wind_burst', 3], 12);
  await smash('밀도 없음(재)', null, 18);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
