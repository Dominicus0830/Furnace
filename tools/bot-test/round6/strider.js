// 스트라이더 떨기: 차가운 땅에서 떨면(DATA_SUFFOCATING, 메타데이터 18) 이동 속도 수정자 -34%(곱하기 기준)가 붙는지, 새끼 기수도 같이 떠는지,
// 실제로 느려 보이는지는 클라이언트가 계산(탄 채)하므로 서버가 보낸 속성·플래그까지만 본다.
const U = require('../round2/u'); const { sleep, chat, ask } = U;
(async () => {
  const bot = await U.setup('Strider');
  const meta = {}; bot._client.on('entity_metadata', p => { for (const m of p.metadata) if (m.key === 18) meta[p.entityId] = m.value; });
  const attrs = {}; bot._client.on('entity_update_attributes', p => { const s = p.properties.find(x => /movement_speed/.test(x.key)); if (s) attrs[p.entityId] = s; });
  for (const c of ['/gamemode creative', '/difficulty peaceful', '/kill @e[type=strider]', '/tp @s 0.5 -60 0.5']) await chat(bot, c);
  await chat(bot, '/summon strider 0.5 -60 3.5 {NoAI:0b,Tags:["mom"],Passengers:[{id:"minecraft:strider",Age:-24000,Tags:["kid"]}]}', 2500);
  const st = U.near(bot, 'strider'); const sp = async t => (await ask(bot, `/attribute @e[tag=${t},limit=1] minecraft:generic.movement_speed get`, /attribute|value|No entity|Found no/));
  console.log('RESULT 차가운 땅 3초 뒤: 떨기 플래그', JSON.stringify(Object.fromEntries(st.map(e => [e.id, meta[e.id]]))), '어미 속도', await sp('mom'), '새끼 속도', await sp('kid'), '(기본 0.175, 떨면 0.1155)');
  console.log('RESULT 봇이 받은 속성 패킷 수정자', JSON.stringify(Object.fromEntries(st.map(e => [e.id, attrs[e.id]?.modifiers?.map(m => m.uuid ?? m.id) ?? null]))));
  // 용암 위로 옮기면 멈춘다
  await chat(bot, '/fill -2 -61 8 3 -61 13 lava'); await chat(bot, '/tp @e[tag=mom] 0.5 -61 10.5', 3000);
  console.log('RESULT 용암 위 3초 뒤: 떨기', JSON.stringify(Object.fromEntries(st.map(e => [e.id, meta[e.id]]))), '어미 속도', await sp('mom'), '새끼 속도', await sp('kid'));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
