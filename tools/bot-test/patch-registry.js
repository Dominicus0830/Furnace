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

// minecraft-data 1.21.1 의 potion_contents 에 1.21.2 에서 생긴 customName 칸이 들어 있어, 성분 있는 물약 칸을 읽다 PartialReadError 로
// 창 열기(windowOpen)까지 막힌다. 바닐라 1.21.1 PotionContents(cwu)는 potion·custom_color·custom_effects 세 칸뿐 — 그 칸을 뺀다(2026-11-06).
const pp = require.resolve('minecraft-data/minecraft-data/data/pc/1.21.1/protocol.json');
const proto = JSON.parse(fs.readFileSync(pp, 'utf8'));
let fixed = false;
(function walk(o) {
  if (!o || typeof o !== 'object') return;
  if (o.potion_contents && Array.isArray(o.potion_contents[1])) {
    const n = o.potion_contents[1].length;
    o.potion_contents[1] = o.potion_contents[1].filter(f => f.name !== 'customName');
    fixed = fixed || o.potion_contents[1].length !== n;
  }
  for (const k in o) walk(o[k]);
})(proto);
if (fixed) { fs.writeFileSync(pp, JSON.stringify(proto, null, 2)); console.log('minecraft-data potion_contents 패치 적용'); } else console.log('minecraft-data potion_contents 패치: 이미 적용됐거나 형태가 다름');
