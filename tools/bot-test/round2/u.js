// 공용 보조(이번 라운드)
const L = require('../lib');
const { waitMsg, sleep } = L;
async function ask(bot, c, re = /./, ms = 3000) { const t = Date.now(); bot.chat(c); return await waitMsg(bot, re, ms, t); }
async function num(bot, sel, path) { const m = await ask(bot, `/data get entity ${sel} ${path}`, /entity data|has the following|No entity|Found no/i); const x = m && m.match(/data: (-?[\d.]+)/); return x ? parseFloat(x[1]) : m; }
const near = (bot, name, pred = () => true) => Object.values(bot.entities).filter(e => e.name === name && pred(e)).sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position));
const count = (bot, name) => bot.inventory.items().filter(i => i.name === name).reduce((a, b) => a + b.count, 0);
async function hold(bot, name) { const it = bot.inventory.items().find(i => i.name === name); if (!it) throw new Error('no ' + name); await bot.equip(it, 'hand'); await sleep(200); }
async function setup(name = 'Tester') { const bot = L.makeBot(name, { plugins: { resource_pack: false } }); await L.spawned(bot); await sleep(1500); L.cmd('op ' + name); await sleep(800); return bot; }
module.exports = { ...L, ask, num, near, count, hold, setup };
function uuidInts(u) { const h = u.replace(/-/g, ''); const a = []; for (let i = 0; i < 4; i++) a.push(parseInt(h.substr(i * 8, 8), 16) | 0); return `[I;${a.join(',')}]`; }
module.exports.uuidInts = uuidInts;
async function waitEnt(bot, name, ms = 4000) { const end = Date.now() + ms; while (Date.now() < end) { const e = Object.values(bot.entities).filter(x => x.name === name).sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0]; if (e) return e; await L.sleep(100); } return null; }
module.exports.waitEnt = waitEnt;
