// RCON 으로 들어온 명령의 발신자(이름 "Rcon", 바닐라 RconConsoleSource). 메시지는 그 RCON 요청의 응답으로 돌아간다.
package org.domi.furnace.api.command;

/** 명령 하나를 처리하는 동안만 쓰인다 — 응답을 다 돌려준 뒤 보낸 메시지는 어디에도 가지 않는다. */
public interface RemoteConsoleSender extends CommandSender {
}
