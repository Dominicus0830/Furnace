// 메뉴·보스바·사이드바 API 봇 시험 — round9/probe/UiProbe 를 넣은 서버에서. "RESULT 이름 PASS|FAIL 근거".
// SRV=<서버 폴더> node round9/ui.js
const fs = require('fs');
const { makeBot, spawned, sleep, chat } = require('../lib');
const SRV = process.env.SRV;
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
const log = () => fs.readFileSync(SRV + '/server.log').toString('utf8').split('\n').filter(l => l.includes('[UI] ')).map(l => l.slice(l.indexOf('[UI] ') + 5));

(async () => {
  const bot = makeBot('Tester', { plugins: { resource_pack: false } });
  await spawned(bot); await sleep(1500);
  const pk = [];
  bot._client.on('packet', (d, m) => { if (m.state === 'play' && /boss|score|objective/.test(m.name)) pk.push({ n: m.name, d }); });
  const take = () => pk.splice(0).map(x => x.n);

  // 보스바
  await chat(bot, '/uibar', 600);
  let got = pk.splice(0);
  const add = got.find(x => x.n === 'boss_bar');
  result('보스바 추가', !!add && add.d.action === 0 && Math.abs(add.d.health - 0.5) < 1e-6 && add.d.color === 5 && add.d.dividers === 2, JSON.stringify(add?.d));
  await chat(bot, '/uibarset', 600);
  got = pk.splice(0).filter(x => x.n === 'boss_bar').map(x => x.d.action);
  result('보스바 변경(진행도·이름·모양)', JSON.stringify(got) === JSON.stringify([2, 3, 4]), JSON.stringify(got));

  // 사이드바
  await chat(bot, '/uisb', 600);
  got = take();
  result('사이드바 보이기(목표→슬롯→점수 3)', got.join(',').replace(/,/g, ' ') === 'scoreboard_objective scoreboard_display_objective scoreboard_score scoreboard_score scoreboard_score', got.join(','));
  await chat(bot, '/uisbset', 600);
  got = take();
  result('사이드바 바뀐 것만(제목 + 줄 하나)', got.slice().sort().join(' ') === 'scoreboard_objective scoreboard_score', got.join(','));
  await chat(bot, '/uihide', 600);
  got = take();
  result('숨기기', got.includes('boss_bar') && got.includes('scoreboard_display_objective') && got.filter(n => n === 'scoreboard_objective').length === 1, got.join(','));

  // 메뉴
  let opened = null; bot.once('windowOpen', w => { opened = w; });
  await chat(bot, '/uimenu', 900);
  result('메뉴 열림(상자 3줄, 장식·버튼)', !!opened && opened.slots.length === 27 + 36 && opened.slots[13]?.name === 'diamond' && opened.slots[0]?.name === 'gray_stained_glass_pane', opened ? `${opened.type} n=${opened.slots.length} s13=${opened.slots[13]?.name} s0=${opened.slots[0]?.name}` : 'no window');
  result('onOpen 처리기', log().includes('open Tester'), JSON.stringify(log()));
  // 장식 칸 클릭 — 취소돼 커서가 비고 칸이 그대로여야 한다
  await bot.clickWindow(0, 0, 0).catch(() => {}); await sleep(700);
  const w = bot.currentWindow;
  result('장식 칸 집기 취소(칸 유지·커서 비움)', !!w && w.slots[0]?.name === 'gray_stained_glass_pane' && !w.selectedItem, w ? `s0=${w.slots[0]?.name} cursor=${w.selectedItem?.name}` : 'closed');
  // 쉬프트 클릭으로 인벤토리 칸 → 메뉴로 못 넣는다(내 인벤토리 비어 있으니 그냥 무해해야 함)
  // 에메랄드: allow
  await bot.clickWindow(15, 0, 0).catch(() => {}); await sleep(700);
  result('allow 칸은 바닐라처럼 집힘', bot.currentWindow && bot.currentWindow.selectedItem?.name === 'emerald' && log().some(l => l.startsWith('click15 allow LEFT')), `cursor=${bot.currentWindow?.selectedItem?.name} ${JSON.stringify(log())}`);
  await bot.clickWindow(15, 0, 0).catch(() => {}); await sleep(500);   // 도로 내려놓기
  // 다이아: 처리기가 메시지 + 닫기
  let closed = false; bot.once('windowClose', () => { closed = true; });
  const since = Date.now();
  await bot.clickWindow(13, 1, 0).catch(() => {}); await sleep(900);
  result('버튼 클릭: 처리기 호출·메시지·닫기', log().some(l => l.startsWith('click13 RIGHT')) && bot.msgs.some(m => m.t >= since && /BOUGHT/.test(m.m)) && closed && log().includes('close Tester'), `closed=${closed} ${JSON.stringify(log())}`);

  const fails = results.filter(r => !r[1]).length;
  console.log(`SUMMARY ${results.length - fails}/${results.length} PASS`);
  bot.end(); process.exit(fails ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
