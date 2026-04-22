package com.tuservidor.cobblejobs.fishing.rarity;

/**
 * Rarezas de peces con colores, multiplicadores económicos y XP.
 */
public enum FishRarity {

    COMMON   ("§7",    "Común",     1.0,   10.0,  false),
    RARE     ("§b",    "Raro",      2.5,   30.0,  false),
    EPIC     ("§d",    "Épico",     6.0,   80.0,  true),
    LEGENDARY("§6§l",  "Legendario",15.0, 200.0,  true);

    /** Color de texto del nombre */
    public final String color;
    /** Nombre en español */
    public final String displayName;
    /** Multiplicador sobre precio base */
    public final double priceMultiplier;
    /** XP otorgada al capturar */
    public final double xpReward;
    /** ¿Muestra partículas al capturar? */
    public final boolean showParticles;

    FishRarity(String color, String displayName, double priceMultiplier,
               double xpReward, boolean showParticles) {
        this.color = color;
        this.displayName = displayName;
        this.priceMultiplier = priceMultiplier;
        this.xpReward = xpReward;
        this.showParticles = showParticles;
    }

    /** Formatea el nombre con color */
    public String formatted() {
        return color + displayName + "§r";
    }

    /** Nombre del enum seguro para config */
    public String key() {
        return name().toLowerCase();
    }
}
