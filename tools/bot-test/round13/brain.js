// E2 봇 시험 — round13/probe/BrainProbe 를 넣은 서버에서. "RESULT 이름 PASS|FAIL 근거". SRV=<서버 폴더> node round13/brain.js
const fs = require('fs');
const { makeBot, spawned, sleep, chat, cmd } = require('../lib');
const SRV = process.env.SRV;
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
const log = () => fs.readFileSync(SRV + '/server.log').toString('utf8').split('\n').filter(l => l.includes('[BR] ')).map(l => l.slice(l.indexOf('[BR] ') + 5));
const last = p => log().filter(l => l.startsWith(p)).pop() ?? '';

(async () => {
  const bot = makeBot('Tester', { plugins: { resource_pack: false } });
  await spawned(bot); await sleep(1500);
  cmd('op Tester'); cmd('difficulty easy'); await sleep(800);
  const at = bot.entity.position.floored();
  await chat(bot, `/summon minecraft:villager ${at.x + 3} ${at.y} ${at.z}`, 600);
  await chat(bot, `/summon minecraft:piglin ${at.x - 3} ${at.y} ${at.z} {IsImmuneToZombification:1b}`, 600);
  await chat(bot, `/summon minecraft:cow ${at.x} ${at.y} ${at.z + 3}`, 600);
  await chat(bot, `/summon minecraft:axolotl ${at.x} ${at.y} ${at.z - 3}`, 600);
  await sleep(500);

  await chat(bot, '/br info villager', 700);
  result('주민 두뇌 손잡이·활동 목록', /info activities=.*minecraft:core.*/.test(log().join('\n')) && log().includes('info usesBrain=true brain=true')
    && /minecraft:work|minecraft:rest/.test(last('info activities')), last('info activities'));
  await chat(bot, '/br info cow', 700);
  result('소는 두뇌 없음', log().includes('info usesBrain=false brain=false'), JSON.stringify(log().slice(-2)));
  await chat(bot, '/br info axolotl', 700);
  result('아홀로틀 두뇌', /minecraft:play_dead/.test(last('info activities')), last('info activities'));

  await chat(bot, '/br home villager', 700);
  result('집 기억 쓰기·읽기', /^home set=true read=GlobalBlockPos\[dimension=minecraft:overworld, pos=BlockPos\[x=1, y=2, z=3\]\]/.test(last('home')), last('home'));
  bot.msgs.length = 0;
  await chat(bot, '/data get entity @e[type=minecraft:villager,limit=1,sort=nearest] Brain.memories."minecraft:home".value.pos[2]', 800);
  result('집 기억이 바닐라 저장 형식으로(/data)', bot.msgs.some(m => /: 3$/.test(m.m)), JSON.stringify(bot.msgs.map(m => m.m)));

  await chat(bot, '/br force villager brprobe:guard', 700);
  await sleep(4000);
  await chat(bot, '/br active villager', 700);
  result('붙잡은 플러그인 활동이 80틱 뒤에도 유지', last('active') === 'active [minecraft:core, brprobe:guard] forced=brprobe:guard', last('active'));
  await chat(bot, '/br behave villager', 700);
  await sleep(2500);
  await chat(bot, '/br count villager', 700);
  const m = /count ticks=(\d+) seen=(\w+)/.exec(last('count'));
  result('플러그인 행동이 매 틱 돈다(핵심 활동)', !!m && +m[1] >= 30, last('count'));
  result('플러그인 센서가 플러그인 기억을 채운다', !!m && m[2] === 'true', last('count'));
  await chat(bot, '/br release villager', 700);
  await sleep(1500);
  await chat(bot, '/br active villager', 700);
  result('놓으면 몹이 스스로 활동을 고른다', /forced=none/.test(last('active')) && !/brprobe:guard/.test(last('active')), last('active'));

  await chat(bot, '/br force piglin minecraft:avoid', 700);
  await sleep(3000);
  await chat(bot, '/br active piglin', 700);
  result('피글린 피하기 활동 고정(조건 없이)', last('active') === 'active [minecraft:core, minecraft:avoid] forced=minecraft:avoid', last('active'));
  await chat(bot, '/br force piglin minecraft:nope', 700);
  result('모르는 바닐라 활동은 거부', /force err=java.lang.IllegalArgumentException/.test(last('force')), last('force'));
  await chat(bot, '/br release piglin', 600);
  await chat(bot, '/br anger piglin', 700);
  result('피글린 분노·공격 대상(플레이어 UUID)', last('anger') === 'anger angry=true target=true targetApi=true', last('anger'));

  const fails = results.filter(r => !r[1]).length;
  console.log(`SUMMARY ${results.length - fails}/${results.length} PASS`);
  bot.end(); process.exit(fails ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
