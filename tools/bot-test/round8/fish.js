// 낚시: 전리품 표가 test:t 로 바뀐 서버에서 행운 속성 0 / -1(불운) / +5 로 낚아 흙 수를 센다. 기대: 불운 0, 보통 ≈50%, +5 ≈86%
const U = require('../round2/u.js');
const { cmd, sleep } = U;
const N = parseInt(process.argv[2] || '6');
async function round(bot, label, luck) {
  cmd('attribute Tester minecraft:generic.luck base set ' + luck); await sleep(500);
  let dirt = 0, stone = 0;
  for (let i = 0; i < N; i++) {
    cmd('clear Tester dirt'); cmd('clear Tester stone');
    const before = () => bot.entities;
    const got = await (async () => {
      bot.activateItem(); await sleep(1500);   // 던지기
      const hook = () => Object.values(bot.entities).find(e => e.name === 'fishing_bobber');
      let rest = null, end = Date.now() + 60000;
      while (Date.now() < end) {
        const h = hook(); if (!h) { await sleep(200); continue; }
        if (rest === null && h.position.y < -60.5 && Math.abs(h.velocity.y) < 0.02) { await sleep(800); rest = hook().position.y; }
        if (rest === null) { await sleep(50); continue; }
        if (rest !== null && h.position.y < rest - 0.08) { await sleep(150); bot.activateItem(); return true; }   // 입질(찌가 잠긴다) → 거두기
        await sleep(50);
      }
      bot.activateItem(); return false;
    })();
    if (!got) { console.log('낚시 실패/시간 초과'); try { bot.activateItem(); } catch (e) {} continue; }
    await sleep(3000);
    const d = U.count(bot, 'dirt'), st = U.count(bot, 'stone');
    if (d > 0) dirt++; else if (st > 0) stone++;
    cmd('clear Tester dirt'); cmd('clear Tester stone'); await sleep(300);
  }
  return `${label}(luck ${luck}) 낚시: 흙 ${dirt} / 돌 ${stone} (N=${N})`;
}
(async () => {
  const out = [];
  const bot = await U.setup('Tester');
  cmd('gamemode creative Tester'); cmd('gamerule doMobSpawning false'); cmd('difficulty peaceful');
  cmd('fill 2 -62 2 10 -62 10 water'); cmd('fill 2 -61 2 10 -61 10 air'); await sleep(800);
  cmd('tp Tester 6.5 -60 12.5 180 30');
  cmd('give Tester fishing_rod[enchantments={levels:{"minecraft:lure":3}}]'); await sleep(1200);
  await U.hold(bot, 'fishing_rod'); await bot.look(0, 0.0, true);
  for (const [label, luck] of (process.env.ROUNDS ? JSON.parse(process.env.ROUNDS) : [['불운', -1], ['보통', 0], ['행운', 5]])) out.push(await round(bot, label, luck));
  console.log('=== 결과 ===\n' + out.join('\n')); process.exit(0);
})().catch(e => { console.log('ERR', e); process.exit(1); });
