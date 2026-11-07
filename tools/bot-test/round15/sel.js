const { start, run, sleep, cmd } = require('./h');
(async () => {
  const bot = await start();
  const L = [];
  const t = async (line, wait) => { const r = await run(bot, line, wait); console.log(line.padEnd(70), '=>', JSON.stringify(r)); };
  await t('/gamemode creative');
  await t('/tp @s 0 -60 0');
  await t('/kill @e[type=!player]');
  await t('/scoreboard objectives add pts dummy');
  await t('/summon armor_stand 5 -60 0 {CustomName:\'"Alpha"\',Tags:["a","grp"]}');
  await t('/summon armor_stand -5 -60 0 {CustomName:\'"Beta"\',Tags:["b","grp"]}');
  await t('/summon armor_stand 0 -60 10 {Tags:["grp"]}');
  await t('/summon pig 20 -60 0');
  await t('/summon cow 0 -60 -30');
  await t('/scoreboard players set @e[tag=a] pts 5');
  await t('/scoreboard players set @e[tag=b] pts 10');
  await sleep(500);
  const q = ['@e[type=armor_stand]','@e[type=armor_stand,limit=2]','@e[type=armor_stand,limit=1,sort=furthest]','@e[type=armor_stand,sort=nearest,limit=1]',
   '@e[tag=grp,tag=!a]','@e[tag=]','@e[tag=!]','@e[name=Alpha]','@e[name=!Alpha,type=armor_stand]','@e[type=!armor_stand,type=!player]','@e[type=#minecraft:skeletons]',
   '@e[scores={pts=5..}]','@e[scores={pts=..5}]','@e[scores={pts=6}]','@e[scores={pts=5..10}]','@e[scores={pts=!}]',
   '@e[distance=..6,type=armor_stand]','@e[distance=6..,type=armor_stand]','@e[x=5,y=-60,z=0,distance=..1]','@e[dx=10,dy=2,dz=0,type=armor_stand]','@e[x=-6,y=-61,z=-1,dx=2,dy=3,dz=2]',
   '@e[y_rotation=0..90]','@e[x_rotation=-90..90,type=pig]','@e[gamemode=creative]','@a[gamemode=!survival]','@a[level=0..]','@p','@r','@a','@s','@n','@e[type=pig,nbt={}]','@e[nbt={Invulnerable:0b},type=pig]',
   '@e[predicate=minecraft:foo]','@e[limit=0]','@e[limit=-1]','@e[sort=foo]','@e[foo=1]','@e[','@e[type=','@e[type=foobar]','@e[type=player,type=pig]','@e[team=]','@e[team=!]','@e[team=nope]','@e[distance=-1]','@a[advancements={minecraft:story/root=true}]','@e[advancements={foo:bar=true}]','@x','@','@e[limit=1,limit=2]','@e[dx=1]','@e[scores={pts=1..}]','@e[sort=random,limit=2,type=armor_stand]'];
  for (const s of q) await t('/execute if entity ' + s);
  for (const s of ['@e[type=armor_stand]','@e[type=armor_stand,sort=furthest]','@e[tag=grp,limit=2]','@e[tag=grp,limit=2,sort=furthest]','@e[type=armor_stand,sort=arbitrary]']) await t('/tag ' + s + ' list');
  await t('/say @e[type=armor_stand,sort=nearest]');
  await t('/say @e[type=armor_stand,sort=furthest]');
  await t('/say @e[type=armor_stand,sort=arbitrary]');
  await t('/say @e[type=armor_stand]');
  await t('/say @e[tag=grp]');
  await t('/say @e[limit=3]');
  await t('/say @a @p @s @r @n[type=pig]');
  await t('/data get entity @e[type=pig,limit=1] Pos');
  await t('/data get entity @e[type=pig] Pos');
  await t('/data get entity @e[type=armor_stand] Pos');
  bot.end(); await sleep(200); process.exit(0);
})();
