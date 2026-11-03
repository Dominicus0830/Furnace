const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
(async () => {
  const bot = makeBot('Tester'); await spawned(bot); await sleep(1500);
  cmd('op Tester'); await sleep(800);
  const hex = bot.player.uuid.replace(/-/g, ''); const ints = [0, 1, 2, 3].map(i => (parseInt(hex.substr(i * 8, 8), 16) | 0));
  const owner = `[I;${ints.join(',')}]`;
  await chat(bot, '/gamerule doMobSpawning false'); await chat(bot, '/gamerule showDeathMessages true');
  await chat(bot, '/tp @s 0.5 -60 0.5', 500); await chat(bot, '/kill @e[type=wolf]', 500);
  const build = async (x, kind) => {
    await chat(bot, `/fill ${x} 40 10 ${x} 45 10 minecraft:stone`, 300);
    if (kind === 'ladder') await chat(bot, `/fill ${x} 40 11 ${x} 45 11 minecraft:ladder[facing=south]`, 300);
    if (kind === 'vine') await chat(bot, `/fill ${x} 40 11 ${x} 45 11 minecraft:vine[north=true]`, 300);
  };
  const cases = [[10, 'ladder', '사다리'], [14, 'vine', '덩굴'], [18, 'ladder', '부순 사다리'], [22, 'none', '일반 낙하']];
  for (const [x, kind] of cases) await build(x, kind);
  for (const [x, kind, label] of cases) {
    const t = Date.now();
    const y = kind === 'none' ? 45 : 44;
    await chat(bot, `/summon minecraft:wolf ${x}.5 ${y} 11.5 {Owner:${owner},Sitting:1b,CustomName:'"Rex${x}"'}`, 300);
    if (label === '부순 사다리') { await sleep(500); await chat(bot, `/fill ${x} 40 11 ${x} 45 11 minecraft:air`, 200); }
    const m = await waitMsg(bot, new RegExp(`^(?!Summoned).*Rex${x}`), 25000, t);
    console.log('RESULT', label, '→', m);
  }
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
