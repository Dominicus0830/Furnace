// 최소 RCON 클라이언트
const net = require('net');
function pkt(id, type, body) { const b = Buffer.from(body, 'utf8'); const buf = Buffer.alloc(14 + b.length); buf.writeInt32LE(10 + b.length, 0); buf.writeInt32LE(id, 4); buf.writeInt32LE(type, 8); b.copy(buf, 12); return buf; }
function connect(pass, port = 25575) {
  return new Promise((res) => {
    const s = net.connect(port, '127.0.0.1'); const st = { s, closed: false, authed: null, data: [] };
    s.on('data', d => { st.data.push(d); if (st.authed === null && d.length >= 12) st.authed = d.readInt32LE(4) !== -1; });
    s.on('close', () => { st.closed = true; }); s.on('error', () => { st.closed = true; });
    s.on('connect', () => { s.write(pkt(1, 3, pass)); });
    setTimeout(() => res(st), 1500);
  });
}
function command(st, c) { return new Promise(r => { const n = st.data.length; st.s.write(pkt(2, 2, c)); setTimeout(() => r(Buffer.concat(st.data.slice(n)).slice(12).toString('utf8').replace(/\0/g, '')), 800); }); }
module.exports = { connect, command };
