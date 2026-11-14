// O00: 봇 하나를 접속시켜 SECS 초 동안 가만히 둔다(청크 틱 유지용). 사용: SRV=... node o00hold.js <이름> <초> [시야=10]
const mc = require('minecraft-protocol');
const [name = 'Holder', secArg = '120', vdArg = '10'] = process.argv.slice(2);
const c = mc.createClient({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.1', auth: 'offline' });
c.on('login', () => c.write('settings', { locale: 'en_us', viewDistance: +vdArg, chatFlags: 0, chatColors: true, skinParts: 127, mainHand: 1, enableTextFiltering: false, enableServerListing: true }));
c.on('position', p => { try { c.write('teleport_confirm', { teleportId: p.teleportId }); } catch (e) {} });
c.on('chunk_batch_finished', () => c.write('chunk_batch_received', { chunksPerTick: 64 }));
c.on('error', e => console.log('ERR', e.message));
setTimeout(() => { c.end(); setTimeout(() => process.exit(0), 300); }, +secArg * 1000);
