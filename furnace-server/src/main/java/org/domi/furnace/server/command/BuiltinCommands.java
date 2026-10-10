// 기본 명령 등록(바닐라 Commands 생성자의 register 순서를 따른다 — 트리 노드 순서가 commands 패킷 순서가 된다).
package org.domi.furnace.server.command;

import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.builtin.PlayerListCommands;
import org.domi.furnace.server.command.builtin.ServerCommands;

final class BuiltinCommands {

    private BuiltinCommands() {}

    static void registerAll(CommandDispatcher<CommandSource> d) {
        // 바닐라 Commands 생성자 순서(공통 → 전용 서버 전용). 아직 없는 명령은 기능과 함께 제자리에 끼운다.
        org.domi.furnace.server.command.builtin.WorldCommands.registerDifficulty(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerMe(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerGameRule(d);
        ServerCommands.registerHelp(d);
        ServerCommands.registerKick(d);
        ServerCommands.registerList(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerMsg(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerSay(d);
        ServerCommands.registerSeed(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerTime(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerWeather(d);
        org.domi.furnace.server.command.builtin.WorldBorderCommand.register(d);
        // 전용 서버 전용
        PlayerListCommands.registerBanIp(d);
        PlayerListCommands.registerBanList(d);
        PlayerListCommands.registerBan(d);
        PlayerListCommands.registerDeop(d);
        PlayerListCommands.registerOp(d);
        PlayerListCommands.registerPardon(d);
        PlayerListCommands.registerPardonIp(d);
        ServerCommands.registerSaveAll(d);
        ServerCommands.registerSaveOff(d);
        ServerCommands.registerSaveOn(d);
        ServerCommands.registerSetIdleTimeout(d);
        ServerCommands.registerStop(d);
        PlayerListCommands.registerWhitelist(d);
    }
}
