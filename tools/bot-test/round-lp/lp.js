// LuckPerms(실제 jar) 접속 시험: 로그인 때 권한 주입, 콘솔·RCON 으로 권한 바꾸기, Bukkit 명령 권한 판정, 퇴장 때 주입 해제.
// 준비: plugins/ 에 LuckPerms-Bukkit-5.5.91.jar(+ plugins/LuckPerms/libs 미리 채움), server.properties 에 enable-rcon=true rcon.password=testpw
const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('../lib');
const rcon = require('../rcon');
const fs = require('fs');
const SRV = process.env.SRV;
const log = () => fs.readFileSync(SRV + '/server.log', 'utf8');
const results = [];
function check(name, ok, detail) { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`); }

(async () => {
  const before = log().length;
  const bot = makeBot('Alice');
  await spawned(bot);
  await sleep(1500);
  const joinLog = log().slice(before);
  check('1 접속 성공(LuckPerms 가 로그인을 막지 않음)', !/denying login|LOADING_STATE_ERROR/.test(joinLog));
  check('2 다른 권한 객체 경고 없음(PermissibleBase 정확한 클래스)', !/already has a custom permissible|non-LuckPerms permissible/.test(joinLog));
  check('3 접속 중 예외 없음', !/Exception/.test(joinLog), (joinLog.match(/.*Exception.*/) || [''])[0]);

  let t = Date.now();
  await chat(bot, '/lp info', 1500);
  const denied = bot.msgs.filter(x => x.t >= t).map(x => x.m).join(' | ');
  check('4 권한 없을 때 /lp info 는 거부(버전 한 줄만)', !/Platform:|Storage:/i.test(denied), denied);

  cmd('lp user Alice permission set luckperms.info true');
  await sleep(2000);
  t = Date.now();
  await chat(bot, '/lp info', 2000);
  const allowed = bot.msgs.filter(x => x.t >= t).map(x => x.m).join(' | ');
  check('5 콘솔로 luckperms.info 를 준 뒤 /lp info 실행', /LuckPerms|Platform|Storage/i.test(allowed) && !/do not have permission/i.test(allowed), allowed.slice(0, 200));

  const r = await rcon.connect('testpw');
  // LuckPerms 는 명령을 자기 스레드에서 비동기로 돌리고 콘솔·RCON 발신자에게 보낼 말은 서버 콘솔로 낸다 — RCON 응답은 비고 결과는 server.log 에(Paper 도 같다)
  const before6 = log().length;
  const out = await rcon.command(r, 'lp user Alice permission check luckperms.info');
  await sleep(2000);
  check('6 RCON 으로 LuckPerms 명령 실행(결과는 콘솔 로그)', /Permission check for luckperms\.info/.test(log().slice(before6)), JSON.stringify(out.slice(0, 200)));
  const out2 = await rcon.command(r, 'list');
  check('7 RCON 바닐라 명령 응답 그대로', /There are 1 of a max of/.test(out2), JSON.stringify(out2));
  r.s.end();

  cmd('lp user Alice permission set luckperms.info false');
  await sleep(2000);
  t = Date.now();
  await chat(bot, '/lp info', 1500);
  const again = bot.msgs.filter(x => x.t >= t).map(x => x.m).join(' | ');
  check('8 권한을 다시 뺀 뒤 /lp info 거부(버전 한 줄만)', !/Platform:|Storage:/i.test(again), again);

  const before2 = log().length;
  bot.quit();
  await sleep(2500);
  const quitLog = log().slice(before2);
  check('9 퇴장 때 주입 해제 예외 없음', !/Exception|Exception thrown when unloading/.test(quitLog), quitLog.slice(0, 300));
  console.log(`결과: ${results.filter(x => x.ok).length}/${results.length}`);
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
