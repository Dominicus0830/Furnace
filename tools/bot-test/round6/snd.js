// 소리 id → 이름(Furnace 내장 레지스트리 순서 = 바닐라 reports/registries.json). 패킷 holder 는 번호+1 이지만 minecraft-protocol(registryEntryHolder)이 이미 1 을 뺀 값을 준다.
const fs = require('fs'); const path = require('path');
const names = fs.readFileSync(path.join(__dirname, '../../../furnace-server/src/main/resources/registries/builtin-1.21.1.txt'), 'utf8')
  .split('\n').filter(l => l.startsWith('minecraft:sound_event ')).map(l => l.split(' ')[1]);
function soundOf(p) { const s = p.sound ?? p.soundEvent ?? p; if (s?.soundName) return s.soundName; const id = p.soundId ?? s?.soundId ?? p.sound; return typeof id === 'number' ? (names[id] ?? '#' + id) : JSON.stringify(s); }
function listen(bot, out) { for (const n of ['sound_effect', 'entity_sound_effect']) bot._client.on(n, p => out.push({ t: Date.now(), name: soundOf(p), entity: p.entityId, x: p.x, y: p.y, z: p.z })); }
module.exports = { names, soundOf, listen };
