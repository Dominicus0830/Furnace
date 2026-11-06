// 엔티티 AI·틱 성능 묶음(PR perf-entity) 확인 — 좀비 표적(클래스별 조회), 벽 돌아가기(길찾기 칸 종류 캐시),
// 점유 안 된 종만 있는 곳은 마을이 아님(바닐라 isVillageCenter), 소 600마리 틱 시간(/tick query).
// 사용: SRV=<서버 폴더> node entity/entity.js [perf]   — perf 면 틱 시간만 잰다(전/후 jar 비교용)
const U = require('../round2/u'); const { sleep, chat, ask } = U;
const count = async (bot, sel) => { const m = await ask(bot, `/execute if entity ${sel}`, /Test (passed|failed)/); const x = m && m.match(/count: (\d+)/); return x ? +x[1] : 0; };
const num = async (bot, sel, path) => { const m = await ask(bot, `/data get entity ${sel} ${path}`, /data:|Found no|No entity/i, 2000); const x = m && m.match(/data: (-?[\d.]+)/); return x ? +x[1] : null; };
(async () => {
  const bot = await U.setup('Ent');
  const base = ['/gamemode creative', '/difficulty easy', '/gamerule doMobSpawning false', '/gamerule doDaylightCycle false', '/time set midnight', '/kill @e[type=!player]'];
  for (const c of base) await chat(bot, c, 150);
  if (process.argv[2] === 'perf') {
    await chat(bot, '/tp @s 300.5 -58 300.5', 3000);
    await chat(bot, '/fill 280 -61 280 320 -59 320 stone', 300);
    await chat(bot, '/fill 285 -60 285 315 -58 315 air', 300);
    for (let i = 0; i < 600; i++) U.cmd(`summon cow ${286 + (i % 29) + 0.5} -60 ${286 + Math.floor(i / 29) % 29 + 0.5} {PersistenceRequired:1b}`);   // 콘솔(채팅 속도 제한 없음)
    await sleep(5000);
    const log = () => require('fs').readFileSync(process.env.SRV + '/server.log', 'utf8');
    U.cmd('execute if entity @e[type=cow]'); await sleep(1500);
    console.log('RESULT 소', (log().match(/Test passed, count: \d+/g) || []).pop());
    await sleep(15000);   // 예열
    for (let r = 0; r < 3; r++) {
      await sleep(10000);
      const n0 = log().length; U.cmd('tick query'); await sleep(1500);
      console.log('RESULT 틱', r, log().slice(n0).split('\n').filter(l => /tick|P50|ms/i.test(l)).map(l => l.replace(/^.*INFO\]: /, '')).join(' | '));
    }
    bot.quit(); await sleep(500); process.exit(0);
  }
  // A. 점유 안 된 종만 있는 곳: 나쁜 징조가 습격 징조로 바뀌지 않는다
  await chat(bot, '/tp @s 500.5 -60 500.5', 3000);
  await chat(bot, '/setblock 500 -60 503 bell[attachment=floor]', 300);
  await chat(bot, '/gamemode survival', 200);
  await chat(bot, '/effect give @s bad_omen 6000 0', 200);
  await sleep(6000);
  const eff = await ask(bot, '/data get entity @s active_effects', /entity data|Found/, 2000);
  console.log('RESULT A 빈 종만 있는 곳 습격 징조', /raid_omen/.test(eff ?? ''), '(바닐라: false)');
  await chat(bot, '/effect clear @s', 200);
  await chat(bot, '/gamemode creative', 200);
  // B. 좀비가 주민을 노린다(NearestAttackableTargetGoal 이 주민 클래스만 훑음)
  // 봇은 상자 밖(615,615)에 둔다 — 상자 안 주민 자리에 서 있으면 봇 몸이 좀비를 밀어내 좀비가 주민을 한 번만 치고 멈춘 것처럼 보인다(2026-11-06 확인)
  await chat(bot, '/tp @s 615.5 -60 615.5', 3000);
  await chat(bot, '/fill 595 -61 595 606 -57 606 glass hollow', 300);
  await chat(bot, '/fill 596 -60 596 605 -58 605 air', 300);
  await chat(bot, '/summon villager 600.5 -60 600.5 {PersistenceRequired:1b,NoAI:1b}', 200);
  await chat(bot, '/summon zombie 604.5 -60 604.5 {PersistenceRequired:1b}', 200);
  const hp0 = await num(bot, '@e[type=villager,limit=1,sort=nearest]', 'Health');
  let hp = hp0, gone = false;
  for (let i = 0; i < 30; i++) { await sleep(1000); hp = await num(bot, '@e[type=villager,limit=1,sort=nearest]', 'Health'); if (hp == null) { gone = true; break; } if (hp < hp0 - 3) break; }   // 한 대(3)가 아니라 두 대 이상 맞는지
  console.log('RESULT B 주민 체력', hp0, '→', hp, gone ? '(죽거나 좀비 주민이 됨)' : '', '(기대: 6 이상 줄어듦 — 좀비가 계속 침)');
  // C. 울타리를 돌아 플레이어에게 온다(길찾기). 울타리(1.5칸)는 넘지 못하지만 눈높이 시야는 지나가 표적은 잡는다.
  await chat(bot, '/kill @e[type=!player]', 300);
  await chat(bot, '/tp @s 700.5 -60 708.5', 3000);
  await chat(bot, '/fill 694 -60 705 706 -60 705 oak_fence', 300);   // z=705 울타리, 서쪽 끝은 x=693 줄로 막아 동쪽(x=707)으로만 돌게
  await chat(bot, '/fill 693 -60 698 693 -60 712 oak_fence', 300);
  await chat(bot, '/summon zombie 700.5 -60 701.5 {PersistenceRequired:1b}', 300);
  await chat(bot, '/gamemode survival', 200);
  await chat(bot, '/effect give @s resistance 1000 4 true', 200);
  let best = 1e9, path = [];
  for (let i = 0; i < 30; i++) {
    await sleep(1000);
    const c = Object.values(bot.entities).filter(e => e.name === 'zombie')[0];
    if (c) { const d = c.position.distanceTo(bot.entity.position); best = Math.min(best, d); path.push(`${c.position.x.toFixed(1)},${c.position.z.toFixed(1)}`); if (d < 2.0) break; }
  }
  console.log('RESULT C 좀비 최소 거리', best.toFixed(2), '(기대: 2 미만 — 울타리 동쪽 끝을 돌아옴) 경로', path.join(' '));
  await chat(bot, '/gamemode creative', 200);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
