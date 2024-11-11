package fr.aym.gtwmap.client.gui;

import fr.aym.acsguis.api.GuiAPIClientHelper;
import fr.aym.acsguis.component.GuiComponent;
import fr.aym.acsguis.component.layout.GuiScaler;
import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.acsguis.component.panel.GuiPanel;
import fr.aym.acsguis.component.style.AutoStyleHandler;
import fr.aym.acsguis.component.style.ComponentStyleManager;
import fr.aym.acsguis.component.textarea.GuiLabel;
import fr.aym.acsguis.cssengine.selectors.EnumSelectorContext;
import fr.aym.acsguis.cssengine.style.EnumCssStyleProperty;
import fr.aym.acsguis.utils.CircleBackground;
import fr.aym.acsguis.utils.ComponentRenderContext;
import fr.aym.acsguis.utils.GuiTextureSprite;
import fr.aym.acsguis.utils.IGuiTexture;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.api.GtwMapApi;
import fr.aym.gtwmap.api.ITrackableObject;
import fr.aym.gtwmap.client.ClientEventHandler;
import fr.aym.gtwmap.client.gps.GpsNavigator;
import fr.aym.gtwmap.common.gps.BezierCurveLink;
import fr.aym.gtwmap.common.gps.GpsNode;
import fr.aym.gtwmap.common.gps.GpsNodes;
import fr.aym.gtwmap.common.gps.WaypointNode;
import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.MapContainerClient;
import fr.aym.gtwmap.map.MapPartClient;
import fr.aym.gtwmap.map.PartPos;
import fr.aym.gtwmap.network.S19PacketMapPartQuery;
import fr.aym.gtwmap.utils.Config;
import fr.aym.gtwmap.utils.GtwMapConstants;
import lombok.AllArgsConstructor;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.util.*;

import static org.lwjgl.opengl.GL11.*;

public class GuiMinimap extends GuiFrame {
    public static final ResourceLocation STYLE = new ResourceLocation(GtwMapConstants.ID, "css/gui_mini_map.css");

    private Viewport viewport;

    private final GuiPanel mapPane;
    private final Map<PartPos, GuiBigMap.PartPosAutoStyleHandler> partsStore = new HashMap<>();
    private final Map<EntityPlayer, ITrackableObject<?>> trackedPlayers = new HashMap<>();
    private final Map<ITrackableObject<?>, EntityPosCache> tackedObjectsPosCache = new HashMap<>();

    private final Map<GpsNode, GuiComponent<?>> gpsNodeComponents = new HashMap<>();
    private GuiComponent<?> customMarkerComponent;

    private float mapSize;
    private int mapSizeChangeCooldown;
    private float targetMapSize;

    public GuiMinimap() {
        super(new GuiScaler.AdjustToScreenSize(true, 0.3f, 0.3f));
        setCssId("root");
        setPauseGame(false);
        setNeedsCssReload(true);

        this.viewport = new Viewport((float) (mc.player.posX - 60), (float) (mc.player.posZ - 60), 120, 120);

        final GuiPanel pane = new GuiPanel();
        pane.setCssId("container");
        mapPane = new GuiPanel();
        mapPane.setCssId("map_pane");
        pane.add(mapPane);
        add(pane);

        for (EntityPlayer player : mc.world.playerEntities) {
            boolean self = player == mc.player;
            ITrackableObject<?> trackedObject = new ITrackableObject.TrackedEntity(player, self ? I18n.format("gui.map.you") : player.getDisplayNameString(), self ? "r_arrow_white" : "player_white");
            trackedPlayers.put(player, trackedObject);
            makeTrackedPoint(trackedObject, self);
        }
        for (ITrackableObject<?> object : GtwMapApi.getTrackedObjects()) {
            makeTrackedPoint(object, false);
        }
        refreshGpsNodeComponents();
        refreshCustomMarker();
    }

