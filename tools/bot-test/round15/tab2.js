const { start, run, sleep, cmd } = require('./h');
(async () => {
  const bot = await start();
  await run(bot, '/gamemode creative'); await run(bot, '/tp @s 0 -60 0');
  console.log(await run(bot, '/bossbar list'));
  console.log(await run(bot, '/bossbar add m11:bb2 "x"'));
  console.log(await run(bot, '/summon armor_stand 5 -60 0 {Tags:["tagA"]}'));
  const tc = async (s) => {
    try {
      const r = await Promise.race([bot.tabComplete(s, true, false), new Promise((_, j) => setTimeout(() => j(new Error('timeout')), 2000))]);
      const arr = r.map(x => typeof x === 'string' ? x : (x.match ?? JSON.stringify(x)));
      console.log(JSON.stringify(s).padEnd(60), '=>', arr.length > 14 ? arr.slice(0, 14).join(',') + `,...(${arr.length})` : arr.join(','));
    } catch (e) { console.log(JSON.stringify(s).padEnd(60), '=> ERR', e.message); }
  };
  for (const s of process.argv.slice(2)) await tc(s);
  bot.end(); await sleep(200); process.exit(0);
})();
