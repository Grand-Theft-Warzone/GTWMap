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
import fr.aym.acsguis.cssengine.style.EnumCssStyleProperties;
import fr.aym.acsguis.event.listeners.mouse.IMouseClickListener;
import fr.aym.acsguis.event.listeners.mouse.IMouseExtraClickListener;
import fr.aym.acsguis.event.listeners.mouse.IMouseMoveListener;
import fr.aym.acsguis.utils.GuiTextureSprite;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.client.ClientEventHandler;
import fr.aym.gtwmap.client.gps.GpsNavigator;
import fr.aym.gtwmap.common.gps.GpsNode;
import fr.aym.gtwmap.common.gps.GpsNodes;
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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector3f;
import java.awt.*;
import java.util.List;
import java.util.*;

public class GuiMapTest extends GuiFrame {
    public static final ResourceLocation STYLE = new ResourceLocation(GtwMapConstants.ID, "css/gui_map.css");
    public static int loadingTiles;

    private GuiMinimap.Viewport viewport;
    private float playerX, playerZ;
    private boolean viewInitialized;

    public int mouseClickX = 0, mouseClickY = 0;
    private EditMode editMode = EditMode.VIEW_NODES;

    private GuiPanel mapPane;
    private final Map<PartPos, GuiPanel> partsStore = new HashMap<>();
    private WorldPosAutoStyleHandler myPos;
    private GpsNode selectedNode;

    private final GpsNodes gpsNodes = GpsNodes.getInstance();
    private final Map<GpsNode, GuiComponent<?>> gpsNodeComponents = new HashMap<>();

