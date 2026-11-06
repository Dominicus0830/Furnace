// 네더 포탈 무작위 틱 → 좀비 피글린 생성: 난이도·doMobSpawning·차원·바닥 조건.
const U = require('../round2/u.js');
const { cmd, sleep } = U;
const P = [10, -60, 10];   // 첫 틀 왼쪽 아래(안쪽 x 10..11, y -59..-57)
const N = 10;              // 틀 10개를 x 방향으로 5칸 간격(확률 시험이라 많이 세운다)
function build(ox, oy, oz) {
  for (let i = 0; i < N; i++) {
    const x = ox + i * 5;
    cmd(`fill ${x - 1} ${oy} ${oz} ${x + 2} ${oy + 4} ${oz} obsidian`);
    cmd(`fill ${x} ${oy + 1} ${oz} ${x + 1} ${oy + 3} ${oz} nether_portal[axis=x]`);
  }
}
const setBase = (b) => { for (let i = 0; i < N; i++) cmd(`setblock ${P[0] + i * 5} ${P[1]} ${P[2]} ${b}`); };
async function count(bot) {
  const m = await U.ask(bot, '/execute if entity @e[type=zombified_piglin]', /Test passed|Test failed/i);
  const x = m && m.match(/count: (\d+)/); return x ? +x[1] : (m && /passed/.test(m) ? 1 : 0);
}
async function run(bot, label, setup, ms = 15000) {
  cmd('kill @e[type=!player]'); await sleep(500);
  setup();
  await sleep(1500);
  cmd('gamerule randomTickSpeed 4096'); await sleep(ms); cmd('gamerule randomTickSpeed 3');
  const n = await count(bot);
  console.log(label, '→ 좀비 피글린', n);
  return n;
}
(async () => {
  const bot = await U.setup('Tester');
  cmd('gamemode creative Tester'); cmd('gamerule naturalRegeneration false'); cmd('gamerule doMobSpawning true');
  cmd('fill 0 -60 0 70 -50 30 air'); bot.chat('/tp 5.5 -60 5.5'); await sleep(1500);
  const res = {};
  build(...P);
  res.easy = await run(bot, 'easy', () => { cmd('difficulty easy'); cmd('gamerule doMobSpawning true'); });
  const m = await U.ask(bot, '/data get entity @e[type=zombified_piglin,limit=1] Pos', /entity data|has the following|No entity|Found no/i);
  console.log('Pos', m);
  console.log('Hand', await U.ask(bot, '/data get entity @e[type=zombified_piglin,limit=1] HandItems', /entity data|has the following|No entity|Found no/i));
  console.log('PortalCooldown', await U.ask(bot, '/data get entity @e[type=zombified_piglin,limit=1] PortalCooldown', /entity data|has the following|No entity|Found no/i));
  res.peaceful = await run(bot, 'peaceful', () => cmd('difficulty peaceful'));
  res.noSpawnRule = await run(bot, 'doMobSpawning=false', () => { cmd('difficulty hard'); cmd('gamerule doMobSpawning false'); });
  res.hard = await run(bot, 'hard', () => { cmd('kill @e[type=!player]'); build(...P); cmd('difficulty hard'); cmd('gamerule doMobSpawning true'); });
  // 포탈 아래가 흑요석이 아니라 땅(포탈이 땅 위에 서 있을 때와 같음)·네더 차원
  cmd('execute in minecraft:the_nether run fill 0 100 0 30 110 30 air');
  cmd('execute in minecraft:the_nether run fill 9 100 9 14 105 11 air');
  cmd('kill @e[type=!player]');
  cmd('execute in minecraft:the_nether run fill 9 100 10 12 104 10 obsidian');
  cmd('execute in minecraft:the_nether run fill 10 101 10 11 103 10 nether_portal[axis=x]');
  cmd('gamerule randomTickSpeed 4096'); await sleep(15000); cmd('gamerule randomTickSpeed 3');
  const nn = await U.ask(bot, '/execute in minecraft:the_nether if entity @e[type=zombified_piglin]', /Test passed|Test failed/i);
  console.log('네더 차원 →', nn);
  console.log(JSON.stringify(res));
  process.exit(0);
})();
