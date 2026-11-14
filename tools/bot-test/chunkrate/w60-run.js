#!/usr/bin/env node
// 다인원 새 땅 탐험 한 판: 새 월드 → 서버 시작(+표본 추출기) → 봇 w60.js → 서버 종료 → 결과 한 줄을 runs/w60.jsonl 에 덧붙인다.
// 리눅스(클라우드)·Windows(노트북) 공용 — bash·tail·su 를 쓰지 않는다(콘솔 명령은 서버 폴더의 cmds 파일을 이 스크립트가 지켜보다 표준입력으로 넣는다).
// 사용: node w60-run.js --tag <꼬리표> --jar <서버 jar> [--layout lines|radial] [--n 60] [--speed 5.6] [--secs 90] [--warm 20] [--vd 10]
//         [--ymode ground|fixed] [--y 200] [--xmx 8G] [--props "a=b;c=d"] [--fprops "k=v;k2=v2"] [--from <이전 판 이름>] [--person] -- [JVM 옵션...]
// 환경: W60_ROOT(작업 폴더, 기본 <임시폴더>/w60) · VANILLA_DATA(data/·version.json 이 든 바닐라 데이터 폴더, 필수) · RUNAS(리눅스 root 일 때 서버를 돌릴 일반 사용자, 기본 w60 가 있으면 w60)
// --from: 이전 판의 월드를 복사해 시작(이미 만든 땅 장면, 미리 생성 효과 비교). 없으면 새 월드(시드 20261112).
const fs = require('fs'), path = require('path'), os = require('os'), cp = require('child_process');
const argv = process.argv.slice(2), opt = { layout: 'lines', n: 60, speed: 5.6, secs: 90, warm: 20, vd: 10, ymode: 'ground', y: 200, xmx: '8G', props: '', fprops: '' };
let jvm = [];
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === '--') { jvm = argv.slice(i + 1); break; }
  if (argv[i] === '--person') { opt.person = true; continue; }
  const k = argv[i].replace(/^--/, ''); if (!(k in opt) && !['tag', 'jar', 'from'].includes(k)) { console.error('모르는 옵션 ' + argv[i]); process.exit(2); }
  opt[k] = argv[++i];
}
if (!opt.tag || !opt.jar) { console.error('--tag 와 --jar 가 필요합니다(파일 맨 위 설명 참고)'); process.exit(2); }
const VD = process.env.VANILLA_DATA; if (!VD || !fs.existsSync(path.join(VD, 'version.json'))) { console.error('VANILLA_DATA 에 바닐라 데이터 폴더(data/, version.json)를 지정하세요'); process.exit(2); }
const ROOT = process.env.W60_ROOT || path.join(os.tmpdir(), 'w60'), RUNS = path.join(ROOT, 'runs'), HERE = __dirname;
const D = path.join(RUNS, 'w' + opt.tag), OUT = path.join(RUNS, 'w60.jsonl');
fs.mkdirSync(RUNS, { recursive: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const PY = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');

// root 로는 서버를 돌리지 않는다(ThreadPriorityPolicy·측정 일관성). 리눅스 root 면 일반 사용자로.
let uid, gid;
if (process.platform !== 'win32' && process.getuid && process.getuid() === 0) {
  const user = process.env.RUNAS || 'w60';
  try { uid = +cp.execSync(`id -u ${user}`).toString(); gid = +cp.execSync(`id -g ${user}`).toString(); }
  catch (e) { console.error(`root 로 실행 중입니다: useradd -m ${user} 로 사용자를 만들거나 RUNAS=<사용자> 를 지정하세요`); process.exit(2); }
}
const chown = p => { if (uid !== undefined && !fs.lstatSync(p).isSymbolicLink()) { try { fs.chownSync(p, uid, gid); } catch (e) {} } };
const chownR = p => { chown(p); if (fs.statSync(p).isDirectory() && !fs.lstatSync(p).isSymbolicLink()) for (const f of fs.readdirSync(p)) chownR(path.join(p, f)); };

// 1) 월드 폴더
if (opt.from) {
  fs.rmSync(D, { recursive: true, force: true }); fs.cpSync(path.join(RUNS, 'w' + opt.from), D, { recursive: true, verbatimSymlinks: true });
  for (const f of fs.readdirSync(D)) if (/^(server-.*\.log|samples-.*\.csv|startup\.txt|bot\.err|pid)$/.test(f)) fs.rmSync(path.join(D, f));
} else {
  fs.rmSync(D, { recursive: true, force: true }); fs.mkdirSync(path.join(D, 'cache'), { recursive: true });
  try { fs.symlinkSync(path.resolve(VD), path.join(D, 'cache', 'vanilla-data-1.21.1'), 'junction'); }   // Windows 는 junction(관리자 권한 불필요)
  catch (e) { fs.cpSync(VD, path.join(D, 'cache', 'vanilla-data-1.21.1'), { recursive: true }); }
  const sp = ['online-mode=false', 'spawn-protection=0', 'level-seed=20261112', 'max-players=100', `view-distance=${opt.vd}`, `simulation-distance=${opt.vd}`, ...opt.props.split(';').filter(Boolean)];
  fs.writeFileSync(path.join(D, 'server.properties'), sp.join('\n') + '\n');
  fs.writeFileSync(path.join(D, 'furnace.properties'), ['metrics.prometheus.enabled=true', ...opt.fprops.split(';').filter(Boolean)].join('\n') + '\n');
  fs.writeFileSync(path.join(D, 'eula.txt'), 'eula=true\n');
}
fs.writeFileSync(path.join(D, 'cmds'), '');
chownR(D);

// 2) 서버 시작
const k = fs.readdirSync(D).filter(f => /^server-\d+\.log$/.test(f)).length;
const logPath = path.join(D, `server-${k}.log`), logFd = fs.openSync(logPath, 'a'); chown(logPath);
const jvmArgs = [`-Xmx${opt.xmx}`, `-Dfurnace.w60=${opt.tag}`, '-Dfurnace.tickstats=true', '-Dfurnace.chunktrace=true', ...jvm, '-jar', path.resolve(opt.jar), '--nogui'];
const t0 = Date.now();
const srv = cp.spawn('java', jvmArgs, { cwd: D, stdio: ['pipe', logFd, logFd], uid, gid });
srv.on('error', e => { console.error('java 실행 실패: ' + e.message); process.exit(1); });
srv.stdin.on('error', () => {});   // 서버가 먼저 죽어도 러너가 EPIPE 로 죽지 않게
// cmds 파일 지켜보기: 봇이 한 줄씩 덧붙이면 표준입력으로 넣는다.
let cmdPos = 0;
const pump = () => { try { const b = fs.readFileSync(path.join(D, 'cmds')); if (b.length > cmdPos) { srv.stdin.write(b.subarray(cmdPos)); cmdPos = b.length; } } catch (e) {} };
const pumpTimer = setInterval(pump, 50);
const logText = () => { try { return fs.readFileSync(logPath, 'utf8'); } catch (e) { return ''; } };

(async () => {
  let up = false;
  for (let i = 0; i < 900 && srv.exitCode === null; i++) { if (/Done \(/.test(logText())) { up = true; break; } await sleep(200); }
  if (!up) { console.error('서버가 안 떴습니다: ' + logPath); clearInterval(pumpTimer); srv.kill(); process.exit(1); }
  const done = (logText().match(/Done \([^)]*\)/) || [''])[0];
  fs.appendFileSync(path.join(D, 'startup.txt'), `k=${k} wall_to_done_s=${((Date.now() - t0) / 1000).toFixed(1)} ${done}\n`);
  // 표본 추출기(CPU·Prometheus 메트릭 1초 간격). 파이썬이 없으면 건너뛴다(sum 은 표본 없이 봇·서버 로그 항목만 낸다).
  const sam = cp.spawn(PY, [path.join(HERE, 'w60-sampler.py'), String(srv.pid), path.join(D, `samples-${k}.csv`)], { stdio: 'ignore', uid, gid });
  sam.on('error', () => console.error('경고: 표본 추출기(python) 를 못 띄움 — CPU·TPS 표본 없음'));
  await sleep(3000);
  const hdr = `== tag=${opt.tag} jar=${path.basename(opt.jar, '.jar')} layout=${opt.layout} n=${opt.n} speed=${opt.speed} world=w${opt.tag} ymode=${opt.ymode} opts=${jvm.join('+')} t=${Math.floor(Date.now() / 1000)}`;
  fs.appendFileSync(OUT, hdr + '\n');
  // 3) 봇
  const env = { ...process.env, SRV: D, XOFF: process.env.XOFF || '200000', NAMEP: opt.tag.slice(0, 6) + 'x', YMODE: opt.ymode, Y: String(opt.y), PERSON: opt.person ? '1' : '' };
  const bot = cp.spawn('node', [path.join(HERE, 'w60.js'), opt.n, opt.vd, opt.speed, opt.secs, opt.layout, opt.warm], { cwd: HERE, env, stdio: ['ignore', 'pipe', fs.openSync(path.join(D, 'bot.err'), 'a')] });
  let outBuf = ''; bot.stdout.on('data', d => { outBuf += d; });
  const killT = setTimeout(() => bot.kill(), (+opt.secs + +opt.warm + 150) * 1000);
  await new Promise(r => bot.on('close', r)); clearTimeout(killT);
  const line = outBuf.split('\n').find(l => l.startsWith('{'));
  if (line) { fs.appendFileSync(OUT, line + '\n'); console.log(line); } else console.error('봇이 결과를 못 냈습니다 — ' + path.join(D, 'bot.err'));
  // 4) 종료
  fs.appendFileSync(path.join(D, 'cmds'), 'stop\n'); pump(); await sleep(100); pump();
  const exited = new Promise(r => srv.on('exit', r));
  await Promise.race([exited, sleep(180000).then(() => { console.error('강제 종료'); srv.kill('SIGKILL'); })]);
  clearInterval(pumpTimer); sam.kill();
  process.exit(line ? 0 : 1);
})();