    public GuiMapTest() {
        super(new GuiScaler.AdjustFullScreen());
        setCssId("root");
        setPauseGame(false);
        setNeedsCssReload(true);

        this.viewport = new GuiMinimap.Viewport((float) (mc.player.posX - 40), (float) (mc.player.posZ - 40), 80, 80);

        final GuiPanel pane = new GuiPanel();
        pane.setCssId("container");
        mapPane = new GuiPanel();
        mapPane.setCssId("map_pane");
        pane.add(mapPane);

        pane.addClickListener((mouseX, mouseY, mouseButton) -> {
            if (mouseButton == 1) {
                handleRightClick(mouseX, mouseY);
            }
        });
        add(pane);
        pane.addMoveListener(new IMouseMoveListener() {
            @Override
            public void onMouseUnhover(int mouseX, int mouseY) {
                mouseClickX = 0;
                mouseClickY = 0;
            }

            @Override
            public void onMouseMoved(int mouseX, int mouseY) {
                if (mouseClickX != 0 && (mouseX != mouseClickX || mouseY != mouseClickY)) {
                    updateViewport(new GuiMinimap.Viewport(viewport.x - (mouseX - mouseClickX) / 1, viewport.y - (mouseY - mouseClickY) / 1, viewport.width, viewport.height));
                    mouseClickX = mouseX;
                    mouseClickY = mouseY;
                }
            }

            @Override
            public void onMouseHover(int mouseX, int mouseY) {
            }
        });
        pane.addExtraClickListener(new IMouseExtraClickListener() {
            @Override
            public void onMouseReleased(int mouseX, int mouseY, int mouseButton) {
                mouseClickX = 0;
                mouseClickY = 0;
            }

            @Override
            public void onMousePressed(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton == 0) {
                    mouseClickX = mouseX;
                    mouseClickY = mouseY;
                }
            }

            @Override
            public void onMouseDoubleClicked(int mouseX, int mouseY, int mouseButton) {
            }
        });
        pane.addWheelListener(dWheel -> {

            if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                selectedNode = null;
                if(editMode == EditMode.VIEW)
                    return;
                if(dWheel > 0) {
                    editMode = EditMode.values()[(editMode.ordinal() + 1) % EditMode.values().length];
                    if(editMode == EditMode.VIEW)
                       editMode = EditMode.VIEW_NODES;
                } else {
                    editMode = EditMode.values()[(editMode.ordinal() + EditMode.values().length - 1) % EditMode.values().length];
                    if(editMode == EditMode.VIEW)
                        editMode = EditMode.EDIT_ONEWAY_LINK;
                }
                return;
            }

            //double oldW = pane.getxSlider().getValue(), oldH = pane.getySlider().getValue();
            float amount = dWheel / 60;
            //System.out.println("From amount " + amount + " " + ((float)(amount*-1/4/100)) + " " + ((float)(amount/4)));
            if (dWheel > 0)
                amount = MathHelper.clamp((float) (amount / 4), 1.111111F, 10);
            else
                amount = MathHelper.clamp((float) (amount * -1 / 2.5F), 0, 0.999999F);
            updateViewport(new GuiMinimap.Viewport(viewport.x, viewport.y, (int) (viewport.width * amount), (int) (viewport.height * amount)));
            //System.out.println(dWheel + " " + amount);
        });

        add(new GuiLabel(2, 2, getWidth() - 4, 20, "Grand Theft Warzone Map. Right-click to set a custom marker.").setCssId("title"));
        myPos = new WorldPosAutoStyleHandler(playerX, playerZ);
        mapPane.add(new GuiLabel("V").setHoveringText(Collections.singletonList("Your position")).setCssId("custom_marker").setCssClass("waypoint").getStyle().addAutoStyleHandler(myPos).getOwner());

        if (editMode != EditMode.VIEW) {
            for (GpsNode node : GpsNodes.getInstance().getNodes()) {
                GuiComponent<?> label = new GuiLabel("N").setCssId("gps_node").setCssClass("waypoint");
                label.addClickListener((mouseX, mouseY, mouseButton) -> {
                    System.out.println("Click " + mouseButton);
                    if(mouseButton != 1)
                        return;
                    handleNodeRightClick(node);
                });
                WorldPosAutoStyleHandler position = new WorldPosAutoStyleHandler(node.getPosition().x, node.getPosition().z);
                label.getStyle().addAutoStyleHandler(position);
                gpsNodeComponents.put(node, label);
                mapPane.add(label);
            }
        }

        //ClientEventHandler.gpsNavigator.setPath(mc.player, new Vec3d(309, 5, 77));
    }

    private void handleNodeRightClick(GpsNode node) {
        switch (editMode) {
            case REMOVE_NODES:
                node.delete(gpsNodes, true);
                mapPane.remove(gpsNodeComponents.remove(node));
                break;
            case EDIT_LINKS:
            case EDIT_ONEWAY_LINK:
                if(selectedNode == null) {
                    selectedNode = node;
                } else {
                    if(selectedNode.getNeighbors(gpsNodes).contains(node)) {
                        selectedNode.removeNeighbor(gpsNodes, node, true);
                        if(editMode == EditMode.EDIT_LINKS) {
                            node.removeNeighbor(gpsNodes, selectedNode, true);
                        }
                    } else {
                        selectedNode.addNeighbor(gpsNodes, node, true);
                        if (editMode == EditMode.EDIT_LINKS && !node.getNeighbors(gpsNodes).contains(selectedNode)) {
                            node.addNeighbor(gpsNodes, selectedNode, true);
                        }
                    }
                    selectedNode = null;
                }
                break;
        }
    }

    private void handleRightClick(int mouseX, int mouseY) {
        if (editMode == EditMode.VIEW) {
            boolean b = MapApp.customPoint != null && MapApp.customPoint.isHovered();
            if (MapApp.customPoint != null) {
                mapPane.remove(MapApp.customPoint);
                MapApp.customPoint = null;
            }
            if (!b) {
                final MapApp.MapWaypoint w = new MapApp.MapWaypoint(mouseX - mapPane.getScreenX() - 4, mouseY - mapPane.getScreenY() - 2, "Custom point", "X");
                mapPane.add(MapApp.customPoint = new GuiLabel(w.getIcon()) {
                    @Override
                    public void drawForeground(int mouseX, int mouseY, float partialTicks) {
                        super.drawForeground(mouseX, mouseY, partialTicks);
                        GL11.glDisable(GL11.GL_SCISSOR_TEST);
                        if (this.isHovered()) {
                            GuiAPIClientHelper.drawHoveringText(Arrays.asList(w.getLabel()), mouseX, mouseY);
                            RenderHelper.disableStandardItemLighting();
                        }
                        GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    }
                }.setCssId("custom_marker").setCssClass("waypoint"));
                float worldX = viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth());
                float worldZ = viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight());
                //System.out.println("World coords : " + worldX + " " + worldZ);
                MapApp.customPoint.getStyle().addAutoStyleHandler(new AutoStyleHandler<ComponentStyleManager>() {
                    @Override
                    public boolean handleProperty(EnumCssStyleProperties property, EnumSelectorContext context, ComponentStyleManager csm) {
                        if (property == EnumCssStyleProperties.LEFT) {
                            float guiX = (worldX - viewport.x) * mapPane.getWidth() / viewport.width;
                            csm.getXPos().setAbsolute(guiX);
                            //System.out.println("Cmp guiX " + guiX + " " + worldX);
                            return true;
                        }
                        if (property == EnumCssStyleProperties.TOP) {
                            float guiY = (worldZ - viewport.y) * mapPane.getHeight() / viewport.height;
                            csm.getYPos().setAbsolute(guiY);
                            // System.out.println("Cmp guiY " + guiY + " " + worldZ);
                            //csm.setZLevel(30);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public Collection<EnumCssStyleProperties> getModifiedProperties(ComponentStyleManager componentStyleManager) {
                        return Arrays.asList(EnumCssStyleProperties.LEFT, EnumCssStyleProperties.TOP);
                    }
                });
            }
        } else if(editMode == EditMode.ADD_NODES) {
            float worldX = viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth());
            float worldZ = viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight());
            GpsNode node = new GpsNode(new Vector3f(worldX, 0, worldZ), new HashSet<>());
            node.create(gpsNodes, true);
            GuiComponent<?> label = new GuiLabel("N").setCssId("gps_node").setCssClass("waypoint");
            label.addClickListener((mouseX2, mouseY2, mouseButton) -> {
                System.out.println("Click " + mouseButton);
                if(mouseButton != 1)
                    return;
                handleNodeRightClick(node);
            });
            WorldPosAutoStyleHandler position = new WorldPosAutoStyleHandler(node.getPosition().x, node.getPosition().z);
            label.getStyle().addAutoStyleHandler(position);
            gpsNodeComponents.put(node, label);
            mapPane.add(label);
        } else {
           // selectedNode = null;
        }
    }

    private void updateViewport(GuiMinimap.Viewport newViewport) {
        if (/*newViewport.width > 5000 || newViewport.height > 7000 || */newViewport.width < 50 || newViewport.height < 50)
            //DRAWLIFE if(newViewport.x < 6144 || newViewport.width+newViewport.x > 15232 || newViewport.y < 4512 || newViewport.height+newViewport.y > 13696 || newViewport.width < 50 || newViewport.height < 50)
            return;
        if (mapPane.getWidth() == 0 || mapPane.getHeight() == 0) {
            System.out.println("Escaping from 0-world");
            return;
        }
        playerX = (float) mc.player.posX;
        playerZ = (float) mc.player.posZ;
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
        int dw = (int) (400 * mapPane.getWidth() / newViewport.width);
        int dh = (int) (400 * mapPane.getHeight() / newViewport.height);

        int bx = (int) (-(ux % 400) * mapPane.getWidth() / newViewport.width);
        int by = (int) (-(uy % 400) * mapPane.getHeight() / newViewport.height);

        MapContainerClient mapContainer = (MapContainerClient) MapContainer.getInstance(true);

        countx = 0;
        List<PartPos> oldPoses = new ArrayList<>(partsStore.keySet());
        int x = (int) (newViewport.x - (ux % 400) - 400);
        for (int dx = bx - dw; dx < bx + mapPane.getWidth() + dw; dx = dx + dw) {
            county = 0;
            int z = (int) (newViewport.y - (uy % 400) - 400);
            for (int dy = by - dh; dy < by + mapPane.getHeight() + dh; dy = dy + dh) {
                PartPos pos = new PartPos(x / 400, z / 400);
                MapPartClient part = (MapPartClient) mapContainer.requestTile(x, z, mc.world, mc.player);
                part.refreshMapContents();
                int finalDx = dx;
                int finalDy = dy;

                GuiPanel partPane = partsStore.get(pos);
                if (partPane == null) {
                    partPane = (GuiPanel) new GuiPanel().getStyle()
                            //TODO CHANGE WHEN FIXED IN ACSGUIS
                            .setTexture(new GuiTextureSprite(part.getLocation(), 0, 0, 400, 400) {
                                @Override
                                public void drawSprite(int x, int y, int spriteWidth, int spriteHeight, int uOffset, int vOffset, int textureWidth, int textureHeight, int width, int height) {
                                    //That fdp is loading the texture himself, we don't want that !! (TODO change in ACsGuis)
                                    Minecraft.getMinecraft().renderEngine.bindTexture(part.getLocation());
                                    Gui.drawScaledCustomSizeModalRect(x, y,
                                            0 + uOffset, 0 + vOffset,
                                            (int) Math.ceil(textureWidth * 1), (int) Math.ceil(textureHeight * 1),
                                            (int) Math.ceil(spriteWidth * 1), (int) Math.ceil(spriteHeight * 1),
                                            400, 400);
                                }
                            }).getOwner();
                    partsStore.put(pos, partPane);
                    mapPane.add(partPane);
                } else {
                    oldPoses.remove(pos);
                }
                partPane.getStyle().getAutoStyleHandlers().clear();
                partPane.getStyle().addAutoStyleHandler(new AutoStyleHandler<ComponentStyleManager>() {
                    @Override
                    public boolean handleProperty(EnumCssStyleProperties property, EnumSelectorContext context, ComponentStyleManager csm) {
                        if (property == EnumCssStyleProperties.LEFT) {
                            csm.getXPos().setAbsolute(finalDx);
                            return true;
                        }
                        if (property == EnumCssStyleProperties.TOP) {
                            csm.getYPos().setAbsolute(finalDy);
                            return true;
                        }
                        if (property == EnumCssStyleProperties.WIDTH) {
                            csm.getWidth().setAbsolute(dw);
                            return true;
                        }
                        if (property == EnumCssStyleProperties.HEIGHT) {
                            csm.getHeight().setAbsolute(dh);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public Collection<EnumCssStyleProperties> getModifiedProperties(ComponentStyleManager componentStyleManager) {
                        return Arrays.asList(EnumCssStyleProperties.LEFT, EnumCssStyleProperties.TOP, EnumCssStyleProperties.WIDTH, EnumCssStyleProperties.HEIGHT);
                    }
                });
                z = z + 400;
                county++;
            }
            x = x + 400;
            countx++;
        }
        oldPoses.stream().map(partsStore::remove).forEach(mapPane::remove);

        //====================== Waypoints ======================
        //TODO DON'T RECREATE COMPONENTS
        /*for (final MapApp.MapWaypoint w : MapApp.waypoints) {
            GuiLabel label = new GuiLabel(w.getIcon()) {
                @Override
                public void drawForeground(int mouseX, int mouseY, float partialTicks) {
                    super.drawForeground(mouseX, mouseY, partialTicks);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    if (this.isHovered()) {
                        GuiAPIClientHelper.drawHoveringText(Arrays.asList(w.getLabel()), mouseX, mouseY);
                        //GlStateManager.disableDepth();
                        //GlStateManager.enableLighting();
                        RenderHelper.disableStandardItemLighting();
                    }
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                }
            };
            label.setCssClass("waypoint");
            label.getStyle().addAutoStyleHandler(new AutoStyleHandler<ComponentStyleManager>() {
                @Override
                public boolean handleProperty(EnumCssStyleProperties property, EnumSelectorContext context, ComponentStyleManager csm) {
                    if (property == EnumCssStyleProperties.COLOR) {
                        csm.setForegroundColor(w.getColor());
                        return true;
                    }
                    if (property == EnumCssStyleProperties.LEFT) {
                        csm.getXPos().setRelative(w.getX() - newViewport.x, CssValue.Unit.RELATIVE_INT);
                        return true;
                    }
                    if (property == EnumCssStyleProperties.TOP) {
                        csm.getYPos().setRelative(w.getY() - newViewport.y, CssValue.Unit.RELATIVE_INT);
                        return true;
                    }
                    return false;
                }

                @Override
                public Collection<EnumCssStyleProperties> getModifiedProperties(ComponentStyleManager componentStyleManager) {
                    return Arrays.asList(EnumCssStyleProperties.COLOR, EnumCssStyleProperties.LEFT, EnumCssStyleProperties.TOP);
                }
            });
            mapPane.add(label);
        }
        if (MapApp.customPoint != null) {//TODO UPDATE SYSTEM
            mapPane.add(MapApp.customPoint);
        }*/

        //====================== Geo localisation ======================
        //TODO

        myPos.setPosX(playerX);
        myPos.setPosZ(playerZ);

        viewport = newViewport;
        //TODO ONLY REFRESH POSITIONS
        mapPane.getStyle().refreshCss(false, "viewport_upd");
    }

    private int countx, county;

    @Override
    public void drawForeground(int mouseX, int mouseY, float partialTicks) {
        // Draw links between nodes
        Set<UUID> renderedNodes = new HashSet<>();
        GlStateManager.disableTexture2D();
        for(Map.Entry<GpsNode, GuiComponent<?>> entry : gpsNodeComponents.entrySet()) {
            renderedNodes.add(entry.getKey().getId());
            for(GpsNode neighbor : entry.getKey().getNeighbors(gpsNodes)) {
                if(renderedNodes.contains(neighbor.getId()) && neighbor.getNeighbors(gpsNodes).contains(entry.getKey()))
                    continue;
                GuiComponent<?> component = gpsNodeComponents.get(neighbor);
                if(component == null)
                    continue;
                float startX = entry.getValue().getScreenX();
                float startY = entry.getValue().getScreenY();
                float endX = component.getScreenX();
                float endY = component.getScreenY();
                if(neighbor.getNeighbors(gpsNodes).contains(entry.getKey())) {
                    GlStateManager.color(1, 1, 1, 1);
                    GlStateManager.glBegin(GL11.GL_LINES);
                    GlStateManager.glVertex3f(startX, startY, 5);
                    GlStateManager.glVertex3f(endX, endY, 5);
                    GlStateManager.glEnd();
                } else {
                    GlStateManager.color(1, 0, 0f, 1);
                    GlStateManager.glBegin(GL11.GL_LINES);
                    GlStateManager.glVertex3f(startX, startY, 5);
                    GlStateManager.glVertex3f(startX + (endX - startX) / 2, startY + (endY - startY) / 2, 5);
                    GlStateManager.glEnd();
                    GlStateManager.color(0, 1, 0f, 1);
                    GlStateManager.glBegin(GL11.GL_LINES);
                    GlStateManager.glVertex3f(startX + (endX - startX) / 2, startY + (endY - startY) / 2, 5);
                    GlStateManager.glVertex3f(endX, endY, 5);
                    GlStateManager.glEnd();
                }
            }
        }
        if(selectedNode != null) {
            GuiComponent<?> component = gpsNodeComponents.get(selectedNode);
            if(component != null) {
                float startX = component.getScreenX();
                float startY = component.getScreenY();
                float endX = mouseX;
                float endY = mouseY;
                if (editMode == EditMode.EDIT_LINKS) {
                    GlStateManager.color(1, 1, 1, 1);
                    GlStateManager.glBegin(GL11.GL_LINES);
                    GlStateManager.glVertex3f(startX, startY, 5);
                    GlStateManager.glVertex3f(endX, endY, 5);
                    GlStateManager.glEnd();
                } else {
                    GlStateManager.color(1, 0, 0f, 1);
                    GlStateManager.glBegin(GL11.GL_LINES);
                    GlStateManager.glVertex3f(startX, startY, 5);
                    GlStateManager.glVertex3f(startX + (endX - startX) / 2, startY + (endY - startY) / 2, 5);
                    GlStateManager.glEnd();
                    GlStateManager.color(0, 1, 0f, 1);
                    GlStateManager.glBegin(GL11.GL_LINES);
                    GlStateManager.glVertex3f(startX + (endX - startX) / 2, startY + (endY - startY) / 2, 5);
                    GlStateManager.glVertex3f(endX, endY, 5);
                    GlStateManager.glEnd();
                }
            }
        }

        GpsNavigator nav = ClientEventHandler.gpsNavigator;
        if (nav.getRoute() != null) {
            Queue<GpsNode> copy = new LinkedList<>(nav.getRoute());
            GpsNode first = copy.poll(), second = null;
            while((second = copy.poll()) != null) {
                GuiComponent<?> component1 = gpsNodeComponents.get(first);
                GuiComponent<?> component2 = gpsNodeComponents.get(second);
                if(component1 == null || component2 == null)
                    continue;
                float startX = component1.getScreenX();
                float startY = component1.getScreenY();
                float endX = component2.getScreenX();
                float endY = component2.getScreenY();
                //Draw fat line
                GlStateManager.color(0, 0, 1, 1);
                GlStateManager.glLineWidth(10);
                GlStateManager.glBegin(GL11.GL_LINES);
                GlStateManager.glVertex3f(startX, startY, 5);
                GlStateManager.glVertex3f(endX, endY, 5);
                GlStateManager.glEnd();
                GlStateManager.glLineWidth(1);
                first = second;
            }
        }

        GlStateManager.enableTexture2D();

        super.drawForeground(mouseX, mouseY, partialTicks);
        int x = (int) (viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth()));
        int z = (int) (viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight()));
        mc.fontRenderer.drawString("x= " + x + " z=" + z, mouseX + 10, mouseY + 10, Color.WHITE.getRGB());
        if (loadingTiles > 0) {
            mc.fontRenderer.drawString(loadingTiles + " tiles loading", 2, getHeight() - 21, Color.CYAN.getRGB());
        }
        mc.fontRenderer.drawString(countx + "*" + county + " (" + (countx * county) + ") tiles displayed", 2, getHeight() - 11, Color.WHITE.getRGB());

        String modeTitle = null;
        int nodeColor = 0xFFFFFFFF;
        switch (editMode) {
            case VIEW:
            case VIEW_NODES:
                modeTitle = "View mode";
                break;
            case ADD_NODES:
                modeTitle = "Right click to add gps nodes";
                nodeColor = 0xFF00FF00;
                break;
            case REMOVE_NODES:
                modeTitle = "Right click on a gps node to remove it";
                nodeColor = 0xFFFF0000;
                break;
            case EDIT_LINKS:
                modeTitle = "Right click on two nodes to add/remove a link between them";
                nodeColor = Color.YELLOW.getRGB();
                break;
            case EDIT_ONEWAY_LINK:
                modeTitle = "Right click on two nodes in a specific order to add/remove a one way link between them";
                nodeColor = Color.MAGENTA.getRGB();
                break;
        }
        mc.fontRenderer.drawString(modeTitle, 2, 2, nodeColor);
    }

    @Override
    public void guiClose() {
        super.guiClose();
        GtwMapMod.getNetwork().sendToServer(new S19PacketMapPartQuery(Integer.MIN_VALUE, Integer.MAX_VALUE));
        ((MapContainerClient) MapContainer.getInstance(true)).dirtyAll();
    }

    @Override
    public List<ResourceLocation> getCssStyles() {
        return Collections.singletonList(STYLE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!viewInitialized) {
            viewInitialized = true;
            updateViewport(new GuiMinimap.Viewport((float) (mc.player.posX - viewport.width / 2), (float) (mc.player.posZ - viewport.height / 2), viewport.width, viewport.height));
        }
    }

    public enum EditMode {
        VIEW,
        VIEW_NODES,
        ADD_NODES,
        REMOVE_NODES,
        EDIT_LINKS,
        EDIT_ONEWAY_LINK
    }

    @Setter
    @AllArgsConstructor
    public class WorldPosAutoStyleHandler implements AutoStyleHandler<ComponentStyleManager> {
        private float posX;
        private float posZ;

        @Override
        public boolean handleProperty(EnumCssStyleProperties property, EnumSelectorContext context, ComponentStyleManager csm) {
            if (property == EnumCssStyleProperties.LEFT) {
                csm.getXPos().setAbsolute((posX - GuiMapTest.this.viewport.x) * GuiMapTest.this.mapPane.getWidth() / GuiMapTest.this.viewport.width - 4);
                return true;
            }
            if (property == EnumCssStyleProperties.TOP) {
                csm.getYPos().setAbsolute((posZ - GuiMapTest.this.viewport.y) * GuiMapTest.this.mapPane.getHeight() / GuiMapTest.this.viewport.height - 4);
                return true;
            }
            return false;
        }

        @Override
        public Collection<EnumCssStyleProperties> getModifiedProperties(ComponentStyleManager componentStyleManager) {
            return Arrays.asList(EnumCssStyleProperties.LEFT, EnumCssStyleProperties.TOP);
        }
    }
}
