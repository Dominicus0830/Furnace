const { start, run, sleep, cmd } = require('./h');
const { makeBot, spawned } = require('../lib');
(async () => {
  const bot = makeBot('Tester', { plugins: { resource_pack: false } });
  let pk;
  bot._client.on('declare_commands', p => { pk = p; });
  await spawned(bot);
  await sleep(500);
  const nodes = pk.nodes;
  const out = [];
  function walk(i, path, seen) {
    const n = nodes[i];
    const name = n.extraNodeData?.name;
    const p = name ? path + ' ' + name : path;
    const st = n.extraNodeData?.suggestionType;
    if (st) out.push(p + ' :: ' + st + ' [' + (n.extraNodeData.parser) + ']');
    if (seen.has(i)) return; seen.add(i);
    for (const c of n.children) walk(c, p, new Set(seen));
    if (n.redirectNode !== undefined && n.redirectNode !== null && n.flags.has_redirect) {}
  }
  walk(0, '', new Set());
  console.log(out.join('\n'));
  bot.end(); await sleep(200); process.exit(0);
})();
