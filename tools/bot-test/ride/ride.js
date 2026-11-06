// 탄 채 접속 종료 → 탈것 저장 시험. node ride.js mount|check  (SRV=서버 폴더)
const { makeBot, spawned, cmd, sleep } = require('../lib');
const fs = require('fs');
const SRV = process.env.SRV;
function logSince(n) { return fs.readFileSync(SRV + '/server.log', 'utf8').slice(n); }
function logLen() { return fs.readFileSync(SRV + '/server.log', 'utf8').length; }
async function consoleQ(line, re, ms = 3000) {
  const n = logLen(); cmd(line);
  const end = Date.now() + ms;
  while (Date.now() < end) { const m = logSince(n).match(re); if (m) return m[0]; await sleep(100); }
  return '(응답 없음)';
}
async function count(sel) { return consoleQ(`execute if entity ${sel}`, /Test (passed, count: \d+|failed)/); }
function watchPassengers(bot) {
  bot.rides = [];
  bot._client.on('set_passengers', p => {
    bot.rides.push(p);
    if (p.passengers.includes(bot.entity?.id)) bot.vehicleId = p.entityId;
  });
}
(async () => {
  const phase = process.argv[2];
  if (phase === 'mount') {
    console.log('접속 전 보트 수', await count('@e[type=minecraft:boat]'));
    const bot = makeBot('Tester', { plugins: { resource_pack: false } }); watchPassengers(bot);
    await spawned(bot); await sleep(1500);
    cmd('op Tester'); await sleep(500);
    const p = bot.entity.position;
    cmd(`summon minecraft:boat ${p.x.toFixed(2)} ${p.y.toFixed(2)} ${(p.z + 2).toFixed(2)} {CustomName:'"Bote"',Tags:["ridetest"],Passengers:[{id:"minecraft:chicken",Tags:["ridechick"]}]}`);
    await sleep(1500);
    const boat = Object.values(bot.entities).find(e => e.name === 'boat');
    if (!boat) { console.log('보트 못 찾음'); process.exit(1); }
    bot.mount(boat); await sleep(1500);
    console.log('탑승 여부(set_passengers 에 내 id):', bot.vehicleId === boat.id, 'boat id', boat.id);
    console.log('접속 중 보트 수', await count('@e[type=minecraft:boat]'), '닭', await count('@e[type=minecraft:chicken]'));
    if (process.argv[3] === 'stay') { console.log('접속 유지(서버 종료 시험)'); await sleep(600000); }
    bot.quit(); await sleep(1500);
    console.log('퇴장 뒤 보트 수', await count('@e[type=minecraft:boat]'), '닭', await count('@e[type=minecraft:chicken]'));
    process.exit(0);
  }
  if (phase === 'check') {
    console.log('재접속 전 보트 수', await count('@e[type=minecraft:boat]'), '닭', await count('@e[type=minecraft:chicken]'));
    const bot = makeBot('Tester', { plugins: { resource_pack: false } }); watchPassengers(bot);
    await spawned(bot); await sleep(3000);
    const v = bot.vehicleId != null ? bot.entities[bot.vehicleId] : null;
    console.log('재접속 뒤 탑승:', bot.vehicleId != null, v ? `${v.name} ${JSON.stringify(v.metadata?.[2] ?? '')}` : '');
    const vp = bot.rides.filter(r => r.entityId === bot.vehicleId).pop();
    console.log('탈것 승객 수', vp ? vp.passengers.length : 0);
    console.log('재접속 뒤 보트 수', await count('@e[type=minecraft:boat,tag=ridetest,name=Bote]'), '닭', await count('@e[type=minecraft:chicken,tag=ridechick]'));
    console.log('플레이어 탑승(콘솔):', await consoleQ('execute as Tester on vehicle run say vehicle-ok', /vehicle-ok|Test failed|\[Tester\]/, 2000));
    console.log('닭 탑승(콘솔):', await consoleQ('execute as @e[type=minecraft:chicken] on vehicle run say chick-ok', /chick-ok|Test failed/, 2000));
    if (process.argv[3] === 'stay') { console.log('접속 유지'); await sleep(600000); }
    bot.quit(); await sleep(1500);
    process.exit(0);
  }
})();