    public void refreshGpsNodeComponents() {
        Map<GpsNode, GuiComponent<?>> newGpsNodeComponents = new HashMap<>();
        GuiTextureSprite icon = null;
        for (GpsNode node : GpsNodes.getInstance().getNodes()) {
            if (node instanceof WaypointNode) {
                if (gpsNodeComponents.containsKey(node)) {
                    mapPane.remove(gpsNodeComponents.get(node));
                }
                GuiComponent<?> label = new GuiLabel("") {
                    @Override
                    protected void bindLayerBounds() {
                        boolean visible = getRenderMinX() > GuiMinimap.this.getRenderMinX() && getRenderMaxX() < GuiMinimap.this.getRenderMaxX() && getRenderMinY() > GuiMinimap.this.getRenderMinY() && getRenderMaxY() < GuiMinimap.this.getRenderMaxY();
                        if (!visible)
                            return;
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(getRenderMinX() + getWidth() / 2f, getRenderMinY() + getHeight() / 2f, 0);
                        GlStateManager.rotate(180 + mc.player.rotationYaw, 0, 0, 1);
                        GlStateManager.translate(-getRenderMinX() - getWidth() / 2f, -getRenderMinY() - getHeight() / 2f, 0);
                    }

                    @Override
                    public void drawTexturedBackground(int mouseX, int mouseY, float partialTicks) {
                        //super.drawTexturedBackground(mouseX, mouseY, partialTicks);

                        float scale = (float) (1.23f * (10 / Math.sqrt(mapSize)));
                        IGuiTexture renderTexture = style.getTexture();
                        if (renderTexture != null) {
                            GlStateManager.enableBlend();
                            GlStateManager.pushMatrix();
                            GlStateManager.translate(getWidth() / 2f, getHeight() / 2f, 0);
                            GlStateManager.translate(-getWidth() * scale / 2, -getHeight() * scale / 2, 0);
                            renderTexture.drawSprite(getScreenX(), getScreenY(), (int) (getWidth() * scale), (int) (getHeight() * scale));
                            GlStateManager.popMatrix();
                            GlStateManager.disableBlend();
                        }
                    }

                    @Override
                    protected void unbindLayerBounds() {
                        boolean visible = getRenderMinX() > GuiMinimap.this.getRenderMinX() && getRenderMaxX() < GuiMinimap.this.getRenderMaxX() && getRenderMinY() > GuiMinimap.this.getRenderMinY() && getRenderMaxY() < GuiMinimap.this.getRenderMaxY();
                        if (!visible)
                            return;
                        GlStateManager.popMatrix();
                    }
                }.setCssId("gps_node").setCssClass("waypoint");
                int size = ((WaypointNode) node).getIcon().contains("gun") || ((WaypointNode) node).getIcon().contains("bank") || ((WaypointNode) node).getIcon().contains("car") || ((WaypointNode) node).getIcon().equals("r_arrow") ? 50 : 512;
                icon = new GuiTextureSprite(new ResourceLocation(GtwMapConstants.ID, "textures/gps/wp_" + ((WaypointNode) node).getIcon() + ".png"), 0, 0, size, size);
                WorldPosAutoStyleHandler position = new WorldPosAutoStyleHandler(node.getPosition().x, node.getPosition().z, icon);
                label.getStyle().addAutoStyleHandler(position);
                mapPane.add(label);
                newGpsNodeComponents.put(node, label);
            }
        }
        gpsNodeComponents.forEach((node, component) -> {
            if (!newGpsNodeComponents.containsKey(node)) {
                mapPane.remove(component);
            }
        });
        gpsNodeComponents.clear();
        gpsNodeComponents.putAll(newGpsNodeComponents);
    }

    public void refreshCustomMarker() {
        if (customMarkerComponent != null) {
            mapPane.remove(customMarkerComponent);
            customMarkerComponent = null;
        }
        WaypointNode customMarker = ClientEventHandler.gpsNavigator.getCustomWaypoint();
        if (customMarker == null) {
            return;
        }
        GuiTextureSprite icon = new GuiTextureSprite(new ResourceLocation(GtwMapConstants.ID, "textures/gps/wp_" + customMarker.getIcon() + ".png"), 0, 0, 50, 50);
        GuiComponent<?> label = new GuiLabel("") {
            @Override
            protected void bindLayerBounds() {
                boolean visible = getRenderMinX() > GuiMinimap.this.getRenderMinX() && getRenderMaxX() < GuiMinimap.this.getRenderMaxX() && getRenderMinY() > GuiMinimap.this.getRenderMinY() && getRenderMaxY() < GuiMinimap.this.getRenderMaxY();
                if (!visible)
                    return;
                GlStateManager.pushMatrix();
                GlStateManager.translate(getRenderMinX() + getWidth() / 2f, getRenderMinY() + getHeight() / 2f, 0);
                GlStateManager.rotate(180 + mc.player.rotationYaw, 0, 0, 1);
                GlStateManager.translate(-getRenderMinX() - getWidth() / 2f, -getRenderMinY() - getHeight() / 2f, 0);
            }

            @Override
            public void drawTexturedBackground(int mouseX, int mouseY, float partialTicks) {
                //super.drawTexturedBackground(mouseX, mouseY, partialTicks);

                float scale = (float) (1f * (10 / Math.sqrt(mapSize)));
                IGuiTexture renderTexture = style.getTexture();
                if (renderTexture != null) {
                    GlStateManager.enableBlend();
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(getWidth() / 2f, getHeight() / 2f, 0);
                    GlStateManager.translate(-getWidth() * scale / 2, -getHeight() * scale / 2, 0);
                    renderTexture.drawSprite(getScreenX(), getScreenY(), (int) (getWidth() * scale), (int) (getHeight() * scale));
                    GlStateManager.popMatrix();
                    GlStateManager.disableBlend();
                }
            }

            @Override
            protected void unbindLayerBounds() {
                boolean visible = getRenderMinX() > GuiMinimap.this.getRenderMinX() && getRenderMaxX() < GuiMinimap.this.getRenderMaxX() && getRenderMinY() > GuiMinimap.this.getRenderMinY() && getRenderMaxY() < GuiMinimap.this.getRenderMaxY();
                if (!visible)
                    return;
                GlStateManager.popMatrix();
            }
        }.setCssId("gps_node").setCssClass("waypoint");
        label.setHoveringText(Collections.singletonList(customMarker.getName()));
        WorldPosAutoStyleHandler position = new WorldPosAutoStyleHandler(customMarker.getPosition().x, customMarker.getPosition().z, icon);
        label.getStyle().addAutoStyleHandler(position);
        customMarkerComponent = label;
        mapPane.add(label);
    }

