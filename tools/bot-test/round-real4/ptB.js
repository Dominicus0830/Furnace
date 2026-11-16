// 재시작 후 2차: 영속성(LuckPerms)·청정 위치 이동·ProtocolLib 명령·FAWE 재동작
const R = __dirname + '/../';
const { makeBot, spawned, sleep } = require(R + 'lib');
const rcon = require(R + 'rcon');
const fs = require('fs');
const SRV = process.env.SRV;
const log = () => fs.readFileSync(SRV + '/server.log', 'utf8');
const bad = s => s.split('\n').filter(l => /ERROR|WARN|Exception|Error:/.test(l)).map(l => l.slice(0, 260)).join('\n');
const results = [];
function check(name, ok, detail) { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`); }
(async () => {
  const r = await rcon.connect('testpw');
  const rc = async c => (await rcon.command(r, c)).trim();
  const l0 = log().length;
  const bot = makeBot('Tester');
  await spawned(bot, 60000); await sleep(2500);
  const out = async (line, wait = 1800) => { const t = Date.now(); bot.chat(line); await sleep(wait); return bot.msgs.filter(x => x.t >= t).map(x => x.m).join(' | '); };
  check('재시작 후 접속', !!bot.entity);
  console.log('--- 접속 로그 ---\n' + bad(log().slice(l0)));
  const lpi = await out('/lp user Tester parent info');
  check('LuckPerms 데이터 영속(vip 부모 그룹 남음, 관리자 권한은 *)', /vip/.test(lpi), lpi.slice(0, 200));
  await rc('gamemode survival Tester'); await rc('tp Tester 200 -60 200'); await sleep(3000);
  const p0 = bot.entity.position.clone();
  bot.setControlState('forward', true); await sleep(2000); bot.setControlState('forward', false);
  check('봇 이동(전진, 빈 평지)', p0.distanceTo(bot.entity.position) > 2, p0.distanceTo(bot.entity.position).toFixed(2) + '블록');
  bot.setControlState('jump', true); await sleep(300); bot.setControlState('jump', false); await sleep(800);
  for (const c of ['/protocol version', '/protocol check', '/protocol listeners', '/protocol timings', '/packet names server 0-3', '/fawe', '//limit', '//gmask', '//history', '/lp group vip info', '/lp verbose on', '/lp verbose off']) {
    const l1 = log().length; const o = await out(c, 2000);
    console.log(`>>> ${c}\n   chat: ${o.slice(0, 500)}\n   log: ${bad(log().slice(l1)).slice(0, 500)}`);
  }
  await rc('gamemode creative Tester'); await sleep(500);
  console.log('//pos1', await out('//pos1')); await rc('tp Tester 210 -60 210'); await sleep(1200);
  console.log('//pos2', await out('//pos2')); console.log('//set', await out('//set oak_planks', 3500));
  const Vec3 = require(R + 'node_modules/vec3').Vec3;
  const b = bot.blockAt(new Vec3(205, -60, 205)); check('재시작 후 FAWE //set', b && b.name === 'oak_planks', b && b.name);
  const lq = log().length; bot.quit(); await sleep(2000);
  check('퇴장 예외 없음', !/Exception/.test(log().slice(lq)));
  console.log('--- 전체 2차 로그 경고/오류 ---\n' + bad(log().slice(l0)));
  console.log(`결과: ${results.filter(x => x.ok).length}/${results.length}`);
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
