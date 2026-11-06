// D2 데이터 주도 레지스트리 봇 시험: 월드 데이터 팩(round11/pack)의 늑대 변종·마법부여·그림이 구성 단계 RegistryData 에
// 데이터째 실리는지, 그 번호로 아이템·엔티티가 오가는지. 서버 폴더는 newsrv.sh 로 만들고 pack 을 world/datapacks/d2 로 복사해 띄운다.
const { makeBot, spawned, cmd, sleep } = require('../lib');

(async () => {
  const bot = makeBot('Reg');
  const regData = {};
  bot._client.on('registry_data', p => { regData[p.id] = p.entries; });
  await spawned(bot);
  const results = [];
  const ok = (name, cond, info = '') => { results.push(`${cond ? 'PASS' : 'FAIL'} ${name} ${info}`); };

  const wolves = (regData['minecraft:wolf_variant'] || []).map(e => e.key);
  ok('늑대 변종 순서(경로순, d2:aaa 가 처음)', wolves[0] === 'd2:aaa' && wolves.length === 10, JSON.stringify(wolves));
  const aaa = (regData['minecraft:wolf_variant'] || []).find(e => e.key === 'd2:aaa');
  ok('데이터 팩 변종은 데이터째', !!(aaa && aaa.value), JSON.stringify(aaa?.value)?.slice(0, 120));
  const pale = (regData['minecraft:wolf_variant'] || []).find(e => e.key === 'minecraft:pale');
  ok('바닐라 변종은 데이터 없이', pale && !pale.value);
  const ench = (regData['minecraft:enchantment'] || []).map(e => e.key);
  ok('마법부여에 d2:zap', ench.includes('d2:zap'), `index=${ench.indexOf('d2:zap')} / ${ench.length}`);
  const paint = (regData['minecraft:painting_variant'] || []).map(e => e.key);
  ok('그림에 d2:big', paint.includes('d2:big'));

  cmd('op Reg');
  await sleep(800);
  bot.chat('/give Reg minecraft:diamond_sword[minecraft:enchantments={levels:{"d2:zap":2,"minecraft:sharpness":1}}]');
  await sleep(1500);
  const sword = bot.inventory.items().find(i => i.name === 'diamond_sword');
  const levels = sword?.components?.find?.(c => c.type === 'enchantments')?.data?.enchantments;
  ok('사용자 마법부여 아이템 받음', !!sword, JSON.stringify(levels ?? sword?.components)?.slice(0, 200));

  bot.chat('/summon minecraft:wolf ~2 ~ ~ {variant:"d2:aaa",NoAI:1b}');
  await sleep(1500);
  const wolf = Object.values(bot.entities).find(e => e.name === 'wolf');
  ok('늑대 소환·변종 메타데이터 받음', !!wolf, JSON.stringify(wolf?.metadata?.slice?.(17)));
  bot.chat('/data get entity @e[type=wolf,limit=1] variant');
  await sleep(1000);
  const got = bot.msgs.map(m => m.m).find(m => /variant|has the following/.test(m));
  ok('저장 변종 이름 d2:aaa', !!got && got.includes('d2:aaa'), got);

  bot.chat('/gamerule doDaylightCycle');
  await sleep(500);
  console.log(results.join('\n'));
  bot.quit();
  process.exit(results.some(r => r.startsWith('FAIL')) ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
