# Guava·SnakeYAML 서버 jar 포함 — 2026-11-12

결정 5 정정대로 Guava·SnakeYAML 은 서버 jar 에 넣었고(약 3.4 MB, 서버 jar 25.6 MB), Adventure·authlib 등 나머지는 지금처럼 필요할 때 받습니다. 설명: `docs/api/plugin-libraries.md`, 기록: `docs/feature-parity/context-notes/2026-11-12-bundled-guava-snakeyaml.md`.

- **직접 확인(선택):** 인터넷을 끈 서버(또는 `plugins.libraries.offline=true`)에 Guava·SnakeYAML 을 쓰는 Paper 플러그인을 넣고 켜 보세요. 로그에 `... 를 서버 jar 에서 꺼냈다` 가 나오고 플러그인이 켜지면 됩니다. 컨테이너에서 탐침 플러그인으로는 확인했습니다.
- 결정할 것 없음.
