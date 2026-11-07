// 다중 월드(W1) 봇 시험. 단계: 1 = 만들기·들어가기·블록·내리기 거절/성공·다시 불러오기·네더 환경, 2 = (worlds.autoload=mining 로 재시작 뒤) 저장된 차원으로 접속
// 사용: SRV=<서버 폴더> node round-w1/worlds.js 1|2
const { makeBot, spawned, waitMsg, cmd, sleep } = require('../lib');
const fs = require('fs');
const results = [];
function check(name, ok, detail = '') { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name} ${detail}`); }
function serverLog() { return fs.readFileSync(process.env.SRV + '/server.log', 'utf8'); }
async function waitLog(re, ms = 60000) {
  const end = Date.now() + ms;
  while (Date.now() < end) { if (re.test(serverLog())) return true; await sleep(250); }
  return false;
}

async function main(stage) {
  const bot = makeBot('Tester');
  const respawns = [];
  let login = null;
  bot._client.on('login', p => { login = p; });
  const wname = p => (p.worldState && p.worldState.name) || p.worldName;
  bot._client.on('respawn', p => respawns.push(wname(p)));
  await spawned(bot);
  cmd('op Tester');
  await sleep(1000);
  if (stage === '2') {
    check('autoload 로 저장된 차원(mining)으로 접속', login && wname(login) === 'minecraft:mining', login && wname(login));
    check('접속 차원 목록에 mining', login && login.worldNames.includes('minecraft:mining'), JSON.stringify(login && login.worldNames));
    const V = bot.entity.position.constructor;
    let b = null;
    for (let i = 0; i < 30 && !(b && b.name === 'gold_block'); i++) { await sleep(500); b = bot.blockAt(new V(0, 199, 0)); }
    check('mining 의 금 블록이 보인다(그 월드 청크를 받음)', b && b.name === 'gold_block', b && b.name);
    bot.quit(); await sleep(500); return;
  }
  check('처음 접속 차원 목록은 기본 셋', login && login.worldNames.length === 3, JSON.stringify(login && login.worldNames));

  // 1) 만들기(노이즈 오버월드)
  bot.chat('/worlds create mining');
  check('만들기 알림', !!await waitMsg(bot, /월드 'mining' 올림/, 90000));
  check('서버 로그', /추가 월드 'mining' 올림 — 키 minecraft:mining/.test(serverLog()));
  check('폴더 mining/level.dat', fs.existsSync(process.env.SRV + '/mining/level.dat'));

  // 2) 들어가기
  bot.chat('/gamemode creative');
  bot.chat('/execute in minecraft:mining run tp @s 0 200 0');
  for (let i = 0; i < 60 && !respawns.includes('minecraft:mining'); i++) await sleep(500);   // 첫 노이즈 생성은 몇 초 걸린다
  await sleep(1500);
  check('Respawn 패킷 차원 = minecraft:mining', respawns.includes('minecraft:mining'), JSON.stringify(respawns));
  bot.chat('/setblock 0 199 0 minecraft:gold_block');
  await sleep(1500);
  const V = bot.entity.position.constructor;
  const gold = bot.blockAt(new V(0, 199, 0));
  check('새 월드에서 블록 놓기', gold && gold.name === 'gold_block', gold && gold.name);

  // 3) 안에 있는 동안 내리기 거절
  bot.chat('/worlds unload mining');
  check('플레이어가 있으면 내리기 거절', !!await waitMsg(bot, /내리지 않았다/, 20000));

  // 4) 나와서 내리기 → 그 차원 인자는 없는 차원
  bot.chat('/execute in minecraft:overworld run tp @s 0 -50 0');
  await sleep(2500);
  check('오버월드로 돌아옴', respawns[respawns.length - 1] === 'minecraft:overworld', JSON.stringify(respawns));
  const since = Date.now();
  bot.chat('/worlds unload mining');
  check('내리기 성공', !!await waitMsg(bot, /월드 'mining' 내림/, 60000, since));
  check('region 파일 저장', fs.existsSync(process.env.SRV + '/mining/region/r.0.0.mca'));
  const s2 = Date.now();
  bot.chat('/execute in minecraft:mining run say hi');
  check('내린 차원은 /execute in 에서 없는 차원', !!await waitMsg(bot, /dimension|차원|Unknown/i, 5000, s2));

  // 5) 다시 불러오기 → 블록이 남아 있다
  bot.chat('/worlds load mining');
  check('다시 불러오기', !!await waitMsg(bot, /월드 'mining' 불러옴/, 90000));
  bot.chat('/execute in minecraft:mining run tp @s 0.5 200 0.5');
  await sleep(3000);
  const gold2 = bot.blockAt(new V(0, 199, 0));
  check('내렸다 올린 뒤에도 금 블록', gold2 && gold2.name === 'gold_block', gold2 && gold2.name);

  // 6) 네더 환경 빈 월드: 폴더 DIM-1, 네더 타입 규칙(침대 폭발 대신 서버 판정은 단위 시험) — 들어가기만
  bot.chat('/worlds create deep nether void 42');
  check('네더 환경 빈 월드 만들기', !!await waitMsg(bot, /월드 'deep' 올림/, 60000));
  bot.chat('/execute in minecraft:deep run tp @s 0 64 0');
  await sleep(2500);
  check('deep 으로 들어감', respawns[respawns.length - 1] === 'minecraft:deep', JSON.stringify(respawns));
  bot.chat('/worlds list');
  check('목록에 시드 42', !!await waitMsg(bot, /minecraft:deep deep — 타입 minecraft:the_nether.*시드 42/, 5000));

  // 다음 단계: mining 안에서 나간다(재시작 뒤 autoload 로 그 차원에 다시 들어오는지)
  bot.chat('/execute in minecraft:mining run tp @s 0.5 200 0.5');
  await sleep(2500);
  bot.quit();
  await sleep(1000);
}

main(process.argv[2] || '1').then(() => {
  const fail = results.filter(r => !r.ok).length;
  console.log(`결과: ${results.length - fail}/${results.length}`);
  process.exit(fail ? 1 : 0);
}).catch(e => { console.error(e); process.exit(2); });
