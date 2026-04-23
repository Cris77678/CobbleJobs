package com.tuservidor.cobblejobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class FisherCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var base = Commands.literal("job");

        base.then(Commands.literal("info").requires(CommandSourceStack::isPlayer).executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            PlayerFisherData d = PlayerFisherData.get(p.getUUID());
            
            double nextXp = d.xpForNextLevel();
            String xpBar = buildXpBar(d.getXp(), nextXp, 20, d.getLevel());

            p.sendSystemMessage(MessageUtil.literal(
                "§6§l══ 🎣 Estadísticas de Pesca ══\n" +
                "§fNivel: §e" + d.getLevel() + (d.getLevel() >= 50 ? " §6§l[MAX]" : "") + "\n" +
                "§fXP: §7" + xpBar + " §8(" + (int)d.getXp() + "/" + (int)nextXp + ")\n" +
                "§fPeces atrapados: §7" + d.getTotalFishCaught() + "\n" +
                "§6§l════════════════"
            ));
            return 1;
        }));
    }

    private static String buildXpBar(double current, double max, int length, int level) {
        if (level >= 50) return "§6§l" + "█".repeat(length);
        int reached = (int) ((current / max) * length);
        return "§a" + "█".repeat(Math.max(0, reached)) + "§7" + "█".repeat(Math.max(0, length - reached));
    }
}
