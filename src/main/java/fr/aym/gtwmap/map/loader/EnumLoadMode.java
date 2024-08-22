package fr.aym.gtwmap.map.loader;

public enum EnumLoadMode {
    LOAD_FROM_FILE,
    LOAD_NON_LOADED_ZONES,
    LOAD_NON_LOADED_ZONES_GEN_CHUNKS,
    RELOAD_ALL_GEN_CHUNKS;

    public boolean isReloadingAll() {
        return this == RELOAD_ALL_GEN_CHUNKS;
    }

    public boolean isGeneratingChunks() {
        return this == LOAD_NON_LOADED_ZONES_GEN_CHUNKS || this == RELOAD_ALL_GEN_CHUNKS;
    }

    public boolean isFixingNonLoaded() {
        return this != LOAD_FROM_FILE;
    }
}
