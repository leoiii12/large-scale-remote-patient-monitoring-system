package com.fh;

import java.nio.ByteBuffer;
import java.util.UUID;

public class Utils {

    public static byte[] getBytesFromByteBuffer(ByteBuffer byteBuffer) {
        byteBuffer.position(0);

        return byteBuffer.array();
    }

    public static ByteBuffer getByteBufferFromUUID(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);

        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        bb.position(0);

        return bb;
    }

    public static UUID getUUIDFromBytes(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        byteBuffer.position(0);

        long high = byteBuffer.getLong();
        long low = byteBuffer.getLong();

        return new UUID(high, low);
    }

    public static UUID getUUIDFromByteBuffer(ByteBuffer byteBuffer) {
        byteBuffer.position(0);

        return getUUIDFromBytes(byteBuffer.array());
    }

}
