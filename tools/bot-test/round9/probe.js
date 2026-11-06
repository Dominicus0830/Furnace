const U = require('/home/claude/Furnace/tools/bot-test/round2/u.js');
(async () => {
  const bot = await U.setup('Tester');
  U.cmd('tp Tester 6.5 -60 12.5'); await U.sleep(1000);
  await U.hold(bot, 'fishing_rod'); await bot.look(0, 0.0, true);
  bot.activateItem(); 
  for (let i = 0; i < 40; i++) { await U.sleep(250); const h = Object.values(bot.entities).filter(e => /bobber|fishing/.test(e.name || '')); console.log(i, h.map(e => e.name + ' ' + e.position.toString() + ' v' + e.velocity.y.toFixed(3)).join(';')); }
  process.exit(0);
})();
