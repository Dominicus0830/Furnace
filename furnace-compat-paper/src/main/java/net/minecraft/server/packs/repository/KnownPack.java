package net.minecraft.server.packs.repository;

/** 클라이언트와 서로 아는 팩(바닐라 record). STREAM_CODEC 은 없다. */
public record KnownPack(String namespace, String id, String version) {
    public static final String VANILLA_NAMESPACE = "minecraft";

    public static KnownPack vanilla(String id) { return new KnownPack(VANILLA_NAMESPACE, id, net.minecraft.SharedConstants.VERSION_STRING); }

    public boolean isVanilla() { return this.namespace.equals(VANILLA_NAMESPACE); }

    @Override
    public String toString() { return this.namespace + ":" + this.id + ":" + this.version; }
}
