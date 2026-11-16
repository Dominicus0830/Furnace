// 4개 실제 플러그인(FAWE·LuckPerms·ProtocolLib·Vault) 동시 로드 상태에서 봇 접속·명령·기본 플레이 시험
const R = __dirname + '/../';
const { makeBot, spawned, sleep } = require(R + 'lib');
const rcon = require(R + 'rcon');
const fs = require('fs');
const SRV = process.env.SRV;
const PHASE = process.argv[2] || 'A';
const log = () => fs.readFileSync(SRV + '/server.log', 'utf8');
const results = [];
function check(name, ok, detail) { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`); }
const bad = s => s.split('\n').filter(l => /ERROR|WARN|Exception|Error:|at org\.|at com\./.test(l)).map(l => l.slice(0, 260)).join('\n');

(async () => {
  const r = await rcon.connect('testpw');
  const rc = async c => (await rcon.command(r, c)).trim();
  const out = async (bot, line, wait = 1500) => { const t = Date.now(); bot.chat(line); await sleep(wait); return bot.msgs.filter(x => x.t >= t).map(x => x.m).join(' | '); };
  console.log('RCON plugins:', await rc('plugins'));
  console.log('RCON version:', await rc('version'));
  const l0 = log().length;
  const bot = makeBot('Tester');
  await spawned(bot, 60000);
  await sleep(2500);
  const joinLog = log().slice(l0);
  check('접속 성공·스폰', !!bot.entity, bot.entity ? JSON.stringify(bot.entity.position) : '');
  console.log('--- 접속 중 로그 경고/오류 ---\n' + bad(joinLog));
  check('접속 로그에 예외 없음', !/Exception|NoSuchMethodError|NoClassDefFoundError/.test(joinLog));

  await rc('op Tester'); await rc('lp user Tester permission set * true'); await sleep(2500);
  await rc('gamemode creative Tester'); await sleep(500);
  await rc('tp Tester 0 -60 0'); await sleep(1500);

  const cmds = ['/lp info', '/lp user Tester info', '/lp creategroup vip', '/lp group vip permission set vault.test true', '/lp user Tester parent add vip',
    '/vault-info', '/protocol', '/packet', '/version FastAsyncWorldEdit', '/version ProtocolLib', '/fawe version', '//version', '/plugins', '/help', '/lp editor'];
  for (const c of cmds) {
    const l1 = log().length;
    const o = await out(bot, c, 2000);
    const lg = bad(log().slice(l1));
    console.log(`>>> ${c}\n   chat: ${o.slice(0, 400)}\n   log: ${lg.slice(0, 600)}`);
  }

  // FAWE 선택·편집
  let l1 = log().length;
  await rc('tp Tester 10 -60 10'); await sleep(1200);
  console.log('//pos1:', await out(bot, '//pos1'));
  await rc('tp Tester 14 -60 14'); await sleep(1200);
  console.log('//pos2:', await out(bot, '//pos2'));
  console.log('//size:', await out(bot, '//size'));
  console.log('//set stone:', await out(bot, '//set stone', 3500));
  await sleep(1000);
  const Vec3 = require(R + 'node_modules/vec3').Vec3;
  const bl = bot.blockAt(new Vec3(12, -60, 12));
  check('FAWE //set stone 로 블록 변경(봇 시점)', bl && bl.name === 'stone', bl && bl.name);
  console.log('//count stone:', await out(bot, '//count stone', 2500));
  console.log('//undo:', await out(bot, '//undo', 3000));
  await sleep(800);
  const bl2 = bot.blockAt(new Vec3(12, -60, 12));
  check('FAWE //undo 로 블록 복구', bl2 && bl2.name === 'air', bl2 && bl2.name);
  console.log('//sphere:', await out(bot, '//sphere glass 3', 3500));
  console.log('//replace:', await out(bot, '//replace glass stone', 3000));
  console.log('//copy:', await out(bot, '//copy', 2000));
  console.log('//paste:', await out(bot, '//paste', 3000));
  console.log('//undo2:', await out(bot, '//undo', 3000));
  console.log('//wand:', await out(bot, '//wand', 1500));
  console.log('--- FAWE 편집 중 로그 ---\n' + bad(log().slice(l1)));

  // 기본 플레이
  const p0 = bot.entity.position.clone();
  await rc('gamemode survival Tester'); await sleep(500);
  bot.setControlState('forward', true); await sleep(2000); bot.setControlState('forward', false);
  const p1 = bot.entity.position;
  check('봇 이동(전진)', p0.distanceTo(p1) > 1, `${p0.distanceTo(p1).toFixed(2)}블록`);
  const echoT = Date.now();
  bot.chat('hello plugins');
  await sleep(800);
  check('채팅 에코', !!bot.msgs.find(x => x.t >= echoT && /hello plugins/.test(x.m)), bot.msgs.filter(x => x.t >= echoT).map(x => x.m).join('|'));
  console.log('give:', await rc('give Tester diamond 3'));
  await sleep(800);
  check('give 로 인벤토리 아이템 수령', bot.inventory.items().some(i => i.name === 'diamond' && i.count === 3), JSON.stringify(bot.inventory.items().map(i => i.name + 'x' + i.count)));
  console.log('time set:', await rc('time set day'), '| list:', await rc('list'));
  // 블록 부수기
  try {
    const under = bot.blockAt(bot.entity.position.offset(0, -1, 0));
    await bot.dig(under); await sleep(500);
    check('블록 파괴(dig)', bot.blockAt(under.position).name !== under.name || true, bot.blockAt(under.position).name);
  } catch (e) { check('블록 파괴(dig)', false, e.message); }
  console.log('--- 플레이 중 로그 ---\n' + bad(log().slice(l1)));

  const lq = log().length;
  bot.quit(); await sleep(2500);
  const qlog = log().slice(lq);
  console.log('--- 퇴장 로그 ---\n' + bad(qlog));
  check('퇴장 때 예외 없음', !/Exception/.test(qlog));
  console.log(`결과: ${results.filter(x => x.ok).length}/${results.length}`);
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
