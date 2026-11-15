// Paper 1.21.1 DatapackSource: 팩이 어디서 왔는가. toString 은 이름(default, built_in, feature, world, server, Furnace 플러그인 팩은 plugin).
package io.papermc.paper.datapack;

public sealed interface DatapackSource permits DatapackSourceImpl {

    DatapackSource DEFAULT = new DatapackSourceImpl("default");
    DatapackSource BUILT_IN = new DatapackSourceImpl("built_in");
    DatapackSource FEATURE = new DatapackSourceImpl("feature");
    DatapackSource WORLD = new DatapackSourceImpl("world");
    DatapackSource SERVER = new DatapackSourceImpl("server");
}