    private void updateViewport(Viewport newViewport, float partialTicks, boolean refreshTiles) {
        if (/*newViewport.width > 5000 || newViewport.height > 7000 || */newViewport.width < 50 || newViewport.height < 50)
            //DRAWLIFE if(newViewport.x < 6144 || newViewport.width+newViewport.x > 15232 || newViewport.y < 4512 || newViewport.height+newViewport.y > 13696 || newViewport.width < 50 || newViewport.height < 50)
            return;
        if (newViewport.width > 300 || newViewport.height > 300)
            return;
        if (mapPane.getWidth() == 0 || mapPane.getHeight() == 0) {
            System.out.println("Escaping from 0-world");
            return;
        }
        float xdif = (newViewport.width + newViewport.x) - Config.mapDims[1];
        float ydif = (newViewport.height + newViewport.y) - Config.mapDims[3];
        if (xdif > 0)
            newViewport.x -= xdif;
        if (ydif > 0)
            newViewport.y -= ydif;
        newViewport.x = Math.max(newViewport.x, Config.mapDims[0]);
        newViewport.y = Math.max(newViewport.y, Config.mapDims[2]);
        if (newViewport.width + newViewport.x - Config.mapDims[1] > 0)
            return;
        if (newViewport.height + newViewport.y - Config.mapDims[3] > 0)
            return;
        //mapPane.removeAllChilds();
        // System.out.println("Clear");
        //System.out.println("Viewporting to " + newViewport);

        float ux = newViewport.x < 0 ? newViewport.x - 400 : newViewport.x;
        float uy = newViewport.y < 0 ? newViewport.y - 400 : newViewport.y;
        float dw = (400 * mapPane.getWidth() / newViewport.width);
        float dh = (400 * mapPane.getHeight() / newViewport.height);

        float bx = (-(ux % 400) * mapPane.getWidth() / newViewport.width);
        float by = (-(uy % 400) * mapPane.getHeight() / newViewport.height);

        MapContainerClient mapContainer = (MapContainerClient) MapContainer.getInstance(true);

        List<PartPos> oldPoses = new ArrayList<>(partsStore.keySet());
        float x = (newViewport.x - (ux % 400) - 400);
        for (float dx = bx - dw; dx < bx + mapPane.getWidth() + dw; dx = dx + dw) {
            float z = (newViewport.y - (uy % 400) - 400);
            for (float dy = by - dh; dy < by + mapPane.getHeight() + dh; dy = dy + dh) {
                int x2 = (int) x;
                int z2 = (int) z;
                if (x2 < 0)
                    x2 -= 399;
                if (z2 < 0)
                    z2 -= 399;
                PartPos pos = new PartPos(x2 / 400, z2 / 400);
                GuiBigMap.PartPosAutoStyleHandler partPane = partsStore.get(pos);
                MapPartClient part = null;
                if (refreshTiles || partPane == null) {
                    part = (MapPartClient) mapContainer.requestTile(x2, z2, mc.world, mc.player);
                    part.refreshMapContents();
                }
                if (partPane == null) {
                    GuiPanel pane = (GuiPanel) new GuiPanel() {
                        @Override
                        protected void bindLayerBounds() {
                        }
                    }.getStyle()
                            .setTexture(new GuiTextureSprite(part.getLocation(), 0, 0, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE)).getOwner();
                    partPane = new GuiBigMap.PartPosAutoStyleHandler(pane, dx, dy, dw, dh);
                    pane.getStyle().addAutoStyleHandler(partPane);
                    partsStore.put(pos, partPane);
                    mapPane.add(pane);
                } else {
                    oldPoses.remove(pos);
                    partPane.update(dx, dy, dw, dh);
                }
                z = z + 400;
            }
            x = x + 400;
        }
        oldPoses.stream().map(partsStore::remove).map(GuiBigMap.PartPosAutoStyleHandler::getPartPane).forEach(mapPane::remove);

        //====================== Geo localisation ======================
        if (tackedObjectsPosCache.size() != (mc.world.playerEntities.size() + GtwMapApi.getTrackedObjects().size())) {
            trackedPlayers.entrySet().removeIf(p -> !mc.world.playerEntities.contains(p.getKey()));
            tackedObjectsPosCache.entrySet().removeIf(p -> {
                boolean remove = !trackedPlayers.containsValue(p.getKey()) && !GtwMapApi.getTrackedObjects().contains(p.getKey());
                if (remove) {
                    mapPane.remove(p.getValue().component);
                }
                return remove;
            });
            for (EntityPlayer player : mc.world.playerEntities) {
                if (!trackedPlayers.containsKey(player)) {
                    ITrackableObject<?> trackedObject = new ITrackableObject.TrackedEntity(player, player.getDisplayNameString(), "player_white");
                    trackedPlayers.put(player, trackedObject);
                    makeTrackedPoint(trackedObject, false);
                }
            }
            for (ITrackableObject<?> object : GtwMapApi.getTrackedObjects()) {
                if (!tackedObjectsPosCache.containsKey(object)) {
                    makeTrackedPoint(object, false);
                }
            }
        }
        for (EntityPosCache posCache : tackedObjectsPosCache.values()) {
            posCache.update(partialTicks);
        }

        viewport = newViewport;
        mapPane.getStyle().refreshCss(getGui(), false, EnumCssStyleProperty.LEFT, EnumCssStyleProperty.TOP, EnumCssStyleProperty.WIDTH, EnumCssStyleProperty.HEIGHT);
    }

