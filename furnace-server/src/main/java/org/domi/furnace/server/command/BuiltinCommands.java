// 기본 명령 등록(바닐라 Commands 생성자의 register 순서를 따른다 — 트리 노드 순서가 commands 패킷 순서가 된다).
package org.domi.furnace.server.command;

import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.builtin.PlayerListCommands;
import org.domi.furnace.server.command.builtin.ServerCommands;

final class BuiltinCommands {

    private BuiltinCommands() {}

    static void registerAll(CommandDispatcher<CommandSource> d) {
        // 바닐라 Commands 생성자 순서(공통 → 전용 서버 전용). 아직 없는 명령은 기능과 함께 제자리에 끼운다.
        org.domi.furnace.server.command.builtin.AdvancementCommands.register(d);
        org.domi.furnace.server.command.builtin.BossBarCommands.register(d);
        org.domi.furnace.server.command.builtin.ItemCommands.registerClear(d);
        org.domi.furnace.server.command.builtin.DamageCommand.register(d);
        org.domi.furnace.server.command.builtin.DataPackCommands.registerDataPack(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerDefaultGameMode(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerDifficulty(d);
        org.domi.furnace.server.command.builtin.EffectCommand.register(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerMe(d);
        org.domi.furnace.server.command.builtin.ItemCommands.registerEnchant(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerExperience(d);
        org.domi.furnace.server.command.builtin.BlockCommands.registerFill(d);
        org.domi.furnace.server.command.builtin.ForceLoadCommand.register(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerGameMode(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerGameRule(d);
        org.domi.furnace.server.command.builtin.ItemCommands.registerGive(d);
        ServerCommands.registerHelp(d);
        ServerCommands.registerKick(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerKill(d);
        ServerCommands.registerList(d);
        org.domi.furnace.server.command.builtin.LocateCommand.register(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerMsg(d);
        org.domi.furnace.server.command.builtin.ParticleCommand.register(d);
        org.domi.furnace.server.command.builtin.SoundCommands.registerPlaySound(d);
        org.domi.furnace.server.command.builtin.RandomCommand.register(d);
        org.domi.furnace.server.command.builtin.DataPackCommands.registerReload(d);
        org.domi.furnace.server.command.builtin.RecipeCommands.register(d);
        org.domi.furnace.server.command.builtin.RideCommand.register(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerSay(d);
        org.domi.furnace.server.command.builtin.ScoreboardCommand.register(d);
        ServerCommands.registerSeed(d);
        org.domi.furnace.server.command.builtin.BlockCommands.registerSetBlock(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerSpawnPoint(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerSetWorldSpawn(d);
        org.domi.furnace.server.command.builtin.SoundCommands.registerStopSound(d);
        org.domi.furnace.server.command.builtin.SpreadPlayersCommand.register(d);
        org.domi.furnace.server.command.builtin.SummonCommand.register(d);
        org.domi.furnace.server.command.builtin.TextCommands.registerTellRaw(d);
        org.domi.furnace.server.command.builtin.TagCommand.register(d);
        org.domi.furnace.server.command.builtin.TeamCommand.register(d);
        org.domi.furnace.server.command.builtin.TeleportCommand.register(d);
        org.domi.furnace.server.command.builtin.TickCommand.register(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerTime(d);
        org.domi.furnace.server.command.builtin.TextCommands.registerTitle(d);
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
        org.domi.furnace.server.command.builtin.TransferCommand.register(d);
        PlayerListCommands.registerWhitelist(d);
    }
}
