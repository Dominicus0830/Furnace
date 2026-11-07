// 텍스트 안의 {"selector"}·{"score"}·{"nbt"} 를 일반 엔티티(region)까지 풀어 쓰는지(바닐라 ComponentUtils.updateForEntity),
// 그 위에 얹은 /say·/me·/bossbar 이름, 그리고 이번에 채운 자동완성(보스바 id·소리·/tag remove)과 효과 오류 메시지.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ParseResults;
import org.domi.furnace.server.command.brigadier.Suggestion;
import org.domi.furnace.server.command.exec.DeferredResultTest;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextResolveCommandsTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Cow[] cows;
    final List<String> console = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new ChunkPos(0, 0));
        cows = DeferredResultTest.spawnCows(core, 2);
        core.addConsoleListener(t -> console.add(t.getString()));
        core.world().submitAtChunk(new ChunkPos(0, 0), r -> {
            cows[0].addTag("a");
            cows[1].addTag("b");
            return null;
        }).orTimeout(10, TimeUnit.SECONDS).join();
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private List<String> run(String command) {
        console.clear();
        core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
        return List.copyOf(console);
    }

    private Text resolve(Text t) {
        return ComponentUtils.resolve(CommandSource.console(core), t, null).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private static Text selector(String pattern) { return Text.of(new Content.Selector(pattern, null), org.domi.furnace.api.text.Style.empty(), List.of()); }

    // ------------------------------------------------------------------ 선택자

    @Test
    void selectorTextNamesNonPlayerEntities() {
        assertEquals("Cow", resolve(selector("@e[tag=a]")).getString());
        assertEquals("Cow, Cow", resolve(selector("@e[type=cow]")).getString(), "회색 \", \" 로 이은 이름들");
        assertEquals("", resolve(selector("@e[tag=zzz]")).getString());
        assertEquals("", resolve(selector("@e[")).getString(), "선택자 문법 오류는 빈 텍스트");
    }

    @Test
    void selectorTextSeparatorIsResolvedToo() {
        Text sep = Text.text(" | ");
        Text t = Text.of(new Content.Selector("@e[type=cow]", sep), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("Cow | Cow", resolve(t).getString());
    }

    @Test
    void selectorInsideTranslationArgumentsAndSiblingsAndHover() {
        Text t = Text.translatable("commands.kill.success.single", selector("@e[tag=b]"));
        assertEquals("Killed Cow", resolve(t).getString());
        Text sib = Text.text("x ").append(selector("@e[tag=a]"));
        assertEquals("x Cow", resolve(sib).getString());
    }

    @Test
    void sayAndMeExpandEntitySelectorsInTheMessage() {
        assertTrue(run("say hi @e[tag=a]").get(0).endsWith("[Server] hi Cow"));
        assertTrue(run("say @e[type=cow]").get(0).endsWith("[Server] Cow, Cow"));
    }

    // ------------------------------------------------------------------ 점수

    @Test
    void scoreTextReadsHolderNameSelectorAndWildcard() {
        run("scoreboard objectives add pts dummy");
        run("scoreboard players set Foo pts 5");
        run("scoreboard players set @e[tag=a] pts 9");
        assertEquals("5", resolve(Text.of(new Content.Score("Foo", "pts"), org.domi.furnace.api.text.Style.empty(), List.of())).getString());
        assertEquals("9", resolve(Text.of(new Content.Score("@e[tag=a]", "pts"), org.domi.furnace.api.text.Style.empty(), List.of())).getString());
        assertEquals("", resolve(Text.of(new Content.Score("Foo", "nope"), org.domi.furnace.api.text.Style.empty(), List.of())).getString(), "없는 목표");
        assertEquals("", resolve(Text.of(new Content.Score("Nobody", "pts"), org.domi.furnace.api.text.Style.empty(), List.of())).getString(), "점수 없음");
        // 선택자가 아무도 못 찾으면 이름 글자 그대로 점수를 찾는다(바닐라 forNameOnly)
        assertEquals("", resolve(Text.of(new Content.Score("@e[tag=zzz]", "pts"), org.domi.furnace.api.text.Style.empty(), List.of())).getString());
        var ex = assertThrows(java.util.concurrent.CompletionException.class,
                () -> resolve(Text.of(new Content.Score("@e[type=cow]", "pts"), org.domi.furnace.api.text.Style.empty(), List.of())));
        assertInstanceOf(CommandSyntaxException.class, ex.getCause());
    }

    // ------------------------------------------------------------------ NBT

    @Test
    void nbtTextReadsStorageEntityAndInterprets() {
        run("data modify storage t:t x set value 3");
        Text storage = Text.of(new Content.Nbt("x", false, null, new Content.NbtSource.Storage("t:t")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("3", resolve(storage).getString());
        run("data modify storage t:t msg set value {text:\"hello\",color:\"red\"}");
        Text interp = Text.of(new Content.Nbt("msg", true, null, new Content.NbtSource.Storage("t:t")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("hello", resolve(interp).getString());
        Text entity = Text.of(new Content.Nbt("Tags", false, null, new Content.NbtSource.Entity("@e[tag=a]")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("[\"a\"]", resolve(entity).getString(), "일반 엔티티의 NBT 는 주인 region 에서 읽는다");
        Text none = Text.of(new Content.Nbt("Tags", false, null, new Content.NbtSource.Entity("@e[tag=zzz]")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("", resolve(none).getString());
        Text badPath = Text.of(new Content.Nbt(".", false, null, new Content.NbtSource.Storage("t:t")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("", resolve(badPath).getString(), "경로 문법 오류는 빈 텍스트");
        Text missing = Text.of(new Content.Nbt("zzz", false, null, new Content.NbtSource.Storage("t:t")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("", resolve(missing).getString());
    }

    @Test
    void nbtTextJoinsSeveralMatchesWithTheSeparator() {
        run("data modify storage t:t l set value [1,2,3]");
        Text t = Text.of(new Content.Nbt("l[]", false, Text.text(";"), new Content.NbtSource.Storage("t:t")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("1;2;3", resolve(t).getString());
        Text dflt = Text.of(new Content.Nbt("l[]", false, null, new Content.NbtSource.Storage("t:t")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("1,2,3", resolve(dflt).getString(), "구분자 없으면 공백 없는 \",\" (바닐라 NbtContents)");
    }

    // ------------------------------------------------------------------ 스타일 병합·NBT 글자

    @Test
    void resolvedTextKeepsItsOwnStyleAndTheComponentStyleOverlaysIt() {
        run("scoreboard objectives add pts dummy");
        run("scoreboard objectives modify pts numberformat styled {\"color\":\"red\"}");
        run("scoreboard players set Foo pts 7");
        Text score = Text.of(new Content.Score("Foo", "pts"), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals(org.domi.furnace.api.text.NamedTextColor.RED, resolve(score).color(), "점수의 숫자 형식 색은 남는다");
        Text blue = Text.of(new Content.Score("Foo", "pts"), org.domi.furnace.api.text.Style.style(org.domi.furnace.api.text.NamedTextColor.BLUE), List.of());
        assertEquals(org.domi.furnace.api.text.NamedTextColor.BLUE, resolve(blue).color(), "글 자신의 스타일이 덮는다");
    }

    @Test
    void nbtStringTagInterpretsAsJsonAndPlainShowsRawString() {
        run("data modify storage t:t s set value '{\"text\":\"Bob\",\"color\":\"gold\"}'");
        Text interp = Text.of(new Content.Nbt("s", true, null, new Content.NbtSource.Storage("t:t")), org.domi.furnace.api.text.Style.empty(), List.of());
        Text r = resolve(interp);
        assertEquals("Bob", r.getString());
        Text plain = Text.of(new Content.Nbt("s", false, null, new Content.NbtSource.Storage("t:t")), org.domi.furnace.api.text.Style.empty(), List.of());
        assertEquals("{\"text\":\"Bob\",\"color\":\"gold\"}", resolve(plain).getString(), "문자열 태그는 따옴표·색 없이 값 그대로");
    }

    // ------------------------------------------------------------------ 보스바 이름

    @Test
    void bossbarNameResolvesEntitySelector() {
        List<String> out = run("bossbar add t:bar {\"selector\":\"@e[tag=b]\"}");
        assertEquals(List.of("Created custom bossbar [Cow]"), out);
        assertEquals("Cow", core.customBossEvents().get(org.domi.furnace.server.data.ResourceId.parse("t:bar")).name().getString());
        assertEquals(List.of("Custom bossbar [n:5] has been renamed"),
                run("bossbar set t:bar name [\"n:\",{\"score\":{\"name\":\"*\",\"objective\":\"nope\"}},\"5\"]"));
    }

    // ------------------------------------------------------------------ 자동완성

    private List<String> complete(String input) {
        ParseResults<CommandSource> parse = core.commands().getDispatcher().parse(input, CommandSource.console(core));
        List<String> out = new ArrayList<>();
        for (Suggestion s : core.commands().getDispatcher().getCompletionSuggestions(parse).orTimeout(10, TimeUnit.SECONDS).join().getList()) out.add(s.getText());
        return out;
    }

    @Test
    void bossbarIdsAreSuggested() {
        run("bossbar add t:bar one");
        assertTrue(complete("bossbar get ").contains("t:bar"));
        assertTrue(complete("bossbar set t:b").contains("t:bar"));
        assertTrue(complete("bossbar remove ").contains("t:bar"));
        assertTrue(complete("execute store result bossbar ").contains("t:bar"));
    }

    @Test
    void soundNamesAreSuggestedAndTheTreeNamesTheProvider() {
        List<String> sounds = complete("playsound minecraft:block.note_block.b");
        assertTrue(sounds.contains("minecraft:block.note_block.bass"), sounds.toString());
        assertTrue(complete("stopsound @a master minecraft:entity.pig.a").contains("minecraft:entity.pig.ambient"));
        assertEquals("minecraft:available_sounds", org.domi.furnace.server.command.builtin.SoundCommands.AVAILABLE_SOUNDS.id());
    }

    @Test
    void tagRemoveSuggestsTagsOfNonPlayerEntities() {
        assertEquals(List.of("a"), complete("tag @e[tag=a] remove "));
        assertEquals(List.of("a", "b"), complete("tag @e[type=cow] remove ").stream().sorted().toList());
        assertEquals(List.of(), complete("tag @e[type=cow] remove a"), "입력과 같은 후보는 제안하지 않는다(brigadier)");
    }

    // ------------------------------------------------------------------ 오류 메시지

    @Test
    void unknownEffectUsesTheVanillaResourceError() {
        List<String> out = run("effect give @e[type=cow] foo");
        assertEquals(1, out.size(), out.toString());
        assertEquals("Can't find element 'minecraft:foo' of type 'minecraft:mob_effect'", out.get(0));
    }

    @Test
    void invalidComponentJsonShowsTheGsonMessage() {
        List<String> out = run("tellraw @a {\"text\":");
        assertTrue(out.get(0).startsWith("Invalid chat component: End of input at line 1 column 9 path $.text"), out.get(0));
    }
}
