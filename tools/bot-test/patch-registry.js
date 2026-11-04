// Furnace 는 클라이언트가 알려진 팩(minecraft:core)을 몰라도 레지스트리 내용 없이 id 만 보낸다(실제 클라이언트는 팩을 안다).
// mineflayer 는 알려진 팩을 비워 답하므로 내용 없는 엔트리에서 죽는다 — minecraft-data 에 든 바닐라 값으로 채우도록 고친다.
const fs = require('fs');
const p = require.resolve('prismarine-registry/lib/pc/index.js');
let s = fs.readFileSync(p, 'utf8');
const old = "const entries = codec.entries.map((e, ix) => ({ id: ix, name: e.key, element: nbt.simplify(e.value) }))";
if (s.includes(old)) {
  s = s.replace(old, "const known = (staticData.loginPacket?.dimensionCodec?.[codec.id]?.entries) || []\n        const entries = codec.entries.map((e, ix) => { let v = e.value; if (!v) { const k = known.find(x => x.key === e.key); v = k?.value } return { id: ix, name: e.key, element: v ? nbt.simplify(v) : {} } })");
  fs.writeFileSync(p, s); console.log('prismarine-registry 패치 적용');
} else console.log('prismarine-registry 패치: 이미 적용됐거나 형태가 다름');
