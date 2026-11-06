// 사용: node mkpack.js <서버 폴더> — 월드 데이터 팩 lt 를 만든다(서버 기동 전에).
const fs = require('fs'); const p = process.argv[2] + '/world/datapacks/lt';
fs.mkdirSync(p + '/data/test/loot_table', { recursive: true });
fs.writeFileSync(p + '/pack.mcmeta', JSON.stringify({ pack: { pack_format: 48, description: 'lt' } }));
fs.writeFileSync(p + '/data/test/loot_table/t.json', JSON.stringify({ pools: [{ rolls: 1, entries: [
  { type: 'minecraft:item', name: 'minecraft:stone', weight: 100 },
  { type: 'minecraft:item', name: 'minecraft:dirt', weight: 100, quality: 100 }] }] }));
// 낚시 전리품 표를 같은 표로 바꾼다(행운이 낚시에 반영되는지 보려고)
fs.mkdirSync(p + '/data/minecraft/loot_table/gameplay', { recursive: true });
fs.copyFileSync(p + '/data/test/loot_table/t.json', p + '/data/minecraft/loot_table/gameplay/fishing.json');
