// 공용: 봇 접속, 메시지 수집, 명령 실행 보조
const mineflayer = require('mineflayer');
const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const SRV = process.env.SRV;
function cmd(line) { fs.appendFileSync(SRV + '/cmds', line + '\n'); }
function makeBot(name = 'Tester', extra = {}) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.1', auth: 'offline', ...extra });
  bot.msgs = [];
  bot.on('messagestr', (m, pos) => { bot.msgs.push({ t: Date.now(), m, pos }); console.log(`[${name} ${pos}] ${m}`); });
  bot.on('actionBar', (j) => { const m = '[actionbar] ' + (j?.toString?.() ?? JSON.stringify(j)); bot.msgs.push({ t: Date.now(), m, pos: 'actionbar' }); console.log(`[${name} AB] ${m}`); });
  bot._client.on('action_bar', p => { const m = '[ab-raw] ' + JSON.stringify(p.text); bot.msgs.push({ t: Date.now(), m, pos: 'ab' }); });
  bot.on('kicked', r => console.log(`[${name} KICKED]`, JSON.stringify(r)));
  bot.on('error', e => console.log(`[${name} ERROR]`, e.message));
  bot.on('end', r => console.log(`[${name} END]`, r));
  return bot;
}
function spawned(bot, ms = 30000) {
  return new Promise((res, rej) => { const t = setTimeout(() => rej(new Error('spawn timeout')), ms); bot.once('spawn', () => { clearTimeout(t); res(); }); });
}
async function waitMsg(bot, re, ms = 5000, since = 0) {
  const end = Date.now() + ms;
  while (Date.now() < end) { const f = bot.msgs.find(x => x.t >= since && re.test(x.m)); if (f) return f.m; await sleep(100); }
  return null;
}
async function chat(bot, s, wait = 400) { bot.chat(s); await sleep(wait); }
module.exports = { makeBot, spawned, waitMsg, chat, cmd, sleep };
