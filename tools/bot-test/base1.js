// 기존 user-todo 항목: RCON/Query, 관측 명령, 관전 텔레포트, 난이도·태그 질의(op), 유휴 킥
const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const rcon = require('./rcon'), query = require('./query');
async function ask(bot, c, re = /./, ms = 3000) { const t = Date.now(); bot.chat(c); return await waitMsg(bot, re, ms, t); }
(async () => {
  const c = await rcon.connect('testpw'); console.log('RESULT RCON 인증', c.authed, 'list →', JSON.stringify(await rcon.command(c, 'list'))); c.s.destroy();
  const bad = await rcon.connect('wrong'); console.log('RESULT RCON 틀린 비번 인증', bad.authed); bad.s.destroy();
  console.log('RESULT Query basic', JSON.stringify(await query()));
  const op = makeBot('Admin'), user = makeBot('Guest');
  await spawned(op); await spawned(user); await sleep(1500);
  cmd('op Admin'); await sleep(800);
  for (const c2 of ['/furnace threads', '/furnace regions', '/furnace profile']) { const t = Date.now(); op.chat(c2); await sleep(1500); console.log('RESULT', c2, JSON.stringify(op.msgs.filter(m => m.t >= t).slice(0, 3).map(m => m.m))); }
  // 난이도: 비op → 무시, op → 변경
  await chat(op, '/difficulty easy', 500);
  user._client.write('set_difficulty', { newDifficulty: 'hard' }); await sleep(800);
  console.log('RESULT 비op 난이도 패킷 뒤', await ask(op, '/difficulty', /difficulty/i));
  op._client.write('set_difficulty', { newDifficulty: 'hard' }); await sleep(800);
  console.log('RESULT op 난이도 패킷 뒤', await ask(op, '/difficulty', /difficulty/i));
  // F3+I 류: 엔티티·블록 NBT 질의
  const nbtResp = { Admin: 0, Guest: 0 };
  op._client.on('nbt_query_response', () => nbtResp.Admin++); user._client.on('nbt_query_response', () => nbtResp.Guest++);
  await chat(op, '/summon minecraft:cow 2 -60 2 {NoAI:1b}', 800);
  const cow = Object.values(op.entities).find(e => e.name === 'cow');
  for (const b of [op, user]) { b._client.write('query_entity_nbt', { transactionId: 7, entityId: cow.id }); b._client.write('query_block_nbt', { transactionId: 8, location: { x: 0, y: -61, z: 0 } }); }
  await sleep(1000); console.log('RESULT NBT 질의 응답 수', JSON.stringify(nbtResp));
  // 관전 텔레포트
  await chat(op, '/tp Guest 30 -60 30', 500);
  await chat(op, '/gamemode spectator', 800);
  op._client.write('spectate', { target: user.player.uuid }); await sleep(1500);
  console.log('RESULT 관전 텔레포트 후 Admin 위치', op.entity.position, 'Guest 위치', user.entity.position);
  await chat(op, '/gamemode creative', 300);
  op.quit(); user.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
