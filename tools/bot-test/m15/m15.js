// M15: 걸음 소리(플레이어 → 다른 플레이어에게만, 몹 → 모두), 조합 블록(카펫)·자수정 울림을 sound_effect 패킷으로 확인한다.
// 봇 둘(Tester=관찰·op, Walker=걷는 쪽). 소리 분류는 mineflayer 가 이름('player'·'neutral')으로 준다.
const { makeBot, spawned, chat, sleep } = require('../lib');
(async () => {
  const bot = makeBot('Tester'); await spawned(bot); await sleep(1500);
  require('fs').appendFileSync(process.env.SRV + '/cmds', 'op Tester\n'); await sleep(800);
  const walker = makeBot('Walker'); await spawned(walker); await sleep(1500);
  const heard = [], walkerHeard = [];
  bot._client.on('sound_effect', p => heard.push({ t: Date.now(), p }));
  walker._client.on('sound_effect', p => walkerHeard.push({ t: Date.now(), p }));
  for (const c of ['/gamerule doMobSpawning false', '/difficulty peaceful', '/time set day', '/kill @e[type=!player]', '/gamemode survival Tester', '/gamemode survival Walker']) await chat(bot, c, 400);
  const R = [];
  const res = (name, ok, extra = '') => { R.push(ok); console.log('RESULT', ok ? 'PASS' : 'FAIL', name, extra); };
  const cat = s => s.p.soundCategory;
  const vol = s => s.p.volume;
  const info = s => JSON.stringify({ cat: cat(s), vol: vol(s), pitch: s.p.pitch, id: s.p.soundEvent?.soundId ?? s.p.soundEvent?.soundName, x: +s.p.x / 8, z: +s.p.z / 8 });

  // A. Walker 가 돌 바닥을 걷는다 — Tester 는 소리를 듣고, Walker 본인은 못 듣는다
  await chat(bot, '/tp Tester 0.5 -60 5.5', 600);
  await chat(bot, '/tp Walker 0.5 -60 0.5 -180 0', 800);
  heard.length = 0; walkerHeard.length = 0;
  walker.setControlState('forward', true); walker.setControlState('sprint', false); await sleep(4000); walker.setControlState('forward', false);
  const steps = heard.filter(s => cat(s) === 'player' && Math.abs(vol(s) - 0.15) < 1e-3);
  console.log('A heard', heard.length, JSON.stringify(heard.slice(0, 3).map(info)));
  res('걷는 플레이어의 걸음 소리가 다른 플레이어에게 간다(players 분류, 크기 0.15)', steps.length >= 2, `n=${steps.length}`);
  res('걷는 본인에게는 걸음 소리가 오지 않는다', walkerHeard.filter(s => cat(s) === 'player').length === 0, `n=${walkerHeard.filter(s => cat(s) === 'player').length}`);

  // B. 웅크리면 소리 없음
  heard.length = 0;
  walker.setControlState('sneak', true); walker.setControlState('forward', true); await sleep(3000); walker.setControlState('forward', false); walker.setControlState('sneak', false);
  res('웅크리고 걸으면 걸음 소리 없음', heard.filter(s => cat(s) === 'player').length === 0, `n=${heard.filter(s => cat(s) === 'player').length}`);

  // C. 이끼 카펫 위(조합 블록): 한 걸음에 소리 둘(카펫 0.15 + 발밑 0.05)
  await chat(bot, '/fill -2 -60 -12 3 -60 3 minecraft:moss_carpet', 800);
  await chat(bot, '/tp Walker 0.5 -60 0.5 -180 0', 800);
  heard.length = 0;
  walker.setControlState('forward', true); await sleep(3500); walker.setControlState('forward', false);
  const big = heard.filter(s => cat(s) === 'player' && Math.abs(vol(s) - 0.15) < 1e-3).length, small = heard.filter(s => cat(s) === 'player' && Math.abs(vol(s) - 0.05) < 1e-3).length;
  console.log('C', heard.length, JSON.stringify(heard.slice(0, 4).map(info)));
  res('카펫 위 걸음: 0.15 소리와 0.05 소리가 짝으로', big >= 2 && small === big, `0.15 x${big}, 0.05 x${small}`);
  await chat(bot, '/fill -2 -60 -12 3 -60 3 minecraft:air', 800);

  // D. 몹: 소가 돌아다니면 neutral 분류 크기 0.15 의 걸음 소리가 모두에게 간다
  await chat(bot, '/tp Walker 20.5 -60 20.5', 600);
  await chat(bot, '/summon cow 0.5 -60 8.5', 600);
  heard.length = 0;
  await chat(bot, '/data merge entity @e[type=cow,limit=1] {Motion:[0.0,0.0,0.0]}', 300);
  for (let i = 0; i < 40 && heard.filter(s => cat(s) === 'neutral' && Math.abs(vol(s) - 0.15) < 1e-3).length < 2; i++) await sleep(1000);
  const cow = heard.filter(s => cat(s) === 'neutral' && Math.abs(vol(s) - 0.15) < 1e-3);
  console.log('D', heard.length, JSON.stringify(heard.slice(0, 4).map(info)));
  res('소의 걸음 소리(neutral, 크기 0.15, 높이 1.0)', cow.length >= 2 && cow.every(s => Math.abs(s.p.pitch - 1.0) < 1e-3), `n=${cow.length}`);

  // E. 자수정: Walker 가 자수정 블록 위를 걸으면 울림이 한 번 이상(크기 0.17 근처)
  await chat(bot, '/fill -2 -61 -12 3 -61 3 minecraft:amethyst_block', 800);
  await chat(bot, '/tp Walker 0.5 -60 0.5 -180 0', 800);
  heard.length = 0;
  walker.setControlState('forward', true); await sleep(3500); walker.setControlState('forward', false);
  const chime = heard.filter(s => cat(s) === 'player' && vol(s) > 0.15 && vol(s) < 1.4 && Math.abs(vol(s) - 0.15) > 1e-3);
  console.log('E', heard.length, JSON.stringify(heard.slice(0, 5).map(info)));
  res('자수정 위 걷기: 걸음 소리 뒤 울림(크기 0.1 + 세기×1.2)', chime.length >= 1, `n=${chime.length}`);
  console.log(R.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.end(); walker.end(); process.exit(0);
})().catch(e => { console.log('ERR', e); process.exit(1); });
