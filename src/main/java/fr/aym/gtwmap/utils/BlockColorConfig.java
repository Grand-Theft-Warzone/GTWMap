package fr.aym.gtwmap.utils;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.loader.BlockColourGen;
import fr.aym.gtwmap.map.loader.BlockColours;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;

import java.io.File;

public class BlockColorConfig {
    //private static final Map<IBlockState, Integer> blockColors = new HashMap<>();
    //private static final Map<String, Integer> configContent = new HashMap<>();

    private static final BlockColours blockColours = new BlockColours();

	// values that change how height shading algorithm works
	public static final double brightenExponent = 0.35;
	public static final double darkenExponent = 0.35;
	public static final double brightenAmplitude = 0.7;
	public static final double darkenAmplitude = 1.4;

    public static void init(File config) {
        //if (!config.exists()) {
        //genDefaultBlockColors(config, fakeWorld);
            /*} else {
                readBlockColors(config);
            }*/
        if (!config.exists() || !blockColours.CheckFileVersion(config)) {
            GtwMapMod.log.warn("generating block colours");
            BlockColourGen.genBlockColours(blockColours);
            // load overrides again to override block and biome colours
            loadBlockColourOverrides(blockColours);
            GtwMapMod.log.info("saving block colours to '%s'", config);
            blockColours.saveToFile(config);
        } else {
            GtwMapMod.log.info("loading block colours from %s", config);
            blockColours.loadFromFile(config);
            loadBlockColourOverrides(blockColours);
        }
    }

    public static void loadBlockColourOverrides(BlockColours bc) {
        File f = new File("MapData", "colors_overrides.cfg"); //TODO PARAM
        if (f.isFile()) {
            GtwMapMod.log.info("loading block colour overrides file %s", f);
            bc.loadFromFile(f);
        } else {
            GtwMapMod.log.info("recreating block colour overrides file %s", f);
            BlockColours.writeOverridesFile(f);
            if (f.isFile()) {
                bc.loadFromFile(f);
            } else {
                GtwMapMod.log.error("could not load block colour overrides from file %s", f);
            }
        }
    }

    public static int getBlockColor(IBlockState state) {
        //return blockColors.getOrDefault(state, MapColor.AIR.colorValue);
        return blockColours.getColour(state);
    }

    public static int getColumnColour(Chunk chunk, int x, int y, int z, IBlockState state, int blockColor, int heightW, int heightN) {
        double a = 1.0;
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;

        int biome = chunk.getBiomeArray()[(z & 0xf) << 4 | (x & 0xf)] & 0xff;
        int c2 = blockColours.getBiomeColour(state, biome);

        // extract colour components as normalized doubles
        double c1R = ((blockColor >> 16) & 0xff) / 255.0;
        double c1G = ((blockColor >> 8) & 0xff) / 255.0;
        double c1B = ((blockColor >> 0) & 0xff) / 255.0;

        // c2A is implicitly 1.0 (opaque)
        double c2R = ((c2 >> 16) & 0xff) / 255.0;
        double c2G = ((c2 >> 8) & 0xff) / 255.0;
        double c2B = ((c2 >> 0) & 0xff) / 255.0;

        // alpha blend and multiply
        r = r + (a * c1R * c2R);
        g = g + (a * c1G * c2G);
        b = b + (a * c1B * c2B);

        /*
         * // darken blocks depending on how far away they are from this depth
         * slice if (depth != 0) { int bottomOfSlice = maxHeight - ((depth + 1)
         * * maxHeight / Mw.HEIGHT_LEVELS) - 1; if (yRange[0] < bottomOfSlice) {
         * shading *= 1.0 - 2.0 * ((double) (bottomOfSlice - yRange[0]) /
         * (double) maxHeight); } }
         */

        double heightShading = getHeightShading(y, heightW, heightN);
        int lightValue = 15;//chunk.getLightValue(x, y + 1, z);
        double lightShading = lightValue / 15.0;
        double shading = (heightShading + 1.0) * lightShading;

        // apply the shading
        r = Math.min(Math.max(0.0, r * shading), 1.0);
        g = Math.min(Math.max(0.0, g * shading), 1.0);
        b = Math.min(Math.max(0.0, b * shading), 1.0);

        // now we have our final RGB values as doubles, convert to a packed ARGB
        // pixel.
        return ((y & 0xff) << 24) | ((((int) (r * 255.0)) & 0xff) << 16) | ((((int) (g * 255.0)) & 0xff) << 8) | ((((int) (b * 255.0)) & 0xff));
    }

