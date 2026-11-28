package org.embeddedt.embeddium.impl.gametest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.embeddedt.embeddium.impl.gametest.content.TestRegistry;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SyncS2CPacket {
    private final UUID uuid;

    private static final ConcurrentHashMap<UUID, CountDownLatch> LATCHES = new ConcurrentHashMap<>();

    public SyncS2CPacket() {
        this.uuid = UUID.randomUUID();
    }

    public SyncS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
    }

    public void applyBarrier() {
        var latch = new CountDownLatch(1);
        CountDownLatch prevLatch = LATCHES.putIfAbsent(this.uuid, latch);
        if(prevLatch != null) {
            throw new IllegalStateException("Existing latch found for barrier UUID: " + this.uuid);
        }
        TestRegistry.LOGGER.debug("Waiting at barrier {}", this.uuid);
        try {
            TestRegistry.NETWORK_CHANNEL.send(PacketDistributor.ALL.noArg(), this);
            if(!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Client did not reach barrier " + this.uuid);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            LATCHES.remove(this.uuid, latch);
        }
        TestRegistry.LOGGER.debug("Continuing past barrier {}", this.uuid);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var latch = LATCHES.remove(this.uuid);
            if(latch != null) {
                TestRegistry.LOGGER.debug("Reached barrier {}", this.uuid);
                latch.countDown();
            }
        });
        context.get().setPacketHandled(true);
    }
}
