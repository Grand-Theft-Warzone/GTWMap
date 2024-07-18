package fr.aym.gtwmap.map;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.network.S19PacketMapPartQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.function.Function;

public class MapPart {
    private final PartPos pos;
    private final int width, heigth;
    private final int[] mapTextureData;
    private final Function<PartPos, Void> reloadCallable;

    private boolean dirty = true;

    private World world;

    public MapPart(World world, PartPos pos, int width, int height, @Nullable Function<PartPos, Void> reloadCallable2, @Nullable int[] textureData) {
        this.world = world;
        this.pos = pos;
        this.width = width;
        this.heigth = height;
        if (textureData != null)
            this.mapTextureData = textureData;
        else
            this.mapTextureData = new int[width * height];
        for (int i = 0; i < this.mapTextureData.length; ++i) {
            this.mapTextureData[i] = Color.lightGray.getRGB();
        }
        this.reloadCallable = reloadCallable2;
    }

    public void updateMapContents() {
        if (dirty() || mapTextureData[0] == Color.lightGray.getRGB()) {
            if (world.isRemote) {
                onChange(); //Will put gray on client
                dirty = false;
                int x = pos.xOrig * 400;
                int z = pos.zOrig * 400;
                /*if (x < 0)
                    x += 400;
                if (z < 0)
                    z += 400;*/
                System.out.println("CHANGE >>> Requesting "+this+" "+this.pos+" "+x+" "+z);
                GtwMapMod.getNetwork().sendToServer(new S19PacketMapPartQuery(x, z));
            } else {
                try {
                    MapLoader.load(this);
                    dirty = false;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else
            onChange();
    }

    @SideOnly(Side.CLIENT)
    public void feedWidthBlockData(int[] data) {
        // System.out.println("Feeding "+this+" "+this.pos+data[0]+" "+Color.ORANGE.getRGB());
        for (int i = 0; i < data.length; i++) {
    		/*IBlockState state = Block.getStateById(data[i]);
    		int color;
    		if(state.getBlock() == Blocks.AIR)
    			color = Color.BLACK.getRGB(); //Area not loaded
    		else
    			//TODO */
            mapTextureData[i] = data[i];
        }
        onChangeClient();
    }

    public void onChange() {
        if (reloadCallable == null)
            return;
        if (FMLCommonHandler.instance().getSide().isServer())
            onChangeServer();
        else
            onChangeClient();
    }

    private void onChangeServer() {
        FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> reloadCallable.apply(pos));
    }

    @SideOnly(Side.CLIENT)
    private void onChangeClient() {
        Minecraft.getMinecraft().addScheduledTask(() -> reloadCallable.apply(pos));
    }

    public MapPart setDirty(boolean dirty, @Nullable BlockPos mark) {
        if (mark != null) {
            int x = mark.getX() - pos.getInWorldX();
            int z = mark.getZ() - pos.getInWorldZ();
            if ((z * getHeigth() + x) > mapTextureData.length || (z * getHeigth() + x) < 0) {
                System.out.println("OUTOFBOUND " + x + z + getWidth() + getHeigth() + mapTextureData.length + " " + mark + " " + pos + " " + this + " == " + (z * getHeigth() + x));
                this.dirty = true;
                return this;
            }
            mapTextureData[z * this.getHeigth() + x] = Color.lightGray.getRGB();
        }
        this.dirty = dirty;
        return this;
    }

    public boolean dirty() {
        return dirty;
    }

    public PartPos getPos() {
        return pos;
    }

    public int[] getMapTextureData() {
        return mapTextureData;
    }

    public int getWidth() {
        return width;
    }

    public int getHeigth() {
        return heigth;
    }

    public World getWorld() {
        return world;
    }
}
