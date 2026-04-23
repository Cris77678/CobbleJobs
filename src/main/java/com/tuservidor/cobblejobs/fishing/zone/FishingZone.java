package com.tuservidor.cobblejobs.fishing.zone;

import com.tuservidor.cobblejobs.fishing.rarity.FishRarity;

import java.util.List;

public class FishingZone {

    public enum ZoneType {
        LAKE    ("§9",  "Lago"),
        OCEAN   ("§1",  "Océano"),
        RIVER   ("§3",  "Río"),
        SPECIAL ("§6§l","Zona Especial");

        public final String color;
        public final String displayName;
        ZoneType(String color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }
    }

    private String id;
    private ZoneType type = ZoneType.LAKE;
    private boolean enabled = true;

    private double x1, y1, z1;
    private double x2, y2, z2;
    
    // CORRECCIÓN 2: Validar ambos puntos explícitamente
    private boolean hasPos1 = false;
    private boolean hasPos2 = false;
    private boolean configured = false;

    private List<ZoneFishEntry> fishTable;
    private boolean showParticles = true;

    public FishingZone() {}

    public FishingZone(String id, ZoneType type) {
        this.id   = id;
        this.type = type;
    }

    public boolean containsXYZ(double x, double y, double z) {
        if (!configured) return false;
        
        double minY = Math.min(y1, y2) - 35.0; 
        double maxY = Math.max(y1, y2) + 10.0;
        
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
            && y >= minY && y <= maxY
            && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
    }

    public String getId()           { return id; }
    public ZoneType getType()       { return type; }
    public boolean isEnabled()      { return enabled; }
    public boolean isConfigured()   { return configured; }
    public List<ZoneFishEntry> getFishTable() { return fishTable; }
    public boolean isShowParticles(){ return showParticles; }

    public double getX1() { return x1; } public double getY1() { return y1; }
    public double getZ1() { return z1; } public double getX2() { return x2; }
    public double getY2() { return y2; } public double getZ2() { return z2; }

    public void setId(String id)             { this.id = id; }
    public void setType(ZoneType type)       { this.type = type; }
    public void setEnabled(boolean e)        { this.enabled = e; }
    public void setFishTable(List<ZoneFishEntry> t) { this.fishTable = t; }
    public void setShowParticles(boolean b)  { this.showParticles = b; }

    public void setPos1(double x, double y, double z) { 
        x1 = x; y1 = y; z1 = z; 
        hasPos1 = true;
        configured = hasPos1 && hasPos2;
    }
    
    public void setPos2(double x, double y, double z) {
        x2 = x; y2 = y; z2 = z;
        hasPos2 = true;
        configured = hasPos1 && hasPos2;
    }

    public static class ZoneFishEntry {
        private String    species    = "cobblemon:magikarp";
        private FishRarity rarity    = FishRarity.COMMON;
        private double    chance     = 0.6;
        private double    minWeight  = 0.1;
        private double    maxWeight  = 8.0;
        private int       minLength  = 3;
        private int       maxLength  = 60;
        private int       minLevel   = 1;

        public ZoneFishEntry() {}

        public ZoneFishEntry(String species, FishRarity rarity, double chance,
                             double minWeight, double maxWeight,
                             int minLength, int maxLength, int minLevel) {
            this.species   = species;
            this.rarity    = rarity;
            this.chance    = chance;
            this.minWeight = minWeight;
            this.maxWeight = maxWeight;
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.minLevel  = minLevel;
        }

        public String    getSpecies()   { return species; }
        public FishRarity getRarity()   { return rarity; }
        public double    getChance()    { return chance; }
        public double    getMinWeight() { return minWeight; }
        public double    getMaxWeight() { return maxWeight; }
        public int       getMinLength() { return minLength; }
        public int       getMaxLength() { return maxLength; }
        public int       getMinLevel()  { return minLevel; }
    }
}
