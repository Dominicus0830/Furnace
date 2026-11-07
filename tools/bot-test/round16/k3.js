// K3 플레이어별 가상 블록·바이옴 층·개인 보더·장비 덮어쓰기·채팅 서식 봇 시험 — round16/probe/K3Probe 를 넣은 서버에서. 결과는 "RESULT 이름 PASS|FAIL 근거" 줄.
// SRV=<서버 폴더> node round16/k3.js
const V = require('vec3');
const L = require('../lib');
const { sleep, cmd } = L;
const { setup, chat } = require('../round2/u');
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
async function until(f, ms = 6000) { const end = Date.now() + ms; while (Date.now() < end) { const v = f(); if (v) return v; await sleep(100); } return f(); }

// chunk_biomes 항목의 데이터(섹션마다 바이옴 컨테이너)에서 섹션 s 의 칸 idx 번호를 푼다
function readVarInt(b, o) { let v = 0, sh = 0, c; do { c = b[o.p++]; v |= (c & 0x7f) << sh; sh += 7; } while (c & 0x80); return v; }
function biomeInPacket(pk, cx, cz, section, idx) {
  const e = pk.biomes.find(x => x.position.x === cx && x.position.z === cz);
  if (!e) return null;
  const b = Buffer.from(e.data); const o = { p: 0 };
  for (let s = 0; ; s++) {
    const bits = b[o.p++]; let pal = null, single = null;
    if (bits === 0) single = readVarInt(b, o); else if (bits <= 3) { pal = []; const n = readVarInt(b, o); for (let i = 0; i < n; i++) pal.push(readVarInt(b, o)); }
    const n = readVarInt(b, o); const longs = []; for (let i = 0; i < n; i++) { longs.push(b.readBigUInt64BE(o.p)); o.p += 8; }
    if (s === section) {
      if (bits === 0) return single;
      const per = Math.floor(64 / bits); const li = Math.floor(idx / per), off = (idx % per) * bits;
      const v = Number((longs[li] >> BigInt(off)) & ((1n << BigInt(bits)) - 1n));
      return pal ? pal[v] : v;
    }
    if (o.p >= b.length) return null;
  }
}
const name = (bot, x, y, z) => { const b = bot.blockAt(new V(x, y, z)); return b ? b.name : null; };

