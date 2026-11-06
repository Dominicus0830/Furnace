// U2: 나뭇잎 상자(속 빈 7x4x7)에 가둔 파괴수가 20초 동안 부순 나뭇잎 수
const U = require('../round2/u');
const { sleep, near } = U;
const V = require('vec3');
(async () => {
  const bot = await U.setup('Rav');
  bot.chat('/difficulty easy'); bot.chat('/gamerule mobGriefing true'); bot.chat('/kill @e[type=!player]'); await sleep(400);
  bot.chat('/tp @s 73.5 -60 10.5 180 0'); await sleep(1200); bot.chat('/gamemode survival'); bot.chat('/effect give @s resistance 600 4 true'); bot.chat('/effect give @s regeneration 600 4 true');
  for (const leaf of process.env.LEAVES ? process.env.LEAVES.split('/') : ['oak_leaves', 'oak_leaves[persistent=false]', 'spruce_leaves', 'birch_leaves', 'mangrove_leaves', 'cherry_leaves', 'azalea_leaves']) {
    bot.chat('/kill @e[type=ravager]'); bot.chat('/fill 69 -60 -4 77 -56 4 air'); await sleep(300);
    bot.chat(`/fill 70 -60 -3 76 -57 3 ${leaf.includes('[') ? leaf : leaf + '[persistent=true]'} hollow`); await sleep(200);
    bot.chat('/fill 71 -60 -2 75 -58 2 air'); await sleep(200);
    let total = 0; for (let x = 70; x <= 76; x++) for (let y = -60; y <= -57; y++) for (let z = -3; z <= 3; z++) if (bot.blockAt(new V(x, y, z))?.name.endsWith('leaves')) total++;
    bot.chat('/summon ravager 73 -60 0'); await sleep(20000);
    let left = 0; for (let x = 70; x <= 76; x++) for (let y = -60; y <= -57; y++) for (let z = -3; z <= 3; z++) if (bot.blockAt(new V(x, y, z))?.name.endsWith('leaves')) left++;
    const rv = near(bot, 'ravager')[0];
    const inside = rv && rv.position.x > 70 && rv.position.x < 77 && rv.position.z > -3 && rv.position.z < 4;
    console.log(`RESULT 파괴수 ${leaf}: 20초에 ${total - left}/${total} 개 부숨, 파괴수 ${inside ? '상자 안' : '상자 밖'}`);
  }
  bot.chat('/kill @e[type=ravager]'); bot.quit(); await sleep(500); process.exit(0);
})();
