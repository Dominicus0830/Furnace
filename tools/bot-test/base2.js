// 유휴 킥(player-idle-timeout=1)과 리소스팩 수락·거절·늦은 응답
const { makeBot, spawned, sleep } = require('./lib');
const mode = process.argv[2];
(async () => {
  if (mode === 'idle') {
    const b = makeBot('Idler'); let kick = null; b.on('kicked', r => kick = r);
    await spawned(b); const t = Date.now();
    while (!kick && Date.now() - t < 120000) await sleep(1000);
    console.log('RESULT 유휴 킥', ((Date.now() - t) / 1000).toFixed(0), 's', JSON.stringify(kick));
    process.exit(0);
  }
  // 리소스팩: accept | decline | slow
  const b = makeBot('Packer', { plugins: { resource_pack: false } }); let kick = null, packs = [];
  b.on('kicked', r => kick = r);
  b._client.removeAllListeners('add_resource_pack');   // mineflayer 는 구성 단계 팩을 자동 수락한다 — 직접 답한다
  b._client.on('add_resource_pack', async p => {
    packs.push({ url: p.url, forced: p.forced, state: b._client.state });
    const send = r => b._client.write('resource_pack_receive', { uuid: p.uuid, result: r });
    if (mode === 'decline') send(1);
    else { send(3); if (mode === 'slow') await sleep(45000); send(4); send(0); }
  });
  const t = Date.now(); let ok = false; b.on('kicked', () => console.log('kick at', ((Date.now() - t) / 1000).toFixed(1) + 's'));
  try { await spawned(b, 70000); ok = true; } catch (e) {}
  console.log('RESULT 리소스팩', mode, '팩 요청', JSON.stringify(packs), '입장', ok, ((Date.now() - t) / 1000).toFixed(0) + 's', '킥', JSON.stringify(kick));
  b.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
