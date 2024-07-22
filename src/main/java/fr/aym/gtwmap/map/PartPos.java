package fr.aym.gtwmap.map;

import fr.aym.gtwmap.utils.GtwMapConstants;

public class PartPos {
    public final int xOrig;
    public final int zOrig;

    public PartPos(int xOrig, int zOrig) {
        this.xOrig = xOrig;
        this.zOrig = zOrig;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PartPos)
            return ((PartPos) obj).xOrig == xOrig && ((PartPos) obj).zOrig == zOrig;
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        int i = 1664525 * this.xOrig + 1013904223;
        int j = 1664525 * (this.zOrig ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public String toString() {
        return "PartPos{x=" + xOrig + ";z=" + zOrig + "}";
    }

    public int getInWorldX() {
        return xOrig * GtwMapConstants.TILE_SIZE;
    }

    public int getInWorldZ() {
        return zOrig * GtwMapConstants.TILE_SIZE;
    }
}