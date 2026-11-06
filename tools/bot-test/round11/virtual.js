// K1 가상 엔티티·NPC·가짜 탭 줄 봇 시험 — round11/probe/VirtualProbe 를 넣은 서버에서. "RESULT 이름 PASS|FAIL 근거".
// SRV=<서버 폴더> node round11/virtual.js
const fs = require('fs');
const { makeBot, spawned, sleep, chat } = require('../lib');
const SRV = process.env.SRV;
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
const log = () => fs.readFileSync(SRV + '/server.log').toString('utf8').split('\n').filter(l => l.includes('[VE] ')).map(l => l.slice(l.indexOf('[VE] ') + 5));

(async () => {
  const bot = makeBot('Tester', { plugins: { resource_pack: false } });
  await spawned(bot); await sleep(1500);
  const pk = [];
  bot._client.on('packet', (d, m) => { if (m.state === 'play' && /entity|player_info|team|equipment|remove/.test(m.name)) pk.push({ n: m.name, d }); });
  const take = () => pk.splice(0);

  // 가상 주민: 클라이언트(minecraft-protocol)가 패킷을 해석하고 엔티티 목록에 올린다
  await chat(bot, '/vmob', 800);
  const mobId = Number((log().find(l => l.startsWith('mob id=')) ?? '').slice(7));
  let got = take();
  result('가상 주민 add_entity 수신', got.some(x => x.n === 'spawn_entity' && x.d.entityId === mobId), got.map(x => x.n).join(','));
  const metas = got.filter(x => x.n === 'entity_metadata' && x.d.entityId === mobId).flatMap(x => x.d.metadata);
  result('이름표 메타데이터(커스텀 이름·보임)', metas.some(m => m.key === 2 && m.value?.value === '가상 주민') && metas.some(m => m.key === 3 && m.value === true), JSON.stringify(metas));
  result('mineflayer 엔티티 목록에 올라감', !!bot.entities[mobId] && bot.entities[mobId].name === 'villager', String(bot.entities[mobId]?.name));

  // 클릭: 서버에 없는 번호여도 처리기가 불린다
  bot.useOn(bot.entities[mobId]);
  await sleep(800);
  bot.attack(bot.entities[mobId]); await sleep(800);
  const clicks = log().filter(l => l.startsWith('click '));
  result('가상 주민 클릭(우클릭·공격)', clicks.some(l => l.includes(' use ')) && clicks.some(l => l.includes(' attack ')), JSON.stringify(clicks));

  // 가짜 플레이어
  take();
  await chat(bot, '/vnpc', 900);
  const npcId = Number((log().find(l => l.startsWith('npc id=')) ?? '').slice(7));
  got = take();
  const names = got.map(x => x.n);
  const infoIdx = names.indexOf('player_info'), spawnIdx = got.findIndex(x => x.n === 'spawn_entity' && x.d.entityId === npcId);
  result('NPC: 프로필 정보 → 플레이어 스폰 순서', infoIdx >= 0 && spawnIdx > infoIdx, names.join(','));
  result('NPC: 이름표 숨김 팀', got.some(x => x.n === 'teams' && x.d.team?.startsWith('fnpc_')), JSON.stringify(got.find(x => x.n === 'teams')?.d));
  result('NPC: 장비 두 칸 수신', got.filter(x => x.n === 'entity_equipment' && x.d.entityId === npcId).flatMap(x => x.d.equipments).map(e => e.slot).sort().join() === '0,5', JSON.stringify(got.filter(x => x.n === 'entity_equipment').map(x => x.d)));
  result('NPC: 탭 목록에 안 올림', !!bot.entities[npcId] && bot.players['Guard'] && !bot.players['Guard'].listed, JSON.stringify(bot.players['Guard']));
  // 가짜 탭 줄
  take();
  await chat(bot, '/vtab', 800);
  result('가짜 탭 줄(추가 → 지연 → 표시 이름)', bot.players['FakeLine'] != null && bot.players['FakeLine'].ping === 200, JSON.stringify(bot.players['FakeLine']));
  // 정리
  take();
  await chat(bot, '/vclear', 800);
  got = take();
  result('정리: 엔티티 제거 + 프로필 제거', got.some(x => x.n === 'entity_destroy') && got.some(x => x.n === 'player_remove'), got.map(x => x.n).join(','));
  result('정리 뒤 엔티티 없음', !bot.entities[mobId] && !bot.entities[npcId], '');

  const fails = results.filter(r => !r[1]).length;
  console.log(`SUMMARY ${results.length - fails}/${results.length} PASS`);
  bot.end(); process.exit(fails ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
