# LuckPerms·ProtocolLib 불러오기 (2026-11-11)

LuckPerms 5.5.91 이 이제 켜지고, Vault 와 함께 `Registered Vault permission & chat hook.` 까지 나온다. 봇 접속 시험에서 권한 주고 빼기·RCON 이 됐다
(`docs/feature-parity/compat-luckperms-2026-11-11.md`). ProtocolLib 은 아직 켜지지 않는다 — 단계별 다리 계획 `docs/feature-parity/protocollib-bridge-plan-2026-11-11.md`.

## 직접 확인하면 좋은 것 (실제 클라이언트)

- LuckPerms 로 OP 아닌 플레이어에게 `luckperms.info` 를 주고 빼며 `/lp info` 결과와 탭 자동완성의 `/lp` 가 바로 바뀌는지.
- 플러그인이 `PlayerLoginEvent` 를 거부했을 때(예: LuckPerms 데이터 불러오기 실패) 클라이언트 끊김 화면의 제목·사유가 Paper 와 같은지 — Furnace 는 플레이 단계 끊기 패킷으로 보낸다.

## 정할 것 (급하지 않음)

1. **바닐라 접속 거부를 Bukkit `PlayerLoginEvent` 가 뒤집기.** 지금은 밴·화이트리스트·인원 초과로 바닐라가 막은 접속은 Bukkit 플러그인에게 오지 않는다(예약 자리·밴 우회 플러그인이 안 된다).
   추천: Bukkit `PlayerLoginEvent` 처리기가 있을 때만 바닐라 거부를 Player 생성 시점까지 미뤄 그 결과로 보이기(없으면 지금처럼 로그인 단계에서 바로 끊음).
2. **바닐라 명령의 권한 노드(`minecraft.command.*`).** Furnace 바닐라 명령은 OP 레벨만 본다. LuckPerms 로 `minecraft.command.gamemode` 를 주어도 안 된다(CraftBukkit 은 된다). 추천: 후속 작업으로 CraftBukkit 처럼 명령마다 노드를 두고 OP 레벨 기본값으로 판정.
