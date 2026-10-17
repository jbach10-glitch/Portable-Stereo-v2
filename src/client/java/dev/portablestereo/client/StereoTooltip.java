package dev.portablestereo.client;

import dev.portablestereo.ModItems;
import dev.portablestereo.StereoItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

/** Adds "Loaded: <song>" / "Playing: <song>" lines to the stereo's tooltip. */
public final class StereoTooltip {
    private StereoTooltip() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!stack.is(ModItems.PORTABLE_STEREO)) {
                return;
            }
            ItemStack disc = StereoItem.getDisc(stack);
            if (disc.isEmpty()) {
                lines.add(Component.translatable("tooltip.portable_stereo.empty").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.portable_stereo.hint_load").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            Component song = JukeboxSong.fromStack(disc)
                    .map(holder -> holder.value().description())
                    .orElse(disc.getHoverName());
            boolean playing = stack.has(ModItems.PLAYING);
            lines.add(Component.translatable(playing ? "tooltip.portable_stereo.playing" : "tooltip.portable_stereo.loaded", song)
                    .withStyle(playing ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.portable_stereo.hint_eject").withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