    public static double getHeightShading(int height, int heightW, int heightN) {
        int samples = 0;
        int heightDiff = 0;

        if ((heightW > 0) && (heightW < 255)) {
            heightDiff += height - heightW;
            samples++;
        }

        if ((heightN > 0) && (heightN < 255)) {
            heightDiff += height - heightN;
            samples++;
        }

        double heightDiffFactor = 0.0;
        if (samples > 0) {
            heightDiffFactor = (double) heightDiff / ((double) samples);
        }

        // emphasize small differences in height, but as the difference in
        // height increases,
        // don't increase so much
        /*if (Config.moreRealisticMap) {
            return Math.atan(heightDiffFactor) * 0.3;
        }*/

        return (heightDiffFactor >= 0.0) ? Math.pow(heightDiffFactor * (1 / 255.0), brightenExponent) * brightenAmplitude : -Math.pow(-(heightDiffFactor * (1 / 255.0)), darkenExponent) * darkenAmplitude;
    }

    /*private static void genDefaultBlockColors(File writeTo, World fakeWorld) throws IOException {
        GtwMapMod.log.warn("Generating default block colors config. This may take a while.");
        Spliterator<Block> it = Block.REGISTRY.spliterator();
        BlockPos fakePos = new BlockPos(0, 0, 0);
        it.forEachRemaining(block -> {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                if (!state.isTopSolid()) {
                    continue;
                }
                MapColor color = state.getMapColor(fakeWorld, fakePos);
                if (color != MapColor.AIR) {
                    /*int[][] textureData = getTopSideTextureData(state);
                    blockColors.put(state, textureData);
                    configContent.put(block.getRegistryName().toString() + "#" + block.getMetaFromState(state), textureData);*/
    /*
                }
            }
        });
        try (FileWriter writer = new FileWriter(writeTo)) {
            for (Map.Entry<String, Integer> entry : configContent.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        }
        GtwMapMod.log.info("Default block colors config generated.");
    }

    private static int[][] getTopSideTextureData(IBlockState state) {
        int[][] textureData = new int[16][16];
        // Assuming we have a method to get the texture data for a given state and side
        // This method should be implemented to extract the actual texture data
        int[] rawData = extractTextureData(state);
        for (int i = 0; i < 16; i++) {
            System.arraycopy(rawData, i * 16, textureData[i], 0, 16);
        }
        return textureData;
    }

    private static int[] extractTextureData(IBlockState state) {
        // Get the block's top side texture
        Block block = state.getBlock();
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getBlockRendererDispatcher()
                .getBlockModelShapes().getTexture(state);

        // Get the texture data
        int originalWidth = sprite.getIconWidth();
        int originalHeight = sprite.getIconHeight();
        int[] textureData = new int[256]; // 16x16 texture
        int[][] mipmaps = sprite.getFrameTextureData(0);

        // Scale the texture to 16x16 if necessary
        int[] scaledData = new int[256];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int srcX = i * originalWidth / 16;
                int srcY = j * originalHeight / 16;
                scaledData[i * 16 + j] = mipmaps[0][srcY * originalWidth + srcX];
            }
        }

        // Apply a simple blurring algorithm
        int[] blurredData = new int[256];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int sum = 0;
                int count = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int x = i + dx;
                        int y = j + dy;
                        if (x >= 0 && x < 16 && y >= 0 && y < 16) {
                            sum += scaledData[x * 16 + y];
                            count++;
                        }
                    }
                }
                blurredData[i * 16 + j] = sum / count;
            }
        }

        return blurredData;
    }

    private static void readBlockColors(File readFrom) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(readFrom));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("=");
            configContent.put(parts[0], Integer.parseInt(parts[1]));
        }
        configContent.forEach((key, value) -> {
            String[] parts = key.split("#");
            Block block = Block.getBlockFromName(parts[0]);
            if (block != null) {
                IBlockState state = block.getStateFromMeta(Integer.parseInt(parts[1]));
                blockColors.put(state, value);
            }
        });
    }*/
}
