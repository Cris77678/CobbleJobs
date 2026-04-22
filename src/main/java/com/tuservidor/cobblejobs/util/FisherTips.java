package com.tuservidor.cobblejobs.util;

import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Random;

public class FisherTips {

    private static final Random RNG = new Random();

    // Lista de consejos que aparecerán mientras el jugador espera a que pique el pez
    private static final List<String> WAIT_TIPS = List.of(
        "💡 Usa /job collection para ver qué especies te faltan.",
        "💡 Los eventos de Frenesí aumentan la probabilidad de peces raros.",
        "💡 Revisa a los mejores pescadores usando /job top peso.",
        "💡 A mayor nivel, pescarás Pokémon más grandes y pesados.",
        "💡 ¿Sin espacio? Usa /job cooler para guardar tus capturas.",
        "💡 Los Pokémon legendarios solo aparecen en niveles altos.",
        "💡 Vende tus peces rápidamente usando el menú de /job sell."
    );

    // Envía una pista aleatoria directamente a la Action Bar
    public static void sendRandomWaitTip(ServerPlayer player) {
        String tip = getRandomWaitTip();
        sendActionBar(player, tip);
    }

    // Envía una pista específica escrita manualmente
    public static void sendTip(ServerPlayer player, String tip) {
        sendActionBar(player, tip);
    }

    // Retorna una pista aleatoria en forma de String (Usado por FishingHandler)
    public static String getRandomWaitTip() {
        return WAIT_TIPS.get(RNG.nextInt(WAIT_TIPS.size()));
    }

    // Método interno para enviar el paquete a la Action Bar del cliente
    private static void sendActionBar(ServerPlayer player, String text) {
        // El parámetro 'true' en displayClientMessage hace que vaya a la Action Bar en lugar del chat
        player.displayClientMessage(MessageUtil.literal("§e" + text), true);
    }
}
