// 메뉴·보스바·사이드바 API 봇 시험용 플러그인. 명령: /uimenu /uibar /uibarset /uisb /uisbset /uihide. 열기/닫기/클릭은 "[UI] ..." 로 로그에 남긴다.
package probe;

import java.util.List;
import org.domi.furnace.api.bossbar.BossBar;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.menu.Menu;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.scoreboard.Sidebar;
import org.domi.furnace.api.text.Text;

public final class UiProbe extends FurnacePlugin {

    private final BossBar bar = BossBar.of(Text.text("Dragon"), 0.5f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
    private final Sidebar sb = Sidebar.of(Text.text("Title"), List.of(Text.text("first"), Text.text("second"), Text.text("third")));

    @Override
    public void onEnable() {
        server().commands().register(this, "uimenu", (s, a) -> {
            Menu menu = Menu.chest(Text.text("Shop"), 3)
                    .fillEmpty(ItemStack.of("gray_stained_glass_pane"))
                    .button(13, ItemStack.of("diamond"), c -> {
                        logger().info("[UI] click13 " + c.kind() + " cursor=" + c.cursor().type());
                        c.player().sendMessage(Text.text("BOUGHT"));
                        c.close();
                    })
                    .button(15, ItemStack.of("emerald"), c -> {
                        logger().info("[UI] click15 allow " + c.kind());
                        c.allow();
                    })
                    .onOpen(p -> logger().info("[UI] open " + p.name()))
                    .onClose(p -> logger().info("[UI] close " + p.name()));
            ((Player) s).openMenu(menu);
        });
        server().commands().register(this, "uibar", (s, a) -> s.showBossBar(bar));
        server().commands().register(this, "uibarset", (s, a) -> bar.progress(0.9f).name(Text.text("Dragon2")).color(BossBar.Color.RED));
        server().commands().register(this, "uisb", (s, a) -> s.showSidebar(sb));
        server().commands().register(this, "uisbset", (s, a) -> sb.line(1, Text.text("SECOND!")).title(Text.text("Title2")));
        server().commands().register(this, "uihide", (s, a) -> {
            s.hideBossBar(bar);
            s.hideSidebar(sb);
        });
    }
}
