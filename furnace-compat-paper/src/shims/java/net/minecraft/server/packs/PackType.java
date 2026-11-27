package net.minecraft.server.packs;

/** 팩 종류(Mojang 이름 shim): 클라이언트 리소스·서버 데이터. 바닐라 1.21.1 값 그대로. */
enum PackType {
    private final String directory;

    PackType(String directory) { this.directory = directory; }

    public String getDirectory() { return this.directory; }
}
