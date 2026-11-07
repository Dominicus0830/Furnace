// E3 봇 시험 — round15/probe/E3Probe 를 넣은 서버에서. "RESULT 이름 PASS|FAIL 근거". SRV=<서버 폴더> node round15/e3.js
// MODE=main(기본): 엔티티 수·자세·noPhysics·평판·POI·정책 상한·필터.  MODE=npcs: spawn-animals=false spawn-npcs=false 서버에서 소·주민 vs 좀비.
const fs = require('fs');
const { makeBot, spawned, sleep, chat, cmd } = require('../lib');
const SRV = process.env.SRV;
const MODE = process.env.MODE || 'main';
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
const log = () => fs.readFileSync(SRV + '/server.log').toString('utf8').split('\n').filter(l => l.includes('[E3] ')).map(l => l.slice(l.indexOf('[E3] ') + 5));
const last = p => log().filter(l => l.startsWith(p)).pop() ?? '';
const num = (s, k) => { const m = new RegExp(k + '=(\\d+)').exec(s); return m ? +m[1] : -1; };

(async () => {
  const bot = makeBot('Tester', { plugins: { resource_pack: false } });
  await spawned(bot); await sleep(1500);
  cmd('op Tester'); cmd('difficulty easy'); await sleep(800);
  const at = bot.entity.position.floored();

  if (MODE === 'npcs') {
    await chat(bot, `/summon minecraft:villager ${at.x + 3} ${at.y} ${at.z}`, 500);
    await chat(bot, `/summon minecraft:cow ${at.x} ${at.y} ${at.z + 3}`, 500);
    await chat(bot, `/summon minecraft:zombie ${at.x - 3} ${at.y} ${at.z} {NoAI:1b,PersistenceRequired:1b}`, 500);
    await sleep(3000);
    await chat(bot, '/e3 counts', 800);
    const w = last('world');
    // 바닐라 ServerLevel.shouldDiscardEntity: spawn-animals=false 면 Animal·WaterAnimal, spawn-npcs=false 면 Npc(주민)를 지운다. 좀비는 그대로.
    result('spawn-animals/npcs=false: 소·주민은 지워지고 좀비는 남음', !/minecraft:villager/.test(w) && !/minecraft:cow/.test(w) && /minecraft:zombie=1/.test(w), w);
    const fails = results.filter(r => !r[1]).length;
    console.log(`SUMMARY ${results.length - fails}/${results.length} PASS`);
    bot.end(); process.exit(fails ? 1 : 0);
  }

  // 엔티티 수
  await chat(bot, `/summon minecraft:cow ${at.x + 2} ${at.y} ${at.z}`, 500);
  await chat(bot, `/summon minecraft:cow ${at.x + 2} ${at.y} ${at.z + 1}`, 500);
  await chat(bot, `/summon minecraft:zombie ${at.x - 2} ${at.y} ${at.z} {NoAI:1b,PersistenceRequired:1b}`, 500);
  await chat(bot, `/summon minecraft:villager ${at.x} ${at.y} ${at.z + 3}`, 500);
  await sleep(500);
  await chat(bot, '/e3 counts', 800);
  const c = last('chunk'), w = last('world');
  result('청크별 종류별 수(소2·좀비1·주민1·플레이어1)', /minecraft:cow=2/.test(c) && /minecraft:zombie=1/.test(c) && /minecraft:villager=1/.test(c) && /minecraft:player=1/.test(c), c);
  result('월드 합계에도 같은 종류가 있다', /minecraft:cow=2/.test(w) && num(w, 'monster') >= 1, w);

  // 자세
  await chat(bot, '/e3 pose cow sleeping', 700);
  await sleep(400);
  result('엔티티 자세 설정', /pose cow sleeping ok=true/.test(last('pose')), last('pose'));
  await chat(bot, '/e3 ppose crouching', 700);
  await sleep(300);
  result('플레이어 자세 덮어쓰기', /ppose crouching ok=true now=CROUCHING/.test(last('ppose')), last('ppose'));
  await chat(bot, '/e3 ppose none', 700);
  result('플레이어 자세 덮어쓰기 해제', /ppose none ok=true now=STANDING/.test(last('ppose')), last('ppose'));

  // noPhysics: 공중의 좀비가 안 떨어짐
  await chat(bot, `/summon minecraft:zombie ${at.x} ${at.y + 10} ${at.z - 3} {NoAI:1b,PersistenceRequired:1b,Tags:["np"]}`, 500);
  await sleep(300);
  await chat(bot, '/e3 nophys zombie true', 700);
  result('noPhysics 설정', /nophys zombie true ok=true/.test(last('nophys')), last('nophys'));

  // 평판
  await chat(bot, '/e3 gossip', 900);
  const g = last('gossip');
  result('주민 평판·소문(거래 사건 반영)', /gossip rep=\d+/.test(g) && /MAJOR_POSITIVE/.test(g) && num(g, 'rep') > 0, g);

  // POI: 종 — 마을 POI
  await chat(bot, `/setblock ${at.x + 4} ${at.y} ${at.z + 4} minecraft:bell`, 500);
  await chat(bot, `/setblock ${at.x + 5} ${at.y} ${at.z + 5} minecraft:composter`, 500);
  await sleep(500);
  await chat(bot, '/e3 poi', 900);
  const poi = log().filter(l => l.startsWith('poi')).pop() ?? '';
  result('POI 목록(종·퇴비통)', /minecraft:meeting/.test(poi) && /minecraft:farmer/.test(poi), poi);
  await chat(bot, '/e3 raids', 700);
  result('습격 목록(없음)', last('raids') === 'raids n=0', last('raids'));

  // 정책 상한
  await chat(bot, '/e3 cap monster 0', 700);
  result('카테고리 상한 설정', /cap MONSTER=0 vanilla=70/.test(last('cap')), last('cap'));
  await chat(bot, '/gamerule doMobSpawning true', 500);
  await chat(bot, '/e3 counts', 800);
  const m0 = num(last('world'), 'monster');
  await sleep(10000);
  await chat(bot, '/e3 counts', 800);
  const m1 = num(last('world'), 'monster');
  result('몬스터 상한 0 이면 몬스터 수가 늘지 않는다', m0 >= 0 && m1 <= m0, `before=${m0} after=${m1}`);
  await chat(bot, '/e3 capreset monster', 700);
  result('상한 해제하면 바닐라 값', /capreset MONSTER=70/.test(last('capreset')), last('capreset'));
  await sleep(10000);
  await chat(bot, '/e3 counts', 800);
  result('상한을 풀면 몬스터가 다시 늘어난다', num(last('world'), 'monster') > m1, `now=${num(last('world'), 'monster')} was=${m1}`);

  await chat(bot, '/e3 filter minecraft:zombie', 700);
  result('필터 등록', /filter only=minecraft:zombie count=1/.test(last('filter')), last('filter'));
  await chat(bot, '/e3 nofilter', 700);
  result('필터 해제', /nofilter count=0/.test(last('nofilter')), last('nofilter'));

  const fails = results.filter(r => !r[1]).length;
  console.log(`SUMMARY ${results.length - fails}/${results.length} PASS`);
  bot.end(); process.exit(fails ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
