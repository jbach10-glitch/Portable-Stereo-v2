package dev.portablestereo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import dev.portablestereo.network.PlayStereoPayload;
import dev.portablestereo.network.StopStereoPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

/** Server-side bookkeeping: who is currently playing what, and when it ends. */
public final class StereoPlayback {
    private StereoPlayback() {}

    private record Session(Holder<JukeboxSong> song, long endTick) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean isPlaying(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static boolean start(ServerPlayer player, ItemStack disc, ItemStack stereo) {
        Optional<Holder<JukeboxSong>> song = JukeboxSong.fromStack(disc);
        if (song.isEmpty()) {
            return false;
        }
        stop(player);

        long now = player.level().getServer().getTickCount();
        SESSIONS.put(player.getUUID(), new Session(song.get(), now + song.get().value().lengthInTicks()));
        stereo.set(ModItems.PLAYING, true);
        broadcast(player, new PlayStereoPayload(player.getId(), song.get()));
        StereoItem.tell(player, Component.translatable("message.portable_stereo.now_playing",
                song.get().value().description()));
        return true;
    }

    public static void stop(ServerPlayer player) {
        clearPlayingFlags(player);
        if (SESSIONS.remove(player.getUUID()) != null) {
            broadcast(player, new StopStereoPayload(player.getId()));
        }
    }

    /** Send to the holder and to every player whose client is tracking the holder (i.e. nearby). */
    private static void broadcast(ServerPlayer holder, CustomPacketPayload payload) {
        ServerPlayNetworking.send(holder, payload);
        for (ServerPlayer other : PlayerLookup.tracking(holder)) {
            if (other != holder) {
                ServerPlayNetworking.send(other, payload);
            }
        }
    }

    static void onStartTracking(ServerPlayer holder, ServerPlayer viewer) {
        Session session = SESSIONS.get(holder.getUUID());
        if (session != null) {
            // Late arrivals hear the song from the start (vanilla can't seek into a record)
            ServerPlayNetworking.send(viewer, new PlayStereoPayload(holder.getId(), session.song()));
        }
    }

    static void onStopTracking(ServerPlayer holder, ServerPlayer viewer) {
        if (SESSIONS.containsKey(holder.getUUID())) {
            ServerPlayNetworking.send(viewer, new StopStereoPayload(holder.getId()));
        }
    }

    static void tick(MinecraftServer server) {
        long now = server.getTickCount();

        // Once a second: clear stale "playing" looks (e.g. after a restart or a stereo picked back up)
        if (now % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!SESSIONS.containsKey(player.getUUID())) {
                    clearPlayingFlags(player);
                }
            }
        }

        if (SESSIONS.isEmpty()) {
            return;
        }
        boolean fullCheck = now % 10 == 0;

        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                SESSIONS.remove(entry.getKey()); // logged out; clients drop sounds for removed entities
                continue;
            }
            boolean finished = now >= entry.getValue().endTick();
            boolean lostStereo = fullCheck && (!player.isAlive()
                    || !anyStereo(player, s -> s.has(ModItems.PLAYING) && s.has(ModItems.LOADED_DISC)));
            if (finished || lostStereo) {
                stop(player);
            }
        }
    }

    private static void clearPlayingFlags(ServerPlayer player) {
        anyStereo(player, s -> {
            if (s.has(ModItems.PLAYING)) {
                s.remove(ModItems.PLAYING);
            }
            return false; // keep scanning every slot
        });
    }

    /** Runs the test on every stereo in the inventory (and on the mouse cursor); true if any passes. */
    private static boolean anyStereo(ServerPlayer player, Predicate<ItemStack> test) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.PORTABLE_STEREO) && test.test(stack)) {
                return true;
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        return carried.is(ModItems.PORTABLE_STEREO) && test.test(carried);
    }
}
