// 플러그인 API 3단계 이벤트 봇 시험 — probe/EventProbe 플러그인을 넣은 서버에서. 결과는 "RESULT 이름 PASS|FAIL 근거" 줄.
// SRV=<서버 폴더> node round4/ev.js
const fs = require('fs');
const mc = require('minecraft-protocol');
const { setup, chat, sleep, cmd, makeBot, spawned, count, hold, waitEnt } = require('../round2/u');
const SRV = process.env.SRV;
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
let logPos = 0;
function mark() { logPos = fs.statSync(SRV + '/server.log').size; }
function evs(re) {
  const s = fs.readFileSync(SRV + '/server.log').subarray(logPos).toString('utf8');   // 바이트 위치(로그에 한글이 있다)
  return s.split('\n').filter(l => l.includes('[EV] ') && re.test(l)).map(l => l.slice(l.indexOf('[EV] ') + 5));
}
async function evcancel(bot, ...names) { await chat(bot, '/evcancel ' + (names.length ? names.join(' ') : 'none'), 400); }
const ping = () => new Promise((res, rej) => mc.ping({ host: '127.0.0.1', port: 25565, version: '1.21.1' }, (e, r) => e ? rej(e) : res(r)));

(async () => {
  // 서버 목록 핑(접속 전)
  mark();
  const p1 = await ping();
  result('ServerListPingEvent 발화', evs(/ServerListPingEvent/).length === 1, JSON.stringify(evs(/ServerListPingEvent/)));
  const bot = await setup();
  for (const c of ['/gamerule doMobSpawning false', '/gamerule doDaylightCycle false', '/time set day', '/difficulty peaceful', '/gamemode survival', '/clear @s', '/tp @s 0 -60 0']) await chat(bot, c);
  await evcancel(bot, 'ServerListPingEvent');
  const p2 = await ping();
  const motd = JSON.stringify(p2.description);
  result('ServerListPingEvent MOTD·최대 인원 바꾸기', motd.includes('probe motd') && p2.players.max === 7 && JSON.stringify(p1.description) !== motd, `before=${JSON.stringify(p1.description)} after=${motd} max=${p2.players.max}`);
  await evcancel(bot);

  // 관찰자: 웅크림이 서버에서 반영됐는지(공유 플래그 0x02)를 본다
  const obs = makeBot('Observer', { plugins: { resource_pack: false } });
  await spawned(obs); await sleep(1500);
  const crouchSeen = [];
  obs._client.on('entity_metadata', p => { if (p.entityId === bot.entity.id) for (const m of p.metadata) if (m.key === 0) crouchSeen.push((m.value & 0x02) !== 0); });
  await chat(bot, '/tp Observer 2 -60 0', 1500);

  // 웅크림
  mark(); crouchSeen.length = 0;
  bot.setControlState('sneak', true); await sleep(600); bot.setControlState('sneak', false); await sleep(600);
  result('PlayerToggleSneakEvent 누름·뗌', JSON.stringify(evs(/ToggleSneak/)) === JSON.stringify(['PlayerToggleSneakEvent Tester true', 'PlayerToggleSneakEvent Tester false']), JSON.stringify(evs(/ToggleSneak/)));
  result('웅크림(구독 중) 관찰자에게 보임', crouchSeen.includes(true), JSON.stringify(crouchSeen));
  await evcancel(bot, 'PlayerToggleSneakEvent'); mark(); crouchSeen.length = 0;
  bot.setControlState('sneak', true); await sleep(800); bot.setControlState('sneak', false); await sleep(600);
  result('웅크림 취소면 관찰자에게 웅크림 플래그 없음', !crouchSeen.includes(true) && evs(/ToggleSneak.*CANCELLED/).length === 2, JSON.stringify(crouchSeen) + ' ' + JSON.stringify(evs(/ToggleSneak/)));
  await evcancel(bot);

  // 달리기
  mark();
  bot.setControlState('forward', true); bot.setControlState('sprint', true); await sleep(800);
  bot.setControlState('sprint', false); bot.setControlState('forward', false); await sleep(800);
  result('PlayerToggleSprintEvent 시작·멈춤', evs(/ToggleSprint.*true/).length >= 1 && evs(/ToggleSprint.*false/).length >= 1, JSON.stringify(evs(/ToggleSprint/)));

  // 단축 칸
  bot.setQuickBarSlot(0); await sleep(400);
  mark(); bot.setQuickBarSlot(3); await sleep(500);
  result('PlayerItemHeldEvent 0->3', evs(/ItemHeld/).includes('PlayerItemHeldEvent 0->3'), JSON.stringify(evs(/ItemHeld/)));
  await evcancel(bot, 'PlayerItemHeldEvent');
  let heldBack = null; bot._client.once('held_item_slot', p => { heldBack = p.slot; });
  mark(); bot.setQuickBarSlot(5); await sleep(700);
  result('PlayerItemHeldEvent 취소면 이전 칸을 되돌려 보냄', heldBack === 3, `held_item_slot=${heldBack} ${JSON.stringify(evs(/ItemHeld/))}`);
  await evcancel(bot);
  bot.setQuickBarSlot(0); await sleep(400);

  // 손 바꾸기(F) — 구현 자체도 확인
  await chat(bot, '/clear @s', 300);
  await chat(bot, '/item replace entity @s weapon.mainhand with minecraft:diamond 3', 300);
  await chat(bot, '/item replace entity @s weapon.offhand with minecraft:stick 5', 600);
  const swap = () => bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
  const hands = () => [bot.inventory.slots[36]?.name ?? 'air', bot.inventory.slots[45]?.name ?? 'air'].join('/');
  mark(); swap(); await sleep(800);
  result('F 키 손 바꾸기 동작', hands() === 'stick/diamond', hands());
  result('PlayerSwapHandItemsEvent 바뀔 내용', evs(/SwapHand/).includes('PlayerSwapHandItemsEvent main=minecraft:stick off=minecraft:diamond'), JSON.stringify(evs(/SwapHand/)));
  await evcancel(bot, 'PlayerSwapHandItemsEvent');
  mark(); swap(); await sleep(800);
  result('손 바꾸기 취소면 그대로', hands() === 'stick/diamond' && evs(/SwapHand.*CANCELLED/).length === 1, hands());
  await evcancel(bot);

  // 먹기(황금 사과는 배불러도 먹는다)
  await chat(bot, '/clear @s', 300);
  await chat(bot, '/give @s minecraft:golden_apple 3', 600);
  await hold(bot, 'golden_apple');
  mark(); bot.activateItem(); await sleep(2300); bot.deactivateItem(); await sleep(500);
  result('PlayerItemConsumeEvent 먹음', evs(/ItemConsume/).includes('PlayerItemConsumeEvent minecraft:golden_apple main=true') && count(bot, 'golden_apple') === 2, `${count(bot, 'golden_apple')} ${JSON.stringify(evs(/ItemConsume/))}`);
  await evcancel(bot, 'PlayerItemConsumeEvent');
  await chat(bot, '/effect clear @s', 300);
  mark(); bot.activateItem(); await sleep(2300); bot.deactivateItem(); await sleep(800);
  const eff = await new Promise(r => { let got = false; bot.once('entityEffect', () => { got = true; }); setTimeout(() => r(got), 300); });
  result('먹기 취소면 사과가 남고 효과 없음', count(bot, 'golden_apple') === 2 && evs(/ItemConsume.*CANCELLED/).length === 1 && !bot.entity.effects?.[10] && !Object.keys(bot.entity.effects ?? {}).length, `${count(bot, 'golden_apple')} effects=${JSON.stringify(Object.keys(bot.entity.effects ?? {}))}`);
  await evcancel(bot);

  // 경험치·레벨
  mark(); await chat(bot, '/xp add Tester 5 levels', 1000);
  result('PlayerLevelChangeEvent 0->5', evs(/LevelChange/).includes('PlayerLevelChangeEvent 0->5'), JSON.stringify(evs(/LevelChange/)));
  mark(); await chat(bot, '/summon minecraft:experience_orb ~ ~ ~ {Value:7}', 1500);
  result('PlayerExpChangeEvent 구슬 7', evs(/ExpChange/).includes('PlayerExpChangeEvent 7'), JSON.stringify(evs(/ExpChange/)));
  await evcancel(bot, 'PlayerExpChangeEvent');
  const before = bot.experience.points;
  mark(); await chat(bot, '/summon minecraft:experience_orb ~ ~ ~ {Value:7}', 1500);
  result('구슬 경험치를 0 으로 바꾸면 늘지 않음', bot.experience.points === before && evs(/ExpChange/).length === 1, `${before} -> ${bot.experience.points}`);
  await evcancel(bot);

  // 발전 과제
  mark(); await chat(bot, '/advancement grant Tester only minecraft:story/mine_stone', 1200);
  result('PlayerAdvancementDoneEvent', evs(/AdvancementDone/).includes('PlayerAdvancementDoneEvent minecraft:story/mine_stone msg=true'), JSON.stringify(evs(/AdvancementDone/)));

  // 투사체 발사·적중
  await chat(bot, '/clear @s', 300); await chat(bot, '/give @s minecraft:snowball 4', 600); await hold(bot, 'snowball');
  await bot.look(0, -Math.PI / 2.2, true); await sleep(300);
  mark(); bot.activateItem(); await sleep(1500);
  result('ProjectileLaunchEvent 눈덩이', evs(/ProjectileLaunch/).includes('ProjectileLaunchEvent minecraft:snowball'), JSON.stringify(evs(/Projectile/)));
  result('ProjectileHitEvent 땅에 맞음', evs(/ProjectileHit.*snowball.*block=minecraft:/).length >= 1, JSON.stringify(evs(/ProjectileHit/)));
  await bot.look(0, 0, true);

  // 아이템 스폰
  mark(); await chat(bot, '/summon minecraft:item ~ ~1 ~3 {Item:{id:"minecraft:dirt",count:1}}', 800);
  result('ItemSpawnEvent', evs(/ItemSpawnEvent minecraft:item/).length === 1, JSON.stringify(evs(/ItemSpawn/)));

  // 폭발: TNT, 취소하면 블록 그대로
  await chat(bot, '/fill 20 -61 20 24 -61 24 minecraft:stone', 400);
  await evcancel(bot, 'EntityExplodeEvent');
  mark(); await chat(bot, '/summon minecraft:tnt 22 -60 22 {fuse:1}', 1500);
  const stoneLeft = (await new Promise(r => setTimeout(() => r(bot.blockAt(new (require('vec3'))(22, -61, 22))?.name), 200)));
  result('EntityExplodeEvent 취소면 블록 그대로', evs(/EntityExplode.*tnt.*CANCELLED/).length === 1 && stoneLeft === 'stone', `${stoneLeft} ${JSON.stringify(evs(/Explode/))}`);
  await evcancel(bot);
  mark(); await chat(bot, '/summon minecraft:tnt 22 -60 22 {fuse:1}', 1500);
  const after = bot.blockAt(new (require('vec3'))(22, -61, 22))?.name;
  result('EntityExplodeEvent 그대로면 부서짐', evs(/EntityExplode.*tnt/).length === 1 && after === 'air', `${after} ${JSON.stringify(evs(/Explode/))}`);

  // 대상 지정(쉬움 난이도 좀비)
  await chat(bot, '/difficulty easy', 300);
  await chat(bot, '/effect give @s minecraft:resistance 60 4 true', 200);
  mark(); await chat(bot, '/summon minecraft:zombie 0 -60 4 {PersistenceRequired:1b}', 3000);
  result('EntityTargetEvent 좀비 -> 플레이어', evs(/EntityTarget.*zombie->minecraft:player/).length >= 1, JSON.stringify(evs(/EntityTarget/)));
  await chat(bot, '/kill @e[type=zombie]', 300); await chat(bot, '/difficulty peaceful', 300);

  // 길들이기(늑대, 뼈) — 취소면 늘 실패
  await chat(bot, '/tp @s 40 -60 0', 1200);
  await chat(bot, '/summon minecraft:wolf 40 -60 2 {NoAI:1b}', 800);
  await chat(bot, '/clear @s', 200); await chat(bot, '/give @s minecraft:bone 64', 600); await hold(bot, 'bone');
  const wolf = await waitEnt(bot, 'wolf');
  await evcancel(bot, 'EntityTameEvent');
  mark();
  for (let i = 0; i < 20 && wolf; i++) { await bot.lookAt(wolf.position.offset(0, 0.4, 0)); bot.activateEntity(wolf); await sleep(150); }
  await sleep(500);
  const tamedTry = evs(/EntityTame/);
  result('EntityTameEvent 취소면 길들지 않음', tamedTry.length >= 1 && tamedTry.every(l => l.includes('CANCELLED')), JSON.stringify(tamedTry));
  await evcancel(bot); mark();
  for (let i = 0; i < 20 && wolf && !evs(/EntityTame/).length; i++) { await bot.lookAt(wolf.position.offset(0, 0.4, 0)); bot.activateEntity(wolf); await sleep(150); }
  await sleep(500);
  result('EntityTameEvent 그대로면 길듦', evs(/EntityTameEvent minecraft:wolf by Tester$/).length === 1, JSON.stringify(evs(/EntityTame/)));

  // 번식(소 둘, 밀)
  await chat(bot, '/tp @s 60 -60 0', 1200);
  await chat(bot, '/summon minecraft:cow 60 -60 2 {NoAI:0b}', 300); await chat(bot, '/summon minecraft:cow 61 -60 2', 800);
  await chat(bot, '/clear @s', 200); await chat(bot, '/give @s minecraft:wheat 8', 600); await hold(bot, 'wheat');
  mark();
  for (const c of Object.values(bot.entities).filter(e => e.name === 'cow')) { await bot.lookAt(c.position.offset(0, 0.8, 0)); bot.activateEntity(c); await sleep(400); }
  await sleep(8000);
  result('EntityBreedEvent 새끼 소, 먹인 사람', evs(/EntityBreedEvent minecraft:cow breeder=Tester/).length === 1, JSON.stringify(evs(/EntityBreed/)));

  // 차원 이동
  mark(); await chat(bot, '/execute in minecraft:the_nether run tp Tester 0 100 0', 3000);
  result('PlayerChangedWorldEvent 오버월드 -> 네더', evs(/ChangedWorld/).includes('PlayerChangedWorldEvent minecraft:overworld->minecraft:the_nether'), JSON.stringify(evs(/ChangedWorld/)));
  await chat(bot, '/execute in minecraft:overworld run tp Tester 80 -60 0', 3000);

  // 침대
  await chat(bot, '/time set 13000', 300);
  await chat(bot, '/setblock 80 -60 2 minecraft:red_bed[facing=south,part=foot]', 300);
  await chat(bot, '/setblock 80 -60 3 minecraft:red_bed[facing=south,part=head]', 800);
  const bed = bot.blockAt(new (require('vec3'))(80, -60, 3));
  await evcancel(bot, 'PlayerBedEnterEvent'); mark();
  try { await bot.sleep(bed); } catch (e) { console.log('sleep(취소)', e.message); }
  await sleep(800);
  result('PlayerBedEnterEvent 취소면 잠들지 않음', !bot.isSleeping && evs(/BedEnter.*CANCELLED/).length === 1, `${bot.isSleeping} ${JSON.stringify(evs(/Bed/))}`);
  await evcancel(bot); mark();
  try { await bot.sleep(bed); } catch (e) { console.log('sleep', e.message); }
  await sleep(1000);
  const slept = bot.isSleeping;
  try { await bot.wake(); } catch (e) { console.log('wake', e.message); }
  await sleep(1000);
  result('PlayerBedEnter/LeaveEvent', slept && evs(/BedEnterEvent minecraft:red_bed/).length === 1 && evs(/BedLeaveEvent/).length === 1, `${slept} ${JSON.stringify(evs(/Bed/))}`);
  await chat(bot, '/time set day', 300);

  // 비행(크리에이티브) — 취소면 능력 패킷으로 되돌림
  await chat(bot, '/gamemode creative', 800);
  const fly = on => bot._client.write('abilities', { flags: on ? 0x02 : 0 });   // mineflayer creative.startFlying 은 패킷을 보내지 않는다
  mark(); fly(true); await sleep(600); fly(false); await sleep(600);
  result('PlayerToggleFlightEvent 켬·끔', evs(/ToggleFlight.*true/).length === 1 && evs(/ToggleFlight.*false/).length === 1, JSON.stringify(evs(/ToggleFlight/)));
  await evcancel(bot, 'PlayerToggleFlightEvent');
  let abil = null; bot._client.once('abilities', p => { abil = p.flags; });
  mark(); fly(true); await sleep(800);
  result('비행 취소면 능력 패킷(날지 않음)을 다시 보냄', abil !== null && (abil & 0x02) === 0, `flags=${abil} ${JSON.stringify(evs(/ToggleFlight/))}`);
  await evcancel(bot); await chat(bot, '/gamemode survival', 400);

  // 킥: 취소 → 사유 바꾸기
  let obsKick = null; obs.on('kicked', r => { obsKick = JSON.stringify(r); });
  await evcancel(bot, 'PlayerKickEvent'); mark();
  await chat(bot, '/kick Observer bye', 1200);
  result('PlayerKickEvent 취소면 남음', obsKick === null && evs(/PlayerKickEvent.*CANCELLED/).length === 1, JSON.stringify(evs(/Kick/)));
  await evcancel(bot, 'KickReason'); mark();
  await chat(bot, '/kick Observer bye', 1500);
  result('PlayerKickEvent 사유 바꾸기', obsKick !== null && obsKick.includes('probe reason'), obsKick);

  const fail = results.filter(r => !r[1]).length;
  console.log(`SUMMARY ${results.length - fail}/${results.length} PASS`);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error('SCRIPT ERROR', e); process.exit(1); });
