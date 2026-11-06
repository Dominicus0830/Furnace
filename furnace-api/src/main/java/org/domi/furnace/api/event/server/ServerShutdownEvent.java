// 서버 종료가 시작될 때 한 번 — 플러그인을 끄기 전이다. 종료를 시작한 스레드(종료 스레드)에서 불린다.
// 이 시점에는 모든 것이 아직 살아 있다: 접속자는 접속해 있고, region 스레드는 틱을 돌며, 스케줄러가 작업을 받는다.
// 종료 순서 계약(docs/api/lifecycle.md):
//   1. ServerShutdownEvent  2. 플러그인 끄기(켠 순서의 반대, onDisable — region·접속자·스케줄러 그대로)
//   3. 원격 서비스(RCON·Query) 닫기  4. 네트워크 정지·접속자 내보내기  5. region 의 청크·플레이어 저장
//   6. 저장소 닫기·level.dat  7. 스케줄러(region 스레드) 정지  8. 플러그인 클래스로더 닫기
// region 정지는 반드시 모든 플러그인의 onDisable 이 끝난 뒤다 — onDisable 에서 region 작업을 맡기고 기다려도 된다.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.event.Event;

public final class ServerShutdownEvent extends Event {}