(async () => {
  const bot = await setup('Tester');
  const other = L.makeBot('Other', { plugins: { resource_pack: false } });
  await L.spawned(other); await sleep(1500);
  cmd('op Tester');
  const log = { border: [], borderB: [], equip: [], chunkBiomes: 0, multi: 0, single: 0 };
  bot._client.on('initialize_world_border', p => log.border.push(['init', p]));
  for (const n of ['world_border_size', 'world_border_center', 'world_border_lerp_size']) bot._client.on(n, p => log.border.push([n, p]));
  other._client.on('initialize_world_border', p => log.borderB.push(['init', p]));
  for (const n of ['world_border_size', 'world_border_center', 'world_border_lerp_size']) other._client.on(n, p => log.borderB.push([n, p]));
  bot._client.on('entity_equipment', p => log.equip.push(p));
  log.biomePk = [];
  bot._client.on('chunk_biomes', p => { log.chunkBiomes++; log.biomePk.push(p); });
  bot._client.on('multi_block_change', () => log.multi++);
  bot._client.on('block_change', () => log.single++);
  for (const c of ['/gamemode creative', '/gamerule doMobSpawning false', '/time set day', '/tp @s 0 -60 0']) await chat(bot, c);
  cmd('op Other'); await sleep(500);
  await chat(other, '/tp @s 3 -60 -3', 400);
  await sleep(1500);

  // ---------------------------------------------------------------- 가상 블록
  result('시작: 두 플레이어 모두 (4,-60,4) 공기', name(bot, 4, -60, 4) === 'air' && name(other, 4, -60, 4) === 'air', `${name(bot, 4, -60, 4)} ${name(other, 4, -60, 4)}`);
  await chat(bot, '/kset 4 -60 4 minecraft:gold_block', 500);
  await until(() => name(bot, 4, -60, 4) === 'gold_block');
  result('가상 블록: 건 사람 화면에만 금 블록', name(bot, 4, -60, 4) === 'gold_block' && name(other, 4, -60, 4) === 'air', `${name(bot, 4, -60, 4)} / ${name(other, 4, -60, 4)}`);

  await chat(bot, '/setblock 4 -60 4 minecraft:stone', 700);
  result('진짜 블록이 바뀌어도 층이 이긴다(건 사람=금, 다른 사람=돌)', name(bot, 4, -60, 4) === 'gold_block' && name(other, 4, -60, 4) === 'stone', `${name(bot, 4, -60, 4)} / ${name(other, 4, -60, 4)}`);

  // 청크 재전송: 멀리 갔다 돌아오면 청크를 새로 받는다
  await chat(bot, '/tp @s 600 -60 0', 300);
  await until(() => !bot.world.getColumnAt(new V(4, -60, 4)), 8000);
  const unloaded = !bot.world.getColumnAt(new V(4, -60, 4));
  await chat(bot, '/tp @s 0 -60 0', 300);
  await until(() => bot.world.getColumnAt(new V(4, -60, 4)) && name(bot, 4, -60, 4), 15000);
  await sleep(1000);
  result('청크 재전송 뒤에도 가상 블록 유지 (멀리 갔다 옴)', unloaded && name(bot, 4, -60, 4) === 'gold_block', `unloaded=${unloaded} block=${name(bot, 4, -60, 4)}`);
  result('청크 재전송 뒤 진짜 블록은 그대로(다른 사람 돌)', name(other, 4, -60, 4) === 'stone', name(other, 4, -60, 4));

  // 구역 묶음
  const multiBefore = log.multi;
  await chat(bot, '/kfill 10 -60 10 12 -60 12 minecraft:glass', 600);
  await until(() => name(bot, 11, -60, 11) === 'glass');
  let all = true; for (let x = 10; x <= 12; x++) for (let z = 10; z <= 12; z++) if (name(bot, x, -60, z) !== 'glass') all = false;
  result('채우기: 3x3 모두 유리, 구역 묶음 패킷 사용', all && log.multi > multiBefore && name(other, 11, -60, 11) === 'air', `all=${all} multi=${log.multi - multiBefore}`);

  await chat(bot, '/kclear 4 -60 4', 800);
  await until(() => name(bot, 4, -60, 4) === 'stone');
  result('걷기: 진짜 블록(돌)이 다시 보인다', name(bot, 4, -60, 4) === 'stone', name(bot, 4, -60, 4));
  await chat(bot, '/kclearall', 800);
  await until(() => name(bot, 11, -60, 11) === 'air');
  result('모두 걷기: 유리도 사라진다', name(bot, 11, -60, 11) === 'air', name(bot, 11, -60, 11));
  await chat(bot, '/setblock 4 -60 4 minecraft:air', 300);

  // ---------------------------------------------------------------- 바이옴
  // 서버가 레지스트리 내용을 생략하므로 이름 대신 번호로 비교한다(레지스트리 순서: plains=39, desert=14)
  const DESERT = 14;
  const biomeOf = (b, x, y, z) => { const col = b.world.getColumnAt(new V(x, y, z)); if (!col) return null; try { return col.getBiome(new V(x & 15, y, z & 15)); } catch (e) { return 'err:' + e.message; } };
  const biomeBefore = biomeOf(bot, 4, -60, 4);
  const cbBefore = log.chunkBiomes;
  await chat(bot, '/kbiome 4 -60 4 minecraft:desert', 800);
  await until(() => log.chunkBiomes > cbBefore, 4000);
  const bAfter = biomeOf(bot, 4, -60, 4);
  result('가상 바이옴: chunk_biomes 가 건 사람에게만 온다', log.chunkBiomes > cbBefore, `before=${biomeBefore} after=${bAfter}`);
  const live = log.biomePk.length ? biomeInPacket(log.biomePk[log.biomePk.length - 1], 0, 0, 0, (1 << 4) | (1 << 2) | 1) : null;
  result('가상 바이옴: 받은 chunk_biomes 의 그 칸이 사막(14)', live === DESERT, `칸=${live}`);
  result('가상 바이옴: 다른 사람의 바이옴은 그대로', biomeOf(other, 4, -60, 4) !== DESERT, `after=${bAfter} / 다른 사람=${biomeOf(other, 4, -60, 4)}`);
  await chat(bot, '/tp @s 600 -60 0', 300);
  await until(() => !bot.world.getColumnAt(new V(4, -60, 4)), 8000);
  await chat(bot, '/tp @s 0 -60 0', 300);
  await until(() => bot.world.getColumnAt(new V(4, -60, 4)) && biomeOf(bot, 4, -60, 4), 15000);
  await sleep(800);
  result('청크 재전송 뒤에도 가상 바이옴 유지', biomeOf(bot, 4, -60, 4) === DESERT, biomeOf(bot, 4, -60, 4));
  await chat(bot, '/kbiomeclear 4 -60 4', 800);
  await sleep(500);
  const back = biomeInPacket(log.biomePk[log.biomePk.length - 1], 0, 0, 0, (1 << 4) | (1 << 2) | 1);
  result('바이옴 층 걷기: 진짜 바이옴(평원 39)을 다시 보낸다', back === biomeBefore, `다시 받은 칸=${back} (원래 ${biomeBefore})`);

  // ---------------------------------------------------------------- 개인 보더
  await chat(bot, '/kborder 20 100 100', 700);
  const init = log.border.filter(e => e[0] === 'init').pop();
  result('개인 보더: 건 사람에게 초기화 패킷(중심 100,100 지름 20)', !!init && init[1].x === 100 && init[1].z === 100 && init[1].newDiameter === 20,
    JSON.stringify(init && init[1]));
  const bBefore = log.borderB.length;
  await chat(bot, '/worldborder set 500', 800);
  const aSize = log.border.filter(e => e[0] === 'world_border_size' || e[0] === 'world_border_lerp_size');
  const bSize = log.borderB.slice(bBefore).filter(e => e[0] === 'world_border_size' || e[0] === 'world_border_lerp_size');
  result('서버 보더를 바꿔도 개인 보더 사람에게는 가지 않는다 / 다른 사람에게는 간다', aSize.length === 0 && bSize.length >= 1, `A=${aSize.length} B=${bSize.length}`);
  await chat(bot, '/kborderreset', 800);
  const re = log.border.filter(e => e[0] === 'init').pop();
  result('보더 걷기: 서버 보더(지름 500, 중심 0)가 다시 간다', !!re && re[1].newDiameter === 500 && re[1].x === 0, JSON.stringify(re && re[1]));
  await chat(bot, '/worldborder set 59999968', 300);

  // ---------------------------------------------------------------- 장비 덮어쓰기
  const items = bot.registry.items;
  const itemName = it => it && it.itemId != null && items[it.itemId] ? items[it.itemId].name : null;
  const oid = other.entity.id;
  const seen = (slotName) => log.equip.filter(p => p.entityId === oid).flatMap(p => p.equipments).filter(e => e.slot === slotName);
  const headName = () => { const s = log.equip.filter(p => p.entityId === oid).flatMap(p => p.equipments).filter(e => e.slot === 5 || e.slot === 'head'); const l = s[s.length - 1]; return l ? itemName(l.item) : null; };
  const eqBefore = log.equip.length;
  await chat(bot, '/kequip Other head minecraft:diamond_helmet', 800);
  result('장비 덮어쓰기: 건 사람에게 대상의 머리가 다이아 투구', headName() === 'diamond_helmet', `head=${headName()} 패킷 ${log.equip.length - eqBefore}`);
  const otherSaw = []; other._client.on('entity_equipment', p => otherSaw.push(p));
  await chat(bot, '/item replace entity Other armor.head with minecraft:iron_helmet', 800);
  result('진짜 장비가 바뀌어도 덮어쓴 칸은 유지(여전히 다이아)', headName() === 'diamond_helmet', `head=${headName()}`);
  // 대상을 멀리 보냈다 오게 해서 다시 스폰될 때도 덮어쓴 모습
  await chat(other, '/tp @s 600 -60 0', 300);
  await sleep(2500);
  await chat(other, '/tp @s 3 -60 -3', 300);
  await sleep(3000);
  const afterRespawn = log.equip.filter(p => p.entityId === oid).flatMap(p => p.equipments).slice(-3).map(e => `${e.slot}:${itemName(e.item)}`);
  result('시야 밖에서 돌아와 다시 스폰돼도 덮어쓴 장비', headName() === 'diamond_helmet', `마지막 ${JSON.stringify(afterRespawn)}`);
  await chat(bot, '/kequipclear Other head', 1000);
  result('장비 걷기: 진짜 장비(철 투구)가 다시 보인다', headName() === 'iron_helmet', `head=${headName()}`);

  // ---------------------------------------------------------------- 채팅 서식
  await chat(bot, '/kchat', 500);
  await chat(other, 'hello world', 800);
  const aLine = bot.msgs.find(m => m.m.includes('[view:Tester]'));
  const bLine = other.msgs.find(m => m.m.includes('[view:Other]'));
  result('채팅 서식: 받는 사람마다 다른 줄', !!aLine && !!bLine && aLine.m.includes('Other: hello world') && bLine.m.includes('Other: hello world'),
    `A=${aLine && aLine.m} B=${bLine && bLine.m}`);

  const pass = results.filter(r => r[1]).length;
  console.log(`SUMMARY ${pass}/${results.length}`);
  bot.end(); other.end();
  process.exit(pass === results.length ? 0 : 1);
})().catch(e => { console.log('FATAL', e); process.exit(2); });