    public void makeTrackedPoint(ITrackableObject<?> object, boolean rotateLabel) {
        int size = object.getIcon().contains("gun") || object.getIcon().contains("bank") || object.getIcon().contains("car") || object.getIcon().contains("r_arrow") ? 50 : 512;
        GuiTextureSprite icon = new GuiTextureSprite(new ResourceLocation(GtwMapConstants.ID, "textures/gps/wp_" + object.getIcon() + ".png"), 0, 0, size, size);
        WorldPosAutoStyleHandler pos = new WorldPosAutoStyleHandler(object.getPosX(1), object.getPosZ(1), icon);
        GuiLabel label = new GuiLabel("") {
            @Override
            protected void bindLayerBounds() {
                if (rotateLabel) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(getRenderMinX() + getWidth() / 2f, getRenderMinY() + getHeight() / 2f, 0);
                    GlStateManager.rotate(((ITrackableObject.TrackedEntity) object).getTrackedObject().rotationYaw + 90, 0, 0, 1);
                    GlStateManager.translate(-getRenderMinX() - getWidth() / 2f, -getRenderMinY() - getHeight() / 2f, 0);
                }
            }

            @Override
            public void drawTexturedBackground(int mouseX, int mouseY, float partialTicks) {
                if (object.renderPoliceCircleAroundRadius() > 0) {
                    boolean color = mc.player.ticksExisted % 60 >= 30;
                    GlStateManager.color(color ? 0.75f : 0, color ? 0 : 0.1f, color ? 0 : 1, 1);
                }
                //super.drawTexturedBackground(mouseX, mouseY, partialTicks);

                float scale = (float) (1.23f * (10 / Math.sqrt(mapSize)));
                IGuiTexture renderTexture = style.getTexture();
                if (renderTexture != null) {
                    GlStateManager.enableBlend();
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(getWidth() / 2f, getHeight() / 2f, 0);
                    GlStateManager.translate(-getWidth() * scale / 2, -getHeight() * scale / 2, 0);
                    renderTexture.drawSprite(getScreenX(), getScreenY(), (int) (getWidth() * scale), (int) (getHeight() * scale));
                    GlStateManager.popMatrix();
                    GlStateManager.disableBlend();
                }
            }

            @Override
            protected void unbindLayerBounds() {
                if (object.renderPoliceCircleAroundRadius() > 0) {
                    GlStateManager.enableAlpha();
                    // GL11.glEnable(GL_ALPHA_TEST);
                    int color = /*mc.player.ticksExisted % 60 >= 30 ? 0x20AA0000 : */0x200055AA;
                    //CircleBackground.drawDisk(getRenderMinX() + getWidth() / 2, getRenderMinY() + getHeight() / 2, 20, color, 0, 0, 360f);
                    float x = getRenderMinX() + getWidth() / 2;
                    float y = getRenderMinY() + getHeight() / 2;
                    float radius = object.renderPoliceCircleAroundRadius() * (100 / mapSize);
                    int i;
                    int triangleAmount = 50; //# of triangles used to draw circle
                    //In radians
                    float twicePi = 360;

                   /* GlStateManager.enableBlend();
                    //GL11.glEnable(GL_BLEND);
                    // GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.disableTexture2D();
                    //  GL11.glDisable(GL_TEXTURE_2D);
                    // GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                    Tessellator tessellator = Tessellator.getInstance();
                    BufferBuilder bufferbuilder = tessellator.getBuffer();
                    bufferbuilder.begin(7, DefaultVertexFormats.POSITION);
                    float f3 = (float) (color >> 4 & 255) / 255.0F;
                    float f = (float) (color >> 16 & 255) / 255.0F;
                    float f1 = (float) (color >> 8 & 255) / 255.0F;
                    float f2 = (float) (color & 255) / 255.0F;
                    GlStateManager.color(f, f1, f2, f3);
                    /*bufferbuilder.pos((float) x, (float) y, 0); // center of circle
                    for (i = 0; i <= triangleAmount; i++) {
                        //GL11.glColor4f(f, f1, f2, f3);
                        bufferbuilder.pos(
                                (float) (x + (radius * Math.sin((float) i * twicePi / (float) triangleAmount))),
                                (float) (y + (radius * Math.cos((float) i * twicePi / (float) triangleAmount))), 0
                        );
                    }*//*
                    bufferbuilder.pos(0, getScreenY() + getHeight(), 0.0D).endVertex();
                    bufferbuilder.pos(getScreenX() + getWidth(), getScreenY() + getHeight(), 0.0D).endVertex();
                    bufferbuilder.pos(getScreenX() + getWidth(), 0, 0.0D).endVertex();
                    bufferbuilder.pos(0, 0, 0.0D).endVertex();
                    tessellator.draw();
                    GlStateManager.enableTexture2D();
                    GlStateManager.disableBlend();*/

                    float f3 = (float) (color >> 24 & 255) / 255.0F;
                    float f = (float) (color >> 16 & 255) / 255.0F;
                    float f1 = (float) (color >> 8 & 255) / 255.0F;
                    float f2 = (float) (color & 255) / 255.0F;
                    Tessellator tessellator = Tessellator.getInstance();
                    BufferBuilder bufferbuilder = tessellator.getBuffer();
                    GlStateManager.enableBlend();
                    GlStateManager.disableTexture2D();
                    //GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                    GlStateManager.color(f, f1, f2, 0.12f);
                    bufferbuilder.begin(GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);
                   /* bufferbuilder.pos(0, getScreenY() + getHeight(), 0.0D).endVertex();
                    bufferbuilder.pos(getScreenX() + getWidth(), getScreenY() + getHeight(), 0.0D).endVertex();
                    bufferbuilder.pos(getScreenX() + getWidth(), 0, 0.0D).endVertex();
                    bufferbuilder.pos(0, 0, 0.0D).endVertex();*/
                    bufferbuilder.pos(x, y, 0).endVertex(); // center of circle
                    for (i = 0; i < (triangleAmount - 1); i++) {
                        //GL11.glColor4f(f, f1, f2, f3);
                        bufferbuilder.pos(
                                (float) (x + (radius * Math.sin((float) i * twicePi / (float) triangleAmount))),
                                (float) (y + (radius * Math.cos((float) i * twicePi / (float) triangleAmount))), 0
                        ).endVertex();
                    }
                    tessellator.draw();
                    GlStateManager.enableTexture2D();
                    GlStateManager.disableBlend();
                    GlStateManager.color(1, 1, 1, 1);
                }
                if (rotateLabel) {
                    GlStateManager.popMatrix();
                }
            }
        };
        mapPane.add(label.setHoveringText(Collections.singletonList(object.getDisplayName())).setCssId("custom_marker").setCssClass(object.smallIcon() ? "waypoint_small" : "waypoint").getStyle().addAutoStyleHandler(pos).getOwner());
        tackedObjectsPosCache.put(object, new EntityPosCache(object, label, pos));
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks, ComponentRenderContext renderContext) {
        float pX = (float) mc.player.prevPosX + (float) (mc.player.posX - mc.player.prevPosX) * partialTicks;
        float pZ = (float) mc.player.prevPosZ + (float) (mc.player.posZ - mc.player.prevPosZ) * partialTicks;
        updateViewport(new Viewport(pX - mapSize / 2, pZ - mapSize / 2, mapSize, mapSize), partialTicks, partialTicks >= 0.95f);
        super.drawBackground(mouseX, mouseY, partialTicks, renderContext);
    }

    @Override
    public void drawForeground(int mouseX, int mouseY, float partialTicks, ComponentRenderContext renderContext) {
        GlStateManager.disableTexture2D();
        GL11.glEnable(GL_SCISSOR_TEST);
        GuiAPIClientHelper.glScissor(mapPane.getScreenX(), mapPane.getScreenY(), mapPane.getWidth(), mapPane.getHeight());
        GlStateManager.color(87f / 255, 0, 127f / 255, 1);
        GpsNavigator nav = ClientEventHandler.gpsNavigator;
        if (nav.getRoute() != null) {
            Queue<GpsNode> copy = new LinkedList<>(nav.getRoute());
            GpsNode first = copy.poll(), second = null;
            while ((second = copy.poll()) != null) {
                float width = 5;
                // if (!viewport.contains(first.getPosition().x, first.getPosition().z) && !viewport.contains(second.getPosition().x, second.getPosition().z))
                //    continue;
                if (first.getBezierCurves().containsKey(second.getId())) {
                    BezierCurveLink link = first.getBezierCurves().get(second.getId());
                    List<Vector2f> points = link.getRenderPoints();
                    // GlStateManager.glLineWidth(10);
                    //GlStateManager.glBegin(GL11.GL_LINE_STRIP);
                    for (int i = 0; i < points.size() - 1; i++) {
                        Vector2f point1 = points.get(i);
                        Vector2f point2 = points.get(i + 1);
                        float startX = mapPane.getScreenX() + (point1.x - viewport.x) * mapPane.getWidth() / viewport.width;
                        float startY = mapPane.getScreenY() + (point1.y - viewport.y) * mapPane.getHeight() / viewport.height;
                        float endX = mapPane.getScreenX() + (point2.x - viewport.x) * mapPane.getWidth() / viewport.width;
                        float endY = mapPane.getScreenY() + (point2.y - viewport.y) * mapPane.getHeight() / viewport.height;
                        //GlStateManager.glVertex3f(startX, startY, 5);
                        //GlStateManager.glVertex3f(endX, endY, 5);                    //Draw fat line
                        float x1 = startX;
                        float y1 = startY;
                        float x2 = endX;
                        float y2 = endY;
                        float dx = x2 - x1;
                        float dy = y2 - y1;
                        float length = (float) Math.sqrt(dx * dx + dy * dy);
                        float nx = dy / length; // Normal x
                        float ny = -dx / length; // Normal y
                        // Adjust normals based on line width
                        nx *= width / 2.0f;
                        ny *= width / 2.0f;
                        // Begin drawing triangles
                        GlStateManager.glBegin(GL_TRIANGLE_FAN);
                        // First triangle
                        GL11.glVertex3f(x1 - nx, y1 - ny, 5.0f);
                        GL11.glVertex3f(x2 + nx, y2 + ny, 5.0f);
                        GL11.glVertex3f(x1 + nx, y1 + ny, 5.0f);
                        // Second triangle
                        GL11.glVertex3f(x1 - nx, y1 - ny, 5.0f);
                        GL11.glVertex3f(x2 - nx, y2 - ny, 5.0f);
                        GL11.glVertex3f(x2 + nx, y2 + ny, 5.0f);
                        // End drawing
                        //GL11.glEnd();

                        int J;
                        int triangleAmount = 20; //# of triangles used to draw circle
                        //In radians
                        float twicePi = (float) (2.0f * Math.PI);
                        float x = endX;
                        float y = endY;
                        float radius = 2.5f;
                        GlStateManager.glVertex3f(x, y, 0); // center of circle
                        for (J = 0; J <= triangleAmount; J++) {
                            GlStateManager.glVertex3f(
                                    (float) (x + (radius * Math.sin(0 + (float) J * twicePi / (float) triangleAmount))),
                                    (float) (y + (radius * Math.cos(0 + (float) J * twicePi / (float) triangleAmount))), 0
                            );
                        }
                        GlStateManager.glEnd();
                    }
                    //GlStateManager.glEnd();
                    //GlStateManager.glLineWidth(1);
                } else {
                    float startX = (first.getPosition().x - GuiMinimap.this.viewport.x) * GuiMinimap.this.mapPane.getWidth() / GuiMinimap.this.viewport.width + mapPane.getScreenX();
                    float startY = (first.getPosition().z - GuiMinimap.this.viewport.y) * GuiMinimap.this.mapPane.getHeight() / GuiMinimap.this.viewport.height + mapPane.getScreenY();
                    float endX = (second.getPosition().x - GuiMinimap.this.viewport.x) * GuiMinimap.this.mapPane.getWidth() / GuiMinimap.this.viewport.width + mapPane.getScreenX();
                    float endY = (second.getPosition().z - GuiMinimap.this.viewport.y) * GuiMinimap.this.mapPane.getHeight() / GuiMinimap.this.viewport.height + mapPane.getScreenY();
                    //Draw fat line
                    float x1 = startX;
                    float y1 = startY;
                    float x2 = endX;
                    float y2 = endY;
                    float dx = x2 - x1;
                    float dy = y2 - y1;
                    float length = (float) Math.sqrt(dx * dx + dy * dy);
                    float nx = dy / length; // Normal x
                    float ny = -dx / length; // Normal y
                    // Adjust normals based on line width
                    nx *= width / 2.0f;
                    ny *= width / 2.0f;
                    // Begin drawing triangles
                    GlStateManager.glBegin(GL_TRIANGLE_FAN);
                    // First triangle
                    GL11.glVertex3f(x1 - nx, y1 - ny, 5.0f);
                    GL11.glVertex3f(x2 + nx, y2 + ny, 5.0f);
                    GL11.glVertex3f(x1 + nx, y1 + ny, 5.0f);
                    // Second triangle
                    GL11.glVertex3f(x1 - nx, y1 - ny, 5.0f);
                    GL11.glVertex3f(x2 - nx, y2 - ny, 5.0f);
                    GL11.glVertex3f(x2 + nx, y2 + ny, 5.0f);
                    // End drawing
                    //GL11.glEnd();

                    int i;
                    int triangleAmount = 20; //# of triangles used to draw circle
                    //In radians
                    float twicePi = (float) (2.0f * Math.PI);
                    float x = endX;
                    float y = endY;
                    float radius = 2.5f;
                    GlStateManager.glVertex3f(x, y, 0); // center of circle
                    for (i = 0; i <= triangleAmount; i++) {
                        GlStateManager.glVertex3f(
                                (float) (x + (radius * Math.sin(0 + (float) i * twicePi / (float) triangleAmount))),
                                (float) (y + (radius * Math.cos(0 + (float) i * twicePi / (float) triangleAmount))), 0
                        );
                    }
                    GlStateManager.glEnd();
                }
                first = second;
            }
        }
        GL11.glDisable(GL_SCISSOR_TEST);
        GlStateManager.enableTexture2D();

        super.drawForeground(mouseX, mouseY, partialTicks, renderContext);
    }

    @Override
    public void guiClose() {
        super.guiClose();
        if (!(mc.currentScreen instanceof APIGuiScreen) || !(((APIGuiScreen) mc.currentScreen).getFrame() instanceof GuiBigMap)) {
            GtwMapMod.getNetwork().sendToServer(new S19PacketMapPartQuery(Integer.MIN_VALUE, Integer.MAX_VALUE));
            ((MapContainerClient) MapContainer.getInstance(true)).dirtyAll();
        }
    }

    @Override
    public List<ResourceLocation> getCssStyles() {
        return Collections.singletonList(STYLE);
    }

    @Override
    public void tick() {
        super.tick();
        mapSize = computeMapSize(mc.player);
    }

    @AllArgsConstructor
    public static class Viewport {
        protected float x, y, width, height;

        public boolean contains(float x, float z) {
            return x >= this.x && x <= this.x + width && z >= this.y && z <= this.y + height;
        }
    }

    @Override
    protected void bindLayerBounds() {
        if (!Minecraft.getMinecraft().getFramebuffer().isStencilEnabled())
            Minecraft.getMinecraft().getFramebuffer().enableStencil();

        GlStateManager.pushMatrix();
        //   CircleBackground.renderBackground(0, 0, 0, resolution.getScaledWidth(), resolution.getScaledHeight(), Color.RED.getRGB());
// Enable stencil testing
        /*glEnable(GL_STENCIL_TEST);
        glClear(GL_STENCIL_BUFFER_BIT);
        glStencilOp(GL_ZERO, GL_ZERO, GL_ZERO);
        glStencilFunc(GL_ALWAYS, 1, 0xFF);
        glStencilMask(0xFF);*/
        glEnable(GL_STENCIL_TEST);
        glColorMask(false, false, false, false);
        glDepthMask(false);
        glStencilFunc(GL_NEVER, 1, 0xFF);
        glStencilOp(GL_REPLACE, GL_KEEP, GL_KEEP);  // draw 1s on test fail (always)
        // draw stencil pattern
        glStencilMask(0xFF);
        glClear(GL_STENCIL_BUFFER_BIT);  // needs mask=0xFF

// Draw the circle to the stencil buffer
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_TRIANGLE_FAN);
        float cx = (getRenderMaxX() - getRenderMinX()) / 2f + getRenderMinX();
        float cy = (getRenderMaxY() - getRenderMinY()) / 2f + getRenderMinY();
        //glVertex3f(cx, cy, 0); // Center of the circle
        int num_segments = 100;
        float radius = (getRenderMaxX() - getRenderMinX()) / 2f;
        /*for (int i = 0; i <= num_segments; i++) {
            float theta = 2.0f * 3.1415926f * i / num_segments; // Current angle
            float x = (float) (radius * Math.cos(theta)); // Calculate the x component
            float y = (float) (radius * Math.sin(theta)); // Calculate the y component
            glVertex3f(x + cx, y + cy, 0); // Output vertex
        }*/
        float start = 0;
        float twicePi = (float) (2.0f * Math.PI);
        int triangleAmount = 50;
        for (int i = 0; i <= triangleAmount; i++) {
            GlStateManager.glVertex3f(
                    (float) (cx + (radius * Math.sin(start + (float) i * twicePi / (float) triangleAmount))),
                    (float) (cy + (radius * Math.cos(start + (float) i * twicePi / (float) triangleAmount))), 0
            );
        }
        glEnd();
        //CircleBackground.renderBackground(0, cx-radius, cy-radius, cx+radius, cy+radius, Color.YELLOW.getRGB());
        glEnable(GL_TEXTURE_2D);

