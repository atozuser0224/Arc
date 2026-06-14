package dev.arc.client.fabric;

import dev.arc.api.sync.ActivatePacket;
import dev.arc.api.sync.ArcSyncFeature;
import dev.arc.api.sync.ArcSyncPacket;
import dev.arc.api.sync.ArcSyncPacketCodec;
import dev.arc.api.sync.ArcSyncProtocol;
import dev.arc.api.sync.ArcSyncSession;
import dev.arc.api.sync.BlobChunkPacket;
import dev.arc.api.sync.BlobRequestPacket;
import dev.arc.api.sync.ClientHello;
import dev.arc.api.sync.ClientHelloPacket;
import dev.arc.api.sync.FailurePacket;
import dev.arc.api.sync.ServerManifestPacket;
import dev.arc.api.sync.SyncBlob;
import dev.arc.client.ArcClientRuntime;
import dev.arc.client.ClientTransferPlan;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

final class ArcClientNetwork {
    private static final int MAX_BLOB_BYTES = 64 * 1024 * 1024;

    private final ArcClientRuntime runtime;
    private ArcSyncSession transfers;
    private Set<String> remaining = Set.of();
    private String revision;

    ArcClientNetwork(ArcClientRuntime runtime) {
        this.runtime = runtime;
    }

    void register() {
        PayloadTypeRegistry.playC2S().register(ArcPayload.ID, ArcPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ArcPayload.ID, ArcPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
            ArcPayload.ID,
            (payload, context) -> receive(payload.data())
        );
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(ArcPayload.ID)) {
                send(new ClientHelloPacket(new ClientHello(
                    ArcSyncProtocol.VERSION,
                    EnumSet.allOf(ArcSyncFeature.class),
                    null,
                    Set.of()
                )));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnect());
    }

    private void receive(byte[] bytes) {
        try {
            ArcSyncPacket packet = ArcSyncPacketCodec.decode(bytes);
            if (packet instanceof ServerManifestPacket manifest) {
                begin(manifest);
            } else if (packet instanceof BlobChunkPacket chunk) {
                accept(chunk);
            } else if (packet instanceof ActivatePacket activate) {
                activate(activate);
            } else if (packet instanceof FailurePacket failure) {
                throw new IllegalStateException("ArcSync server rejected session: " + failure.getMessage());
            }
        } catch (RuntimeException exception) {
            disconnect();
            throw exception;
        }
    }

    private void begin(ServerManifestPacket packet) {
        PublicKey publicKey;
        try {
            publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(packet.getPublicKey()));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid ArcSync server key", exception);
        }
        String serverId = packet.getSignedManifest().getManifest().getServerId();
        ClientTransferPlan plan = runtime.begin(serverId, publicKey, packet.getSignedManifest());
        transfers = new ArcSyncSession(MAX_BLOB_BYTES, ArcSyncPacketCodec.MAX_CHUNK_BYTES);
        remaining = new HashSet<>(plan.getMissingHashes());
        revision = packet.getSignedManifest().getManifest().getRevision();
        for (SyncBlob blob : packet.getSignedManifest().getManifest().getBlobs()) {
            if (remaining.contains(blob.getSha256())) {
                if (blob.getSize() > MAX_BLOB_BYTES) {
                    throw new IllegalStateException("ArcSync blob exceeds client limit: " + blob.getPath());
                }
                transfers.begin(blob.getSha256(), Math.toIntExact(blob.getSize()));
            }
        }
        send(new BlobRequestPacket(plan.getMissingHashes()));
    }

    private void accept(BlobChunkPacket chunk) {
        if (!remaining.contains(chunk.getSha256()) || transfers == null) {
            throw new IllegalStateException("Unexpected ArcSync blob chunk");
        }
        transfers.accept(chunk.getSha256(), Math.toIntExact(chunk.getOffset()), chunk.getBytes());
        if (chunk.getOffset() + chunk.getBytes().length == chunk.getTotalSize()) {
            runtime.accept(chunk.getSha256(), transfers.complete(chunk.getSha256()));
            remaining.remove(chunk.getSha256());
        }
    }

    private void activate(ActivatePacket packet) {
        if (!packet.getRevision().equals(revision)) {
            throw new IllegalStateException("ArcSync activation revision mismatch");
        }
        if (!remaining.isEmpty()) {
            throw new IllegalStateException("ArcSync activation arrived before all blobs");
        }
        runtime.activate();
    }

    private void disconnect() {
        runtime.disconnect();
        transfers = null;
        remaining = Set.of();
        revision = null;
    }

    private static void send(ArcSyncPacket packet) {
        ClientPlayNetworking.send(new ArcPayload(ArcSyncPacketCodec.encode(packet)));
    }
}
