// 최소 UDP Query 클라이언트(basic stat)
const dgram = require('dgram');
module.exports = function query(port = 25565) {
  return new Promise((res) => {
    const s = dgram.createSocket('udp4'); const sid = 0x01020304 & 0x0F0F0F0F; let step = 0;
    const done = v => { try { s.close(); } catch {} res(v); };
    setTimeout(() => done({ error: 'timeout', step }), 3000);
    s.on('message', m => {
      if (step === 0) { const tok = parseInt(m.slice(5).toString().replace(/\0/g, ''), 10); step = 1;
        const b = Buffer.alloc(11); b.writeUInt16BE(0xFEFD, 0); b[2] = 0; b.writeInt32BE(sid, 3); b.writeInt32BE(tok, 7); s.send(b, port, '127.0.0.1'); }
      else done({ fields: m.slice(5).toString('latin1').split('\0').filter(x => x.length) });
    });
    const h = Buffer.alloc(7); h.writeUInt16BE(0xFEFD, 0); h[2] = 9; h.writeInt32BE(sid, 3); s.send(h, port, '127.0.0.1');
  });
};