// Enable stencil testing to only render within the circle
        //glStencilFunc(GL_EQUAL, 1, 0xFF);
        //glStencilMask(0x00);
        glColorMask(true, true, true, true);
        glDepthMask(true);
        glStencilMask(0x00);
        // draw where stencil's value is 0
        glStencilFunc(GL_EQUAL, 0, 0xFF);
        CircleBackground.drawDisk(cx, cy, radius + 3, 0xff444444, 0, 0, (float) (Math.PI * 2));
        // draw only where stencil's value is 1
        glStencilFunc(GL_EQUAL, 1, 0xFF);
        int i = GlStateManager.glGetError();
        if (i != 0) {
            String s = GLU.gluErrorString(i);
            System.out.println("GL ERROR: " + i + " " + s);
        }
        GlStateManager.translate(getRenderMinX() + getWidth() / 2f, getRenderMinY() + getHeight() / 2f, 0);
        GlStateManager.rotate(180 - mc.player.rotationYaw, 0, 0, 1);
        GlStateManager.translate(-getRenderMinX() - getWidth() / 2f, -getRenderMinY() - getHeight() / 2f, 0);
    }

    @Override
    protected void unbindLayerBounds() {
        if (GtwMapApi.isRenderPoliceBlinking()) {
            GlStateManager.enableAlpha();
            int color = mc.player.ticksExisted % 60 < 30 ? 0x55AA0000 : 0x550000AA;
            //GuiComponent.drawRect(0, 0, resolution.getScaledWidth(), resolution.getScaledHeight(), color);

            float f3 = (float) (color >> 24 & 255) / 255.0F;
            float f = (float) (color >> 16 & 255) / 255.0F;
            float f1 = (float) (color >> 8 & 255) / 255.0F;
            float f2 = (float) (color & 255) / 255.0F;
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferbuilder = tessellator.getBuffer();
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            //GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.color(f, f1, f2, f3);
            bufferbuilder.begin(7, DefaultVertexFormats.POSITION);
            bufferbuilder.pos(0, getScreenY() + getHeight(), 0.0D).endVertex();
            bufferbuilder.pos(getScreenX() + getWidth(), getScreenY() + getHeight(), 0.0D).endVertex();
            bufferbuilder.pos(getScreenX() + getWidth(), 0, 0.0D).endVertex();
            bufferbuilder.pos(0, 0, 0.0D).endVertex();
            tessellator.draw();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }

        glDisable(GL_STENCIL_TEST);
        GlStateManager.popMatrix();

        int i = GlStateManager.glGetError();
        if (i != 0) {
            String s = GLU.gluErrorString(i);
            System.out.println("GL ERROR: " + i + " " + s);
        }
    }

    @Setter
    @AllArgsConstructor
    public class WorldPosAutoStyleHandler implements AutoStyleHandler<ComponentStyleManager> {
        private float posX;
        private float posZ;
        private GuiTextureSprite icon;

        @Override
        public boolean handleProperty(EnumCssStyleProperty property, EnumSelectorContext context, ComponentStyleManager csm) {
            // Not refreshed after component init
            if (property == EnumCssStyleProperty.TEXTURE && icon != null) {
                csm.setTexture(icon);
                return true;
            }
            if (posX < viewport.x || posX > viewport.x + viewport.width || posZ < viewport.y || posZ > viewport.y + viewport.height) {
                csm.setVisible(false);
                return true;
            }
            csm.setVisible(true);
            // Refreshed after component init
            if (property == EnumCssStyleProperty.LEFT) {
                csm.getXPos().setAbsolute((posX - GuiMinimap.this.viewport.x) * GuiMinimap.this.mapPane.getWidth() / GuiMinimap.this.viewport.width - 4);
                return true;
            }
            if (property == EnumCssStyleProperty.TOP) {
                csm.getYPos().setAbsolute((posZ - GuiMinimap.this.viewport.y) * GuiMinimap.this.mapPane.getHeight() / GuiMinimap.this.viewport.height - 4);
                return true;
            }
            return false;
        }

        @Override
        public Collection<EnumCssStyleProperty> getModifiedProperties(ComponentStyleManager componentStyleManager) {
            return Arrays.asList(EnumCssStyleProperty.LEFT, EnumCssStyleProperty.TOP, EnumCssStyleProperty.TEXTURE);
        }
    }

    @AllArgsConstructor
    public class EntityPosCache {
        private final ITrackableObject<?> object;
        private final GuiLabel component;
        private final WorldPosAutoStyleHandler styleHandler;

        public void update(float partialTicks) {
            styleHandler.setPosX(object.getPosX(partialTicks));
            styleHandler.setPosZ(object.getPosZ(partialTicks));
        }
    }

    public float computeMapSize(EntityPlayer player) {
        float speed;
        if (player.isRiding()) {
            speed = (float) (player.getRidingEntity().motionX * player.getRidingEntity().motionX + player.getRidingEntity().motionZ * player.getRidingEntity().motionZ);
        } else {
            speed = (float) (player.motionX * player.motionX + player.motionZ * player.motionZ);
        }
        float size = Math.min(250, Math.max(100, 100 + speed * 800));
        //System.out.println("Speed: " + speed + " Size: " + size);
        if (Math.abs(mapSize - size) > 25 || Math.abs(targetMapSize - size) > 10) {
            if (mapSizeChangeCooldown > 0) {
                mapSizeChangeCooldown--;
            } else {
                targetMapSize = size;
                mapSizeChangeCooldown = 20;
            }
        } else if (targetMapSize == mapSize) {
            mapSizeChangeCooldown = 20;
        }
        size = mapSize;
        if (Math.abs(targetMapSize - mapSize) > 5) {
            if (mapSize < targetMapSize) {
                size += 5;
            } else if (mapSize > targetMapSize) {
                size -= 5;
            }
        } else {
            size = targetMapSize;
        }
        return size;
    }
}
