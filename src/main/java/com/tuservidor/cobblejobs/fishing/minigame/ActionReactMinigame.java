package com.tuservidor.cobblejobs.fishing.minigame;

import com.tuservidor.cobblejobs.fishing.rarity.FishRarity;
import com.tuservidor.cobblejobs.util.MessageUtil;
import com.tuservidor.cobblejobs.util.FisherTips;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;
import java.util.function.Consumer;

public class ActionReactMinigame extends AbstractMinigameSession {

    private final int reactionTime;
    private int phase = 0; 
    private int phaseTicks = 0;
    
    private int waitTicks = 0;
    private final int waitTime = 30 + new java.util.Random().nextInt(40); 

    public ActionReactMinigame(UUID uuid, FishRarity rarity, int level, Consumer<FishingMinigame.MinigameResult> callback) {
        super(uuid, callback);
        this.reactionTime = 40 - (rarity.ordinal() * 5); 
    }

    @Override
    public void start(ServerPlayer player) {
        player.closeContainer();
        phase = 0;
        waitTicks = 0;
        
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(MessageUtil.literal("§e¡Atento!")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(MessageUtil.literal("§7Da CLIC IZQUIERDO rápido cuando diga §cAHORA")));
        player.playNotifySound(SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public boolean tick(ServerPlayer player) {
        if (player.hasDisconnected() || player.isRemoved() || !player.isAlive()) {
            callback.accept(FishingMinigame.MinigameResult.FAIL);
            return false;
        }

        ticksOpen++;
        
        if (phase == 0) {
            waitTicks++;
            
            // Si el jugador intenta hacer trampa clickeando antes de que salga AHORA
            if (waitTicks > 15 && player.swinging) {
                clearTitle(player);
                player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] ¡Te adelantaste! Espantaste al pez."));
                callback.accept(FishingMinigame.MinigameResult.FAIL);
                return false;
            }

            if (waitTicks >= waitTime) {
                phase = 1;
                phaseTicks = 0;
                player.connection.send(new ClientboundSetTitlesAnimationPacket(2, reactionTime, 5));
                player.connection.send(new ClientboundSetTitleTextPacket(MessageUtil.literal("§c§l¡AHORA!")));
                player.connection.send(new ClientboundSetSubtitleTextPacket(MessageUtil.literal("§f¡Da CLIC IZQUIERDO rápido!")));
                player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        } 
        else if (phase == 1) {
            phaseTicks++;
            
            // Condición de victoria: Clic exitoso en la ventana de tiempo
            if (player.swinging) {
                clearTitle(player);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 2.0f);
                callback.accept(FishingMinigame.MinigameResult.SUCCESS);
                return false;
            } 
            // Condición de derrota: Se acabó el tiempo de reacción
            else if (phaseTicks > reactionTime) {
                clearTitle(player);
                player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] ¡Fuiste muy lento y escapó!"));
                FisherTips.sendTip(player, "💡 Tip: Mantén el dedo listo para hacer Clic Izquierdo en cuanto diga AHORA.");
                callback.accept(FishingMinigame.MinigameResult.FAIL);
                return false;
            }
        }
        return true;
    }

    private void clearTitle(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 0, 0));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
    }
}
