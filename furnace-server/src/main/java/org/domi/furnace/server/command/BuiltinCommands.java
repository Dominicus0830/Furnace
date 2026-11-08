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
        org.domi.furnace.server.command.builtin.AttributeCommand.register(d);
        org.domi.furnace.server.command.builtin.BossBarCommands.register(d);
        org.domi.furnace.server.command.builtin.ItemCommands.registerClear(d);
        org.domi.furnace.server.command.builtin.DamageCommand.register(d);
        org.domi.furnace.server.command.builtin.CloneCommand.register(d);
        org.domi.furnace.server.command.builtin.DataCommands.register(d);
        org.domi.furnace.server.command.builtin.DataPackCommands.registerDataPack(d);
        org.domi.furnace.server.command.builtin.DebugCommand.register(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerDefaultGameMode(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerDifficulty(d);
        org.domi.furnace.server.command.builtin.EffectCommand.register(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerMe(d);
        org.domi.furnace.server.command.builtin.ItemCommands.registerEnchant(d);
        org.domi.furnace.server.command.builtin.ExecuteCommands.register(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerExperience(d);
        org.domi.furnace.server.command.builtin.BlockCommands.registerFill(d);
        org.domi.furnace.server.command.builtin.FillBiomeCommand.register(d);
        org.domi.furnace.server.command.builtin.ForceLoadCommand.register(d);
        org.domi.furnace.server.command.builtin.FunctionCommands.registerFunction(d);
        org.domi.furnace.server.command.builtin.FunctionCommands.registerExecuteBranch(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerGameMode(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerGameRule(d);
        org.domi.furnace.server.command.builtin.ItemCommands.registerGive(d);
        ServerCommands.registerHelp(d);
        org.domi.furnace.server.command.builtin.ItemSlotCommands.register(d);
        ServerCommands.registerKick(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerKill(d);
        ServerCommands.registerList(d);
        org.domi.furnace.server.command.builtin.LocateCommand.register(d);
        org.domi.furnace.server.command.builtin.LootCommand.register(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerMsg(d);
        org.domi.furnace.server.command.builtin.ParticleCommand.register(d);
        org.domi.furnace.server.command.builtin.PlaceCommand.register(d);
        org.domi.furnace.server.command.builtin.SoundCommands.registerPlaySound(d);
        org.domi.furnace.server.command.builtin.RandomCommand.register(d);
        org.domi.furnace.server.command.builtin.DataPackCommands.registerReload(d);
        org.domi.furnace.server.command.builtin.RecipeCommands.register(d);
        org.domi.furnace.server.command.builtin.FunctionCommands.registerReturn(d);
        org.domi.furnace.server.command.builtin.RideCommand.register(d);
        org.domi.furnace.server.command.builtin.ChatCommands.registerSay(d);
        org.domi.furnace.server.command.builtin.ScheduleCommand.register(d);
        org.domi.furnace.server.command.builtin.ScoreboardCommand.register(d);
        ServerCommands.registerSeed(d);
        org.domi.furnace.server.command.builtin.BlockCommands.registerSetBlock(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerSpawnPoint(d);
        org.domi.furnace.server.command.builtin.PlayerStateCommands.registerSetWorldSpawn(d);
        org.domi.furnace.server.command.builtin.SpectateCommand.register(d);
        org.domi.furnace.server.command.builtin.SoundCommands.registerStopSound(d);
        org.domi.furnace.server.command.builtin.SpreadPlayersCommand.register(d);
        org.domi.furnace.server.command.builtin.SummonCommand.register(d);
        org.domi.furnace.server.command.builtin.TextCommands.registerTellRaw(d);
        org.domi.furnace.server.command.builtin.TagCommand.register(d);
        org.domi.furnace.server.command.builtin.TeamCommand.register(d);
        org.domi.furnace.server.command.builtin.TeamMsgCommand.register(d);
        org.domi.furnace.server.command.builtin.TeleportCommand.register(d);
        org.domi.furnace.server.command.builtin.TickCommand.register(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerTime(d);
        org.domi.furnace.server.command.builtin.TextCommands.registerTitle(d);
        org.domi.furnace.server.command.builtin.TriggerCommand.register(d);
        org.domi.furnace.server.command.builtin.WorldCommands.registerWeather(d);
        org.domi.furnace.server.command.builtin.WorldBorderCommand.register(d);
        org.domi.furnace.server.command.builtin.JfrCommand.register(d);   // JFR 을 쓸 수 있는 JVM 에서만
        org.domi.furnace.server.command.builtin.PerfCommand.register(d);
        // 전용 서버 전용 (/publish 는 통합 서버 전용이라 전용 서버 트리에 없다 — VanillaCommandTreeTest)
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
        registerNamespacedAliases(d);
    }

    /**
     * 바닐라 루트 명령마다 "minecraft:이름" 별칭을 만든다(Paper 1.21.1 Commands 생성자와 같다). 별칭은 리다이렉트 대상이 되는
     * 명령 노드(별칭의 별칭 tp→teleport 는 끝까지 따라간 teleport)를 가리키고, 실행·권한 조건은 그 노드의 것을 그대로 쓴다.
     * 같은 노드를 가리키므로 게임 규칙처럼 나중에 대상 노드의 자식이 바뀌어도 별칭이 따라간다.
     * 바닐라 명령만 대상이다 — 플러그인·Furnace 전용 명령은 "플러그인:이름" 을 따로 갖는다.
     */
    private static void registerNamespacedAliases(CommandDispatcher<CommandSource> d) {
        for (var node : new java.util.ArrayList<>(d.getRoot().getChildren())) {
            var target = node;
            while (target.getRedirect() != null) target = target.getRedirect();
            if (target == d.getRoot()) continue;   // 루트로 가는 리다이렉트는 없지만 방어
            d.register(org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder.<CommandSource>literal(Commands.VANILLA_NAMESPACE_PREFIX + node.getName())
                    .executes(target.getCommand())
                    .requires(target.getRequirement())
                    .redirect(target));
        }
    }
}
