# 9차 봇 시험 (2026-11-06, 구조물 블록·직소 블록)

상위 README 대로 서버를 띄운 뒤 이 폴더에서 `SRV=$D node structure.js <flat 1 | flat 2 | noise>`.

봇이 편집 패킷(`update_structure_block`·`update_jigsaw_block`·`generate_structure`)을 직접 보낸다. 값은 `/execute if data block … {…}` 과 `execute if items` 의 통과 여부로 확인한다(`/data get` 출력은 잘려 읽기 어렵다).

- `flat 1` (평지): 편집 → 블록 엔티티 갱신 패킷 → 저장 파일(`generated/bot/structures/house.nbt`) → 같은 크기 불러오기(돌·판자·계단·구조 허공·상자 다이아 3) → 크기 다름(템플릿 크기를 받고 안 놓음) → 회전 → 레드스톤 불러오기 → 레드스톤 저장은 파일을 쓰지 않음 → 모서리 크기 감지 → 서바이벌·비OP 편집 무시 → 잘못된 이름 → 모드 전환 → 직소 편집·권한.
- 서버를 껐다 켠 뒤 `flat 2`: 블록 엔티티 영속, 메모리가 비어도 파일에서 불러오기, 없는 템플릿 이름.
- `noise` (`level-type=minecraft\:normal` 서버: `newsrv.sh s8n "level-type=minecraft\\:normal"` — 뒤 줄이 앞 줄을 이긴다): 직소 블록 "생성" → 직소 블록 18개(조각이 이어 붙음).

## 함정
- 숫자 태그는 형을 맞춰야 한다(`seed:5L`). 새 직소 블록의 `joint` 기본은 `rollable`.
- 봇이 접속해 있지 않으면 청크가 안 올라 있어 `/data get` 이 "That position is not loaded".
