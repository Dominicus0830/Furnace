// M9 수면·리스폰 봇 시험: (1) 자는 중 접속 종료 → 다시 접속하면 침대 위가 아니라 일어선 자리, (2) 자는 중 /tp 는 깨우고 목적지로,
// (3) 네더에서 /setworldspawn 거부, (4) 오버월드 /setworldspawn 이 네더 도착 시 스폰 패킷에 반영.
// 사용: SRV=<서버 폴더> node round-m9/sleep.js   (서버는 평지·오프라인, 난이도 peaceful 권장)
const { makeBot, spawned, waitMsg, cmd, sleep } = require('../lib');
const fs = require('fs');
let pass = 0, fail = 0;
const ok = (c, name, extra = '') => { (c ? pass++ : fail++); console.log(`${c ? 'PASS' : 'FAIL'} ${name} ${extra}`); };
const logTail = () => fs.readFileSync(process.env.SRV + '/server.log', 'utf8');

(async () => {
  cmd('difficulty peaceful'); cmd('gamerule doDaylightCycle false'); cmd('time set 18000');
  await sleep(500);
  let bot = makeBot('Sleeper');
  await spawned(bot); await sleep(2500);
  cmd('op Sleeper'); await sleep(300);
  // 시험장은 스폰에서 멀지 않은 평지. 침대(머리 +z), 사방이 비어 있다.
  const base = bot.entity.position.floored();
  const fx = base.x + 2, fy = base.y, fz = base.z;
  cmd(`setblock ${fx} ${fy} ${fz} minecraft:red_bed[facing=south,part=foot]`);
  cmd(`setblock ${fx} ${fy} ${fz + 1} minecraft:red_bed[facing=south,part=head]`);
  await sleep(1200);
  const head = bot.blockAt(bot.entity.position.offset(2, 0, 1).floored());
  console.log('bed block', head && head.name, 'at', head && head.position);
  const bedBlock = bot.findBlock({ matching: b => b.name === 'red_bed', maxDistance: 6 });
  ok(!!bedBlock, 'bed placed');
  try { await bot.sleep(bedBlock); } catch (e) { console.log('sleep err', e.message); }
  await sleep(1500);
  ok(bot.isSleeping, 'bot is sleeping');
  const bedPos = bot.entity.position.clone();
  console.log('sleeping position', bedPos);
  // (2) 자는 중 /tp
  cmd(`tp Sleeper ${fx + 12} ${fy} ${fz + 12}`);
  await sleep(2500);
  ok(!bot.isSleeping, 'tp woke the sleeper');
  const p2 = bot.entity.position;
  ok(Math.abs(p2.x - (fx + 12)) < 1.5 && Math.abs(p2.z - (fz + 12)) < 1.5, 'arrived at tp target (not overwritten by wake correction)', p2.toString());
  const headState = bot.blockAt(bedBlock.position.offset(0, 0, 0));
  // 다시 자기: 가까이 와서 잔다
  cmd(`tp Sleeper ${fx} ${fy} ${fz - 1}`);
  await sleep(2000);
  const bed2 = bot.findBlock({ matching: b => b.name === 'red_bed', maxDistance: 6 });
  try { await bot.sleep(bed2); } catch (e) { console.log('sleep err 2', e.message); }
  await sleep(1500);
  ok(bot.isSleeping, 'sleeping again');
  const bedCenter = bot.entity.position.clone();
  // (1) 자는 중 접속 종료
  bot.quit();
  await sleep(3000);
  bot = makeBot('Sleeper');
  let first;
  bot._client.once('position', p => { first = { x: p.x, y: p.y, z: p.z }; });
  await spawned(bot); await sleep(2000);
  console.log('rejoin position', first, 'bed position was', bedCenter);
  const dist = Math.hypot(first.x - bedCenter.x, first.z - bedCenter.z);
  ok(dist > 0.9, 'rejoin position is the stand-up spot beside the bed (not bed centre)', `dist=${dist.toFixed(2)} y=${first.y}`);
  ok(Math.abs(first.y - (fy)) < 0.5 || first.y >= fy, 'stand-up y at floor level', String(first.y));
  // (3)(4) /setworldspawn
  cmd('op Sleeper'); await sleep(300);
  const before = logTail().length;
  cmd('execute in minecraft:the_nether run setworldspawn 5 70 5');
  await sleep(800);
  ok(/Can only set the world spawn for overworld/.test(logTail().slice(before)), 'setworldspawn refused outside overworld');
  cmd('setworldspawn 33 70 44');
  await sleep(500);
  let spawnPkt = null;
  bot._client.on('spawn_position', p => { spawnPkt = p; });
  cmd('execute in minecraft:the_nether run tp Sleeper 0 70 0');
  await sleep(8000);
  console.log('spawn_position after nether', JSON.stringify(spawnPkt));
  const loc = spawnPkt && (spawnPkt.location || spawnPkt);
  ok(!!loc && (loc.x === 33 || (loc.x === undefined && JSON.stringify(spawnPkt).includes('33'))), 'nether arrival sends overworld shared spawn');
  console.log(`\nRESULT ${pass} pass / ${fail} fail`);
  bot.quit();
  await sleep(500);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error('SCRIPT ERROR', e); process.exit(2); });
