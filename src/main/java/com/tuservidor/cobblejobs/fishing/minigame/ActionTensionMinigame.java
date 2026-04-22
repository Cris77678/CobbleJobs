package com.tuservidor.cobblejobs.fishing.minigame;

import com.tuservidor.cobblejobs.fishing.rarity.FishRarity;
import com.tuservidor.cobblejobs.util.MessageUtil;
import com.tuservidor.cobblejobs.util.FisherTips;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

import java.util.UUID;
import java.util.function.Consumer;

public class ActionTensionMinigame extends AbstractMinigameSession {

    private final ServerBossEvent bossBar;
    private double tension = 0.3;
    private int ticksInGreen = 0;
    private final int ticksRequired;
    private final double escapeSpeed;

    public ActionTensionMinigame(UUID uuid, FishRarity rarity, int level, Consumer<FishingMinigame.MinigameResult> callback) {
        super(uuid, callback);
        this.ticksRequired = 40 + (rarity.ordinal() * 10);
        this.escapeSpeed = 0.010 + (rarity.ordinal() * 0.003);
        
        this.bossBar = new ServerBossEvent(
            MessageUtil.literal("§e🎣 ¡Mantén la tensión! (Presiona SHIFT)"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
        );
    }

    @Override
    public void start(ServerPlayer player) {
        player.closeContainer();
        bossBar.addPlayer(player);
        player.playNotifySound(SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0f, 1.0f);
        
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(MessageUtil.literal("§b¡Pez enganchado!")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(MessageUtil.literal("§7Mantén presionada la tecla §e§lSHIFT §7para subir la barra")));
    }

    @Override
    public boolean tick(ServerPlayer player) {
        // CORRECCIÓN: Evitar crash si el jugador se desconecta
        if (player.hasDisconnected() || player.isRemoved() || !player.isAlive()) {
            bossBar.removePlayer(player);
            callback.accept(FishingMinigame.MinigameResult.FAIL);
            return false;
        }

        ticksOpen++;

        if (player.isCrouching()) {
            tension += 0.03;
        } else {
            tension -= escapeSpeed;
        }
        tension = Math.max(0.0, Math.min(1.0, tension));

        boolean inGreenZone = tension >= 0.40 && tension <= 0.75;

        if (inGreenZone) {
            bossBar.setColor(BossEvent.BossBarColor.GREEN);
            bossBar.setName(MessageUtil.literal("§a🎣 ¡Perfecto! Mantén ahí..."));
            ticksInGreen++;
            if (ticksInGreen % 10 == 0) {
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 2.0f);
            }
        } else if (tension > 0.85 || tension < 0.15) {
            bossBar.setColor(BossEvent.BossBarColor.RED);
            bossBar.setName(MessageUtil.literal("§c🎣 ¡Peligro! ¡Ajusta con SHIFT!"));
        } else {
            bossBar.setColor(BossEvent.BossBarColor.YELLOW);
            bossBar.setName(MessageUtil.literal("§e🎣 ¡Sigue así!"));
        }

        bossBar.setProgress((float) tension);

        if (ticksInGreen >= ticksRequired) {
            finish(player, FishingMinigame.MinigameResult.SUCCESS);
            return false;
        }

        if (tension >= 1.0 || tension <= 0.0 || ticksOpen > 600) {
            player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] ¡El pez rompió el sedal!"));
            FisherTips.sendTip(player, "💡 Tip: No dejes la barra en rojo. Da toques cortos de SHIFT para mantenerla al centro.");
            finish(player, FishingMinigame.MinigameResult.FAIL);
            return false;
        }

        return true;
    }

    private void finish(ServerPlayer player, FishingMinigame.MinigameResult result) {
        bossBar.removePlayer(player);
        callback.accept(result);
    }
}
