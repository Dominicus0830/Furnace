// M11 명령 점검 공용: op 봇 하나로 명령을 보내고 그 사이에 온 메시지를 모은다.
const { makeBot, spawned, cmd, sleep } = require('../lib');
async function start(name = 'Tester') {
  const bot = makeBot(name, { plugins: { resource_pack: false } });
  bot.removeAllListeners('messagestr');
  bot.msgs = [];
  bot.on('messagestr', (m) => bot.msgs.push(m));
  await spawned(bot);
  cmd('op ' + name);
  await sleep(800);
  return bot;
}
// 명령 하나 실행하고 결과 메시지 배열 반환
async function run(bot, line, wait = 350) {
  bot.msgs.length = 0;
  bot.chat(line);
  await sleep(wait);
  return bot.msgs.slice();
}
module.exports = { start, run, sleep, cmd };
