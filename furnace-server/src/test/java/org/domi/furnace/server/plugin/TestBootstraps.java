// 부트스트랩 시험용 플러그인들 — 호출 순서를 TestPlugins.LOG 에 남긴다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.bootstrap.BootstrapContext;
import org.domi.furnace.api.plugin.bootstrap.PluginBootstrap;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEventManager;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEvents;
import org.domi.furnace.api.registry.PluginRegistry;
import org.domi.furnace.api.registry.RegistryListener;

public final class TestBootstraps {

    private TestBootstraps() {}

    /** 레지스트리에 넣는 값. */
    public record Ability(String name) {}

    /** 부트스트랩이 끝난 뒤 다시 등록해 보려고 남겨 두는 등록기. */
    public static volatile LifecycleEventManager leakedLifecycle;

    // ---------------------------------------------------------------- Core: 레지스트리를 만들고 데이터 팩을 알린다

    public static final class CoreBoot implements PluginBootstrap {
        @Override
        public void bootstrap(BootstrapContext ctx) {
            TestPlugins.LOG.add("core:bootstrap:" + (serverReady() ? "server" : "no-server"));
            PluginRegistry<Ability> r = ctx.registries().create("core:abilities", Ability.class);
            r.register("core:dash", new Ability("dash"));
            ctx.lifecycle().register(LifecycleEvents.DATAPACK_DISCOVERY, reg -> reg.discover("/datapack/", "main"));
            leakedLifecycle = ctx.lifecycle();
        }
    }

    public static final class Core extends FurnacePlugin {
        @Override
        public void onLoad() {
            PluginRegistry<Ability> r = registries().registry("core:abilities", Ability.class);
            TestPlugins.LOG.add("core:load:" + r.keys());
            r.freeze();
            r.addListener(new RegistryListener<>() {
                @Override
                public void removed(String key, Ability value) { TestPlugins.LOG.add("core:removed:" + key); }
            });
        }

        @Override
        public void onEnable() { TestPlugins.LOG.add("core:enable"); }
    }

    // ---------------------------------------------------------------- Addon: 남의 레지스트리에 넣고 메인 인스턴스를 직접 만든다

    public static final class AddonBoot implements PluginBootstrap {
        @Override
        public void bootstrap(BootstrapContext ctx) {
            TestPlugins.LOG.add("addon:bootstrap");
            ctx.registries().registry("core:abilities", Ability.class).register("addon:fly", new Ability("fly"));
        }

        @Override
        public FurnacePlugin createPlugin(BootstrapContext ctx) { return new Addon("from-bootstrap:" + ctx.description().name()); }
    }

    public static final class Addon extends FurnacePlugin {
        private final String origin;

        Addon(String origin) { this.origin = origin; }

        @Override
        public void onLoad() { TestPlugins.LOG.add("addon:load:" + origin); }
    }

    // ---------------------------------------------------------------- Bad: 등록하다가 던진다

    public static final class BadBoot implements PluginBootstrap {
        @Override
        public void bootstrap(BootstrapContext ctx) {
            ctx.registries().create("bad:things", String.class).register("bad:one", "1");
            ctx.registries().registry("core:abilities", Ability.class).register("bad:hack", new Ability("hack"));
            ctx.lifecycle().register(LifecycleEvents.DATAPACK_DISCOVERY, reg -> reg.discover("x", "x"));
            throw new IllegalStateException("부트스트랩 실패 시험");
        }
    }

    /** 부트스트랩 클래스가 PluginBootstrap 이 아니다. */
    public static final class NotABootstrap {}

    public static final class Plain extends FurnacePlugin {
        @Override
        public void onLoad() { TestPlugins.LOG.add(name().toLowerCase() + ":load"); }
    }

    private static boolean serverReady() {
        try {
            org.domi.furnace.api.Furnace.server();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
