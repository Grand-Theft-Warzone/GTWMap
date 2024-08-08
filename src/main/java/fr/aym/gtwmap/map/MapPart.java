package fr.aym.gtwmap.map;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.network.S19PacketMapPartQuery;
import lombok.Getter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Arrays;

public abstract class MapPart {
    @Getter
    private final PartPos pos;
    @Getter
    private final int width;
    @Getter
    private final int length;
    @Getter
    private final int[] mapTextureData;
    @Getter
    private final World world;

    @Getter
    private State state = State.NOT_SET;

    protected MapPart(World world, PartPos pos, int width, int length, @Nullable int[] textureData) {
        this.world = world;
        this.pos = pos;
        this.width = width;
        this.length = length;
        if (textureData != null)
            this.mapTextureData = textureData;
        else
            this.mapTextureData = new int[width * length];
        fillWithColor(Color.LIGHT_GRAY.getRGB());
    }

    public void fillWithColor(int color) {
        Arrays.fill(mapTextureData, color);
    }

    public void refreshMapContents() {
        if (!world.isRemote) {
            System.out.println("Refreshing " + this + " " + this.pos + " " + state);
        }
        if (state != State.LOADED && state != State.LOADING) {
            if (world.isRemote) {
                onContentsChange(); //Will put gray on client
                state = State.LOADING;
                int x = pos.xOrig * 400;
                int z = pos.zOrig * 400;
                System.out.println("CHANGE >>> Requesting " + this + " " + this.pos + " " + x + " " + z);
                GtwMapMod.getNetwork().sendToServer(new S19PacketMapPartQuery(x, z));
            } else {
                try {
                    MapLoader.getInstance().load(this);
                    state = State.LOADING;
                } catch (Exception e) {
                    GtwMapMod.log.fatal("Error loading map part {}", this, e);
                    state = State.ERRORED;
                    fillWithColor(Color.RED.getRGB());
                }
            }
        } else {
            onContentsChange();
        }
    }

    @SideOnly(Side.CLIENT)
    public void feedWidthBlockData(int[] data) {
        System.arraycopy(data, 0, mapTextureData, 0, data.length);
        setDirty(false, null);
        onContentsChange();
    }

    public abstract void onContentsChange();

    public MapPart setDirty(boolean dirty, @Nullable BlockPos mark) {
        if (mark != null) {
            int x = mark.getX() - pos.getInWorldX();
            int z = mark.getZ() - pos.getInWorldZ();
            if ((z * getLength() + x) > mapTextureData.length || (z * getLength() + x) < 0) {
                System.out.println("Mark OUTOFBOUND x: " + x + " z: " + z + " w: " + getWidth() + " l: " + getLength() + " mtl: " + mapTextureData.length + " mark: " + mark + " pos: " + pos + " " + this + " == " + (z * getLength() + x));
                this.state = State.ERRORED;
                fillWithColor(Color.ORANGE.getRGB());
                return this;
            }
            mapTextureData[z * this.getLength() + x] = Color.LIGHT_GRAY.getRGB();
        }
        this.state = dirty ? State.DIRTY : State.LOADED;
        return this;
    }

    public enum State {
        NOT_SET, LOADING, LOADED, ERRORED, DIRTY
    }
}
