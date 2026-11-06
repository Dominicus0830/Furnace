// W2 봇 시험 — round-w2/probe/GlowProbe(바이옴 glow:cave·glow:plain, 차원 타입 glow:deep 등록)를 넣은 서버에서.
// "RESULT 이름 PASS|FAIL 근거". SRV=<서버 폴더> node round-w2/glow.js
const fs = require('fs');
const nbt = require('prismarine-nbt');
const { makeBot, spawned, sleep, chat, cmd } = require('../lib');
const SRV = process.env.SRV;
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
const log = () => fs.readFileSync(SRV + '/server.log').toString('utf8').split('\n').filter(l => l.includes('[GB] ')).map(l => l.slice(l.indexOf('[GB] ') + 5));

(async () => {
  const bot = makeBot('Tester', { plugins: { resource_pack: false } });
  const regs = {};
  bot._client.on('registry_data', p => { regs[p.id] = p.entries; });
  const pk = [];
  bot._client.on('packet', (d, m) => { if (m.state === 'play') pk.push(m.name); });
  await spawned(bot); await sleep(1500);

  const biomes = regs['minecraft:worldgen/biome'] || [];
  const keys = biomes.map(e => e.key);
  const iCave = keys.indexOf('glow:cave');
  result('바이옴 레지스트리: 바닐라 64개 뒤에 플러그인 2개', biomes.length === 66 && iCave === 64 && keys[65] === 'glow:plain' && keys[63].startsWith('minecraft:'), `n=${biomes.length} cave=${iCave} last=${keys.slice(-3)}`);
  const cave = biomes[iCave]?.value ? nbt.simplify(biomes[iCave].value) : null;
  result('바이옴 본문(기후·색·소리·입자)', !!cave && Math.abs(cave.temperature - 0.1) < 1e-6 && cave.effects.sky_color === 0x112233
    && cave.effects.ambient_sound.sound_id === 'glow:ambient.hum' && cave.effects.particle.options.type === 'minecraft:ash'
    && cave.effects.music.min_delay === 1200 && cave.effects.grass_color_modifier === 'swamp', JSON.stringify(cave));
  result('바닐라 바이옴은 본문 없음(known packs)', biomes[0].value == null || biomes[0].value === undefined, JSON.stringify(biomes[0]).slice(0, 80));
  const dims = regs['minecraft:dimension_type'] || [];
  const deep = dims.find(e => e.key === 'glow:deep');
  const dv = deep?.value ? nbt.simplify(deep.value) : null;
  result('차원 타입 glow:deep 본문', !!dv && dv.height === 128 && dv.min_y === 0 && (Array.isArray(dv.fixed_time) ? dv.fixed_time[1] : dv.fixed_time) === 6000 && dv.infiniburn === '#minecraft:infiniburn_overworld'
    && dv.monster_spawn_light_level.max_inclusive === 7, JSON.stringify(dv));
  result('접속·스폰 성공(레지스트리를 클라이언트가 받아들임)', bot.entity != null, '');

  cmd('op Tester'); await sleep(600);
  await chat(bot, '/gbget', 700);
  result('/gbget 바닐라 바이옴', log().some(l => /biome=minecraft:/.test(l)), JSON.stringify(log()));
  pk.length = 0;
  await chat(bot, '/gbset glow:cave', 800);
  result('setBiome 성공 + chunk_biomes 재전송', log().some(l => l.startsWith('set=true')) && pk.includes('chunk_biomes'), `${JSON.stringify(log())} ${pk}`);
  await chat(bot, '/gbget', 700);
  result('biomeAt = glow:cave', log().filter(l => l.startsWith('biome=')).pop() === 'biome=glow:cave', JSON.stringify(log()));
  await chat(bot, '/fillbiome ~-6 ~-6 ~-6 ~6 ~6 ~6 glow:plain', 800);
  await chat(bot, '/gbget', 700);
  result('/fillbiome 이 플러그인 바이옴을 받음', log().filter(l => l.startsWith('biome=')).pop() === 'biome=glow:plain', JSON.stringify(log()));
  await chat(bot, '/execute if biome ~ ~ ~ glow:plain run say BIOMEOK', 700);
  result('/execute if biome 플러그인 바이옴', bot.msgs.some(m => /BIOMEOK/.test(m.m)), '');
  await chat(bot, '/gbset glow:none', 700);
  result('모르는 바이옴은 거부', log().some(l => /err=.*IllegalArgumentException/.test(l)), JSON.stringify(log().slice(-1)));

  const fails = results.filter(r => !r[1]).length;
  console.log(`SUMMARY ${results.length - fails}/${results.length} PASS`);
  bot.end(); process.exit(fails ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
