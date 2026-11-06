// 바닐라 DirectoryLock — 월드 폴더의 session.lock 에 "☃" 를 쓰고 파일 잠금을 쥔다. 다른 프로세스가 같은 월드를 열면 실패한다.
package org.domi.furnace.server.world.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DirectoryLock implements AutoCloseable {

    public static final String LOCK_FILE = "session.lock";
    private static final ByteBuffer DUMMY;

    static {
        byte[] b = "☃".getBytes(StandardCharsets.UTF_8);
        DUMMY = ByteBuffer.allocateDirect(b.length);
        DUMMY.put(b).flip();
    }

    private final FileChannel channel;
    private final FileLock lock;

    private DirectoryLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static DirectoryLock create(Path dir) throws IOException {
        Path file = dir.resolve(LOCK_FILE);
        Files.createDirectories(dir);
        FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            ch.write(DUMMY.duplicate());
            ch.force(true);
            FileLock l;
            try {
                l = ch.tryLock();
            } catch (java.nio.channels.OverlappingFileLockException e) {
                l = null;
            }
            if (l == null) throw new LockException(file + ": already locked (possibly by other Minecraft instance?)");
            return new DirectoryLock(ch, l);
        } catch (IOException e) {
            try { ch.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    public boolean isValid() { return lock.isValid(); }

    @Override
    public void close() throws IOException {
        try {
            if (channel.isOpen()) lock.release();
        } finally {
            channel.close();
        }
    }

    public static final class LockException extends IOException {
        LockException(String message) { super(message); }
    }
}
