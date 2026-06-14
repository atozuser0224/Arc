package dev.arc.client.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ArcPayload(byte[] data) implements CustomPayload {
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    public static final Id<ArcPayload> ID = new Id<>(Identifier.of("arc", "sync"));
    public static final PacketCodec<RegistryByteBuf, ArcPayload> CODEC = PacketCodec.of(
        (payload, buffer) -> buffer.writeBytes(payload.data),
        buffer -> {
            int size = buffer.readableBytes();
            if (size < 0 || size > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Arc payload exceeds limit");
            }
            byte[] data = new byte[size];
            buffer.readBytes(data);
            return new ArcPayload(data);
        }
    );

    public ArcPayload {
        if (data.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Arc payload exceeds limit");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
