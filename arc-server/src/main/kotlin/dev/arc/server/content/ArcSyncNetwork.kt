package dev.arc.server.content

import dev.arc.api.sync.ActivatePacket
import dev.arc.api.sync.ArcSync
import dev.arc.api.sync.ArcSyncPacket
import dev.arc.api.sync.ArcSyncPacketCodec
import dev.arc.api.sync.BlobRequestPacket
import dev.arc.api.sync.ClientHelloPacket
import dev.arc.api.sync.FailurePacket
import dev.arc.api.sync.ServerManifestPacket
import dev.arc.api.sync.SignedManifest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

public object ArcSyncNetwork {
    private data class Session(
        val signed: SignedManifest,
        val allowedHashes: Set<String>,
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()

    @JvmStatic
    public fun handle(playerId: UUID, bytes: ByteArray, sender: Consumer<ByteArray>) {
        runCatching {
            when (val packet = ArcSyncPacketCodec.decode(bytes)) {
                is ClientHelloPacket -> handleHello(playerId, packet, sender)
                is BlobRequestPacket -> handleRequest(playerId, packet, sender)
                else -> error("Unexpected client ArcSync packet: ${packet::class.simpleName}")
            }
        }.onFailure { error ->
            sessions.remove(playerId)
            val message = error.message?.take(512) ?: "ArcSync request failed"
            sender.accept(ArcSyncPacketCodec.encode(FailurePacket(message)))
        }
    }

    @JvmStatic
    public fun disconnect(playerId: UUID) {
        sessions.remove(playerId)
    }

    private fun handleHello(
        playerId: UUID,
        packet: ClientHelloPacket,
        sender: Consumer<ByteArray>,
    ) {
        val service = ArcSync.service ?: error("ArcSync is not available")
        val plan = service.plan(packet.hello)
        val signed = service.current ?: error("ArcSync content has not been published")
        check(packet.hello.features.containsAll(signed.manifest.requiredFeatures)) {
            "Client does not support required ArcSync features"
        }
        sessions[playerId] = Session(signed, plan.blobs.mapTo(hashSetOf()) { it.sha256 })
        sender.accept(
            ArcSyncPacketCodec.encode(
                ServerManifestPacket(service.publicKey.encoded, signed),
            ),
        )
    }

    private fun handleRequest(
        playerId: UUID,
        packet: BlobRequestPacket,
        sender: Consumer<ByteArray>,
    ) {
        val service = ArcSync.service ?: error("ArcSync is not available")
        val session = sessions[playerId] ?: error("ArcSync hello is required")
        check(service.current?.manifest?.revision == session.signed.manifest.revision) {
            "ArcSync revision changed; reconnect negotiation is required"
        }
        check(packet.hashes.all { it in session.allowedHashes }) {
            "Blob request contains an unplanned hash"
        }
        packet.hashes.forEach { hash ->
            val bytes = service.blob(hash) ?: error("ArcSync blob is unavailable: $hash")
            if (bytes.isEmpty()) {
                send(sender, dev.arc.api.sync.BlobChunkPacket(hash, 0, 0, bytes))
            } else {
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + ArcSyncPacketCodec.MAX_CHUNK_BYTES, bytes.size)
                    send(
                        sender,
                        dev.arc.api.sync.BlobChunkPacket(
                            hash,
                            offset.toLong(),
                            bytes.size.toLong(),
                            bytes.copyOfRange(offset, end),
                        ),
                    )
                    offset = end
                }
            }
        }
        send(sender, ActivatePacket(session.signed.manifest.revision))
    }

    private fun send(sender: Consumer<ByteArray>, packet: ArcSyncPacket) {
        sender.accept(ArcSyncPacketCodec.encode(packet))
    }
}
