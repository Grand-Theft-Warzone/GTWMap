package fr.aym.gtwmap.client.gui;

import fr.aym.acsguis.api.ACsGuiApi;
import fr.aym.acsguis.api.GuiAPIClientHelper;
import fr.aym.acsguis.component.GuiComponent;
import fr.aym.acsguis.component.layout.GuiScaler;
import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.acsguis.component.panel.GuiPanel;
import fr.aym.acsguis.component.style.AutoStyleHandler;
import fr.aym.acsguis.component.style.ComponentStyleManager;
import fr.aym.acsguis.component.textarea.GuiLabel;
import fr.aym.acsguis.component.textarea.GuiTextField;
import fr.aym.acsguis.cssengine.selectors.EnumSelectorContext;
import fr.aym.acsguis.cssengine.style.EnumCssStyleProperty;
import fr.aym.acsguis.event.listeners.mouse.IMouseExtraClickListener;
import fr.aym.acsguis.event.listeners.mouse.IMouseMoveListener;
import fr.aym.acsguis.utils.ComponentRenderContext;
import fr.aym.acsguis.utils.GuiTextureSprite;
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
import fr.aym.gtwmap.network.SCMessagePlayerList;
import fr.aym.gtwmap.utils.Config;
import fr.aym.gtwmap.utils.GtwMapConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import org.joml.Vector2f;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector3f;
import java.awt.*;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;

public class GuiBigMap extends GuiFrame {
    public static final ResourceLocation STYLE = new ResourceLocation(GtwMapConstants.ID, "css/gui_map.css");
    public static int loadingTiles;

    private GuiMinimap.Viewport viewport;
    private float playerX, playerZ;
    private boolean viewInitialized;

    public int mouseClickX = 0, mouseClickY = 0;
    private EditMode editMode = EditMode.VIEW;

    private final GuiPanel mapPane;
    private final Map<PartPos, PartPosAutoStyleHandler> partsStore = new HashMap<>();
    private final Map<String, ITrackableObject<?>> trackedPlayers = new HashMap<>();
    private final Map<ITrackableObject<?>, EntityPosCache> tackedObjectsPosCache = new HashMap<>();
    private int lastObjectsCount;

    private GpsNode selectedNode;
    private final BezierCurveLink selectedLink = new BezierCurveLink();

    private final GpsNodes gpsNodes = GpsNodes.getInstance();
    private final Map<GpsNode, GuiComponent<?>> gpsNodeComponents = new HashMap<>();
    private GuiComponent<?> customMarkerComponent;

    public GuiBigMap(boolean adminMode) {
        super(new GuiScaler.AdjustFullScreen());
        setCssId("root");
        setPauseGame(false);
        setNeedsCssReload(true);
        if (adminMode) {
            editMode = EditMode.VIEW_NODES;
        }

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
                    float zoomX = viewport.width / 400;
                    float zoomY = viewport.height / 400;
                    updateViewport(new GuiMinimap.Viewport(viewport.x - (mouseX - mouseClickX) * zoomX, viewport.y - (mouseY - mouseClickY) * zoomY, viewport.width, viewport.height));
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
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                selectedNode = null;
                selectedLink.getControlPoints().clear();
                if (editMode == EditMode.VIEW)
                    return;
                if (dWheel < 0) {
                    editMode = EditMode.values()[(editMode.ordinal() + 1) % EditMode.values().length];
                    if (editMode == EditMode.VIEW)
                        editMode = EditMode.VIEW_NODES;
                } else {
                    editMode = EditMode.values()[(editMode.ordinal() + EditMode.values().length - 1) % EditMode.values().length];
                    if (editMode == EditMode.VIEW)
                        editMode = EditMode.EDIT_ONEWAY_LINK;
                }
                return;
            }

            float worldMouseXBeforeZoom = viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth());
            float worldMouseYBeforeZoom = viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight());
            float amount = dWheel / 60;
            if (dWheel > 0)
                amount = MathHelper.clamp(amount / 4, 1.111111F, 10);
            else
                amount = MathHelper.clamp(amount * -1 / 2.5F, 0, 0.999999F);
            float newWidth = viewport.width * amount;
            float newHeight = viewport.height * amount;

            float worldMouseXAfterZoom = viewport.x + (mouseX - mapPane.getRenderMinX()) * newWidth / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth());
            float worldMouseYAfterZoom = viewport.y + (mouseY - mapPane.getRenderMinY()) * newHeight / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight());
            float newViewportX = viewport.x + (worldMouseXBeforeZoom - worldMouseXAfterZoom);
            float newViewportY = viewport.y + (worldMouseYBeforeZoom - worldMouseYAfterZoom);
            updateViewport(new GuiMinimap.Viewport(newViewportX, newViewportY, newWidth, newHeight));
        });

        add(new GuiLabel(2, 2, (int) (getWidth() - 4), 20, I18n.format("gui.map.title")).setCssId("title"));
        for (EntityPlayer player : mc.world.playerEntities) {
            boolean self = player == mc.player;
            ITrackableObject<?> trackedObject = new ITrackableObject.TrackedEntity(player, self ? I18n.format("gui.map.you") : player.getDisplayNameString(), self ? "r_arrow_white" : "player_white");
            trackedPlayers.put(player.getName(), trackedObject);
            makeTrackedPoint(trackedObject, self);
        }
        for (ITrackableObject<?> object : GtwMapApi.getTrackedObjects()) {
            makeTrackedPoint(object, false);
        }
        lastObjectsCount = mc.world.playerEntities.size() + GtwMapApi.getTrackedObjects().size();
        refreshGpsNodeComponents();
        refreshCustomMarker();
    }

    private void addGpsNodeComponent(Map<GpsNode, GuiComponent<?>> gpsNodeComponents, GpsNode node) {
        GuiTextureSprite icon = null;
        if (node instanceof WaypointNode) {
            int size = ((WaypointNode) node).getIconSize();
            icon = new GuiTextureSprite(new ResourceLocation(GtwMapConstants.ID, "textures/gps/wp_" + ((WaypointNode) node).getIcon() + ".png"), 0, 0, size, size);
        }
        GuiComponent<?> label = new GuiLabel(icon != null ? "" : "N").setCssId("gps_node").setCssClass("waypoint");
        label.addClickListener((mouseX2, mouseY2, mouseButton) -> {
            if (mouseButton != 1)
                return;
            handleNodeRightClick(node);
        });
        if (node instanceof WaypointNode) {
            label.setHoveringText(Collections.singletonList(((WaypointNode) node).getName()));
        }
        WorldPosAutoStyleHandler position = new WorldPosAutoStyleHandler(node.getPosition().x, node.getPosition().z, icon);
        label.getStyle().addAutoStyleHandler(position);
        gpsNodeComponents.put(node, label);
        mapPane.add(label);
    }

    public void refreshGpsNodeComponents() {
        Map<GpsNode, GuiComponent<?>> newGpsNodeComponents = new HashMap<>();
        for (GpsNode node : GpsNodes.getInstance().getNodes()) {
            if (editMode != EditMode.VIEW || node instanceof WaypointNode) {
                if (gpsNodeComponents.containsKey(node)) {
                    mapPane.remove(gpsNodeComponents.get(node));
                }
                addGpsNodeComponent(newGpsNodeComponents, node);
            }
            if (selectedNode != null && selectedNode.equals(node)) {
                selectedNode = node;
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
        GuiComponent<?> label = new GuiLabel("").setCssId("gps_node").setCssClass("waypoint");
        label.setHoveringText(Collections.singletonList(customMarker.getName()));
        WorldPosAutoStyleHandler position = new WorldPosAutoStyleHandler(customMarker.getPosition().x, customMarker.getPosition().z, icon);
        label.getStyle().addAutoStyleHandler(position);
        label.addClickListener((mouseX, mouseY, mouseButton) -> {
            if (mouseButton != 1)
                return;
            ClientEventHandler.gpsNavigator.clear();
            ClientEventHandler.gpsNavigator.setCustomWaypoint(null);
        });
        customMarkerComponent = label;
        mapPane.add(label);
    }

    public void handlePlayerList(List<SCMessagePlayerList.PlayerInformation> playerInformations) {
        List<String> pInfosStr = playerInformations.stream().map(SCMessagePlayerList.PlayerInformation::getName).collect(Collectors.toList());
        trackedPlayers.entrySet().removeIf(p -> !pInfosStr.contains(p.getKey()));
        // todo this is duplicated
        tackedObjectsPosCache.entrySet().removeIf(p -> {
            boolean remove = !trackedPlayers.containsValue(p.getKey()) && !GtwMapApi.getTrackedObjects().contains(p.getKey());
            if (remove) {
                mapPane.remove(p.getValue().component);
            }
            return remove;
        });
        for (SCMessagePlayerList.PlayerInformation playerInformation : playerInformations) {
            if (playerInformation.getName().equals(mc.player.getName())) {
                continue;
            }
            EntityPlayer player = mc.world.getPlayerEntityByName(playerInformation.getName());
            if (trackedPlayers.containsKey(playerInformation.getName())) {
                ITrackableObject<?> trackedObject = trackedPlayers.get(playerInformation.getName());
                if (player == null) {
                    if (trackedObject instanceof ITrackableObject.TrackedEntity) {
                        // replace the entity player with the player information
                        trackedPlayers.remove(playerInformation.getName());
                        mapPane.remove(tackedObjectsPosCache.remove(trackedObject).component);
                    } else {
                        // update the player information
                        ((ITrackableObject.TrackedPlayerInformation) trackedObject).update(playerInformation);
                        continue;
                    }
                } else {
                    if (trackedObject instanceof ITrackableObject.TrackedEntity) {
                        // entity players are always up to date
                        continue;
                    }
                    // replace the player information with the entity player itself
                    trackedPlayers.remove(playerInformation.getName());
                    mapPane.remove(tackedObjectsPosCache.remove(trackedObject).component);
                }
            }
            ITrackableObject<?> trackedObject = player != null ? new ITrackableObject.TrackedEntity(player, player.getDisplayNameString(), "player_white") : new ITrackableObject.TrackedPlayerInformation(playerInformation);
            trackedPlayers.put(playerInformation.getName(), trackedObject);
            makeTrackedPoint(trackedObject, false);
        }
    }

    private void handleNodeRightClick(GpsNode node) {
        switch (editMode) {
            case VIEW:
            case VIEW_NODES:
                if (node instanceof WaypointNode) {
                    ClientEventHandler.gpsNavigator.setTargetNode(mc.player, node);
                    ClientEventHandler.gpsNavigator.setCustomWaypoint(null);
                }
                break;
            case ADD_NODES:
                if (!(node instanceof WaypointNode)) {
                    GuiPanel overlay = new GuiPanel();
                    GuiPanel subMenu = new GuiPanel();
                    subMenu.setCssId("sub_menu");
                    GuiLabel l = new GuiLabel(I18n.format("gui.map.create_wp"));
                    subMenu.add(l.setCssId("sub_menu_title"));
                    GuiTextField nameField = new GuiTextField();
                    nameField.setHintText(I18n.format("gui.map.wp_name"));
                    subMenu.add(nameField.setCssId("name_field"));
                    GuiTextField iconField = new GuiTextField();
                    iconField.setHintText("icon");
                    subMenu.add(iconField.setCssId("icon_field"));
                    subMenu.add(new GuiLabel(I18n.format("gui.map.confirm")).addClickListener((x1, y1, b1) -> {
                        mapPane.remove(gpsNodeComponents.remove(node));
                        WaypointNode waypointNode = WaypointNode.fromGpsNode(node, nameField.getText(), iconField.getText(), GpsNodes.getInstance(), true);
                        addGpsNodeComponent(gpsNodeComponents, waypointNode);
                        remove(overlay);
                    }).setCssId("sub_confirm"));
                    subMenu.add(new GuiLabel(I18n.format("gui.map.cancel")).addClickListener((x1, y1, b1) -> {
                        remove(overlay);
                    }).setCssId("sub_cancel"));
                    overlay.add(subMenu);
                    add(overlay.setCssId("overlay"));
                }
                break;
            case REMOVE_NODES:
                if (node instanceof WaypointNode) {
                    ((WaypointNode) node).toGpsNode(gpsNodes, true);
                    return;
                }
                node.delete(gpsNodes, true);
                mapPane.remove(gpsNodeComponents.remove(node));
                break;
            case EDIT_LINKS:
            case EDIT_ONEWAY_LINK:
                if (selectedNode == null) {
                    selectedNode = node;
                    selectedLink.addControlPoint(new Vector2f(node.getPosition().x, node.getPosition().z));
                } else {
                    selectedLink.addControlPoint(new Vector2f(node.getPosition().x, node.getPosition().z));
                    if (selectedNode.getNeighbors(gpsNodes).contains(node)) {
                        selectedNode.removeNeighbor(gpsNodes, node, true);
                        if (editMode == EditMode.EDIT_LINKS) {
                            node.removeNeighbor(gpsNodes, selectedNode, true);
                        }
                    } else {
                        selectedNode.addNeighbor(gpsNodes, node, selectedLink.getControlPoints(), true);
                        if (editMode == EditMode.EDIT_LINKS && !node.getNeighbors(gpsNodes).contains(selectedNode)) {
                            node.addNeighbor(gpsNodes, selectedNode, selectedLink.getControlPoints(), true);
                        }
                    }
                    selectedNode = null;
                    selectedLink.getControlPoints().clear();
                }
                break;
        }
    }

    private void handleRightClick(int mouseX, int mouseY) {
        if (editMode == EditMode.VIEW || editMode == EditMode.VIEW_NODES) {
            ClientEventHandler.gpsNavigator.setTargetPos(mc.player, new Vector3f(viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth()), 0,
                    viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight())));

           /* boolean b = MapApp.customPoint != null && MapApp.customPoint.isHovered();
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
                    public boolean handleProperty(EnumCssStyleProperty property, EnumSelectorContext context, ComponentStyleManager csm) {
                        if (property == EnumCssStyleProperty.LEFT) {
                            float guiX = (worldX - viewport.x) * mapPane.getWidth() / viewport.width;
                            csm.getXPos().setAbsolute(guiX);
                            //System.out.println("Cmp guiX " + guiX + " " + worldX);
                            return true;
                        }
                        if (property == EnumCssStyleProperty.TOP) {
                            float guiY = (worldZ - viewport.y) * mapPane.getHeight() / viewport.height;
                            csm.getYPos().setAbsolute(guiY);
                            // System.out.println("Cmp guiY " + guiY + " " + worldZ);
                            //csm.setZLevel(30);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public Collection<EnumCssStyleProperty> getModifiedProperties(ComponentStyleManager componentStyleManager) {
                        return Arrays.asList(EnumCssStyleProperty.LEFT, EnumCssStyleProperty.TOP);
                    }
                });
            }*/
        } else if (editMode == EditMode.ADD_NODES) {
            addNode(mouseX, mouseY);
        } else if (editMode != EditMode.EDIT_LINKS || editMode != EditMode.EDIT_ONEWAY_LINK) {
            if (selectedNode != null) {
                if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                    GpsNode node = addNode(mouseX, mouseY);
                    handleNodeRightClick(node);
                    return;
                }
                float worldX = viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth());
                float worldZ = viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight());
                selectedLink.addControlPoint(new Vector2f(worldX, worldZ));
            }
        }
    }

    private GpsNode addNode(int mouseX, int mouseY) {
        float worldX = viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth());
        float worldZ = viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight());
        GpsNode node = new GpsNode(new Vector3f(worldX, 0, worldZ), new HashSet<>());
        node.create(gpsNodes, true);
        addGpsNodeComponent(gpsNodeComponents, node);
        return node;
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
        float dw = (400 * mapPane.getWidth() / newViewport.width);
        float dh = (400 * mapPane.getHeight() / newViewport.height);

        float bx = (-(ux % 400) * mapPane.getWidth() / newViewport.width);
        float by = (-(uy % 400) * mapPane.getHeight() / newViewport.height);

        MapContainerClient mapContainer = (MapContainerClient) MapContainer.getInstance(true);
        countx = 0;
        List<PartPos> oldPoses = new ArrayList<>(partsStore.keySet());
        int x = (int) (newViewport.x - (ux % 400) - 400);
        for (float dx = bx - dw; dx < bx + mapPane.getWidth() + dw; dx = dx + dw) {
            county = 0;
            int z = (int) (newViewport.y - (uy % 400) - 400);
            for (float dy = by - dh; dy < by + mapPane.getHeight() + dh; dy = dy + dh) {
                int x2 = (int) x;
                int z2 = (int) z;
                if (x2 < 0)
                    x2 -= 399;
                if (z2 < 0)
                    z2 -= 399;
                PartPos pos = new PartPos(x2 / 400, z2 / 400);
                MapPartClient part = (MapPartClient) mapContainer.requestTile(x2, z2, mc.world, mc.player);
                part.refreshMapContents();
                PartPosAutoStyleHandler partPane = partsStore.get(pos);
                if (partPane == null) {
                    GuiPanel pane = (GuiPanel) new GuiPanel() {
                        @Override
                        protected void bindLayerBounds() {
                        }

                        @Override
                        protected void unbindLayerBounds() {
                        }
                    }.getStyle()
                            .setTexture(new GuiTextureSprite(part.getLocation(), 0, 0, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE)).getOwner();
                    partPane = new PartPosAutoStyleHandler(pane, dx, dy, dw, dh);
                    pane.getStyle().addAutoStyleHandler(partPane);
                    partsStore.put(pos, partPane);
                    mapPane.add(pane);
                } else {
                    oldPoses.remove(pos);
                    partPane.update(dx, dy, dw, dh);
                }
                z = z + 400;
                county++;
            }
            x = x + 400;
            countx++;
        }
        oldPoses.stream().map(partsStore::remove).map(PartPosAutoStyleHandler::getPartPane).forEach(mapPane::remove);

        //====================== Geo localisation ======================
        if (lastObjectsCount != (mc.world.playerEntities.size() + GtwMapApi.getTrackedObjects().size())) {
            tackedObjectsPosCache.entrySet().removeIf(p -> {
                boolean remove = !trackedPlayers.containsValue(p.getKey()) && !GtwMapApi.getTrackedObjects().contains(p.getKey());
                if (remove) {
                    mapPane.remove(p.getValue().component);
                }
                return remove;
            });
            for (EntityPlayer player : mc.world.playerEntities) {
                if (!trackedPlayers.containsKey(player.getName())) {
                    ITrackableObject<?> trackedObject = new ITrackableObject.TrackedEntity(player, player.getDisplayNameString(), "player_white");
                    trackedPlayers.put(player.getName(), trackedObject);
                    makeTrackedPoint(trackedObject, false);
                } else if (!(trackedPlayers.get(player.getName()) instanceof ITrackableObject.TrackedEntity)) {
                    // In this case, the player was sent as a PlayerInformation, but now that we have the EntityPlayer, we can update it
                    mapPane.remove(tackedObjectsPosCache.remove(trackedPlayers.get(player.getName())).component);
                    ITrackableObject<?> trackedObject = new ITrackableObject.TrackedEntity(player, player.getDisplayNameString(), "player_white");
                    trackedPlayers.put(player.getName(), trackedObject);
                    makeTrackedPoint(trackedObject, false);
                }
            }
            for (ITrackableObject<?> object : GtwMapApi.getTrackedObjects()) {
                if (!tackedObjectsPosCache.containsKey(object)) {
                    makeTrackedPoint(object, false);
                }
            }
            lastObjectsCount = mc.world.playerEntities.size() + GtwMapApi.getTrackedObjects().size();
        }
        for (EntityPosCache posCache : tackedObjectsPosCache.values()) {
            posCache.update();
        }

        viewport = newViewport;
        mapPane.getStyle().refreshCss(getGui(), false, EnumCssStyleProperty.LEFT, EnumCssStyleProperty.TOP, EnumCssStyleProperty.WIDTH, EnumCssStyleProperty.HEIGHT);
    }

    public void makeTrackedPoint(ITrackableObject<?> object, boolean rotateLabel) {
        int size = WaypointNode.getIconSize(object.getIcon());
        GuiTextureSprite icon = new GuiTextureSprite(new ResourceLocation(GtwMapConstants.ID, "textures/gps/wp_" + object.getIcon() + ".png"), 0, 0, size, size);
        WorldPosAutoStyleHandler pos = new WorldPosAutoStyleHandler(object.getPosX(1), object.getPosZ(1), icon);
        GuiLabel label = new GuiLabel("") {
            @Override
            protected void bindLayerBounds() {
                if (rotateLabel && getX() > 0 && getY() > 0) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(getRenderMinX() + getWidth() / 2f, getRenderMinY() + getHeight() / 2f, 0);
                    GlStateManager.rotate(((ITrackableObject.TrackedEntity) object).getTrackedObject().rotationYaw + 90, 0, 0, 1);
                    GlStateManager.translate(-getRenderMinX() - getWidth() / 2f, -getRenderMinY() - getHeight() / 2f, 0);
                }
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GuiAPIClientHelper.glScissor(mapPane.getRenderMinX(), mapPane.getRenderMinY(), mapPane.getRenderMaxX() - mapPane.getRenderMinX(), mapPane.getRenderMaxY() - mapPane.getRenderMinY());
            }

            @Override
            protected void unbindLayerBounds() {
                super.unbindLayerBounds();
                if (rotateLabel && getX() > 0 && getY() > 0) {
                    GlStateManager.popMatrix();
                }
            }
        };
        mapPane.add(label.setHoveringText(Collections.singletonList(object.getDisplayName())).setCssId("custom_marker").setCssClass(object.smallIcon() ? "waypoint_small" : "waypoint").getStyle().addAutoStyleHandler(pos).getOwner());
        tackedObjectsPosCache.put(object, new EntityPosCache(object, label, pos));
    }

    private int countx, county;

    @Override
    public void drawForeground(int mouseX, int mouseY, float partialTicks, ComponentRenderContext renderContext) {
        // Draw links between nodes
        GlStateManager.disableTexture2D();
        GuiAPIClientHelper.glScissor(mapPane.getScreenX(), mapPane.getScreenY(), mapPane.getWidth(), mapPane.getHeight());
        if (editMode != EditMode.VIEW) {
            Set<UUID> renderedNodes = new HashSet<>();
            for (Map.Entry<GpsNode, GuiComponent<?>> entry : gpsNodeComponents.entrySet()) {
                renderedNodes.add(entry.getKey().getId());
                for (GpsNode neighbor : entry.getKey().getNeighbors(gpsNodes)) {
                    if (renderedNodes.contains(neighbor.getId()) && neighbor.getNeighbors(gpsNodes).contains(entry.getKey()))
                        continue;
                    GuiComponent<?> component = gpsNodeComponents.get(neighbor);
                    if (component == null)
                        continue;
                    if (!viewport.contains(entry.getKey().getPosition().x, entry.getKey().getPosition().z) && !viewport.contains(neighbor.getPosition().x, neighbor.getPosition().z))
                        continue;
                    boolean twoWay = neighbor.getNeighbors(gpsNodes).contains(entry.getKey());
                    if (entry.getKey().getBezierCurves().containsKey(neighbor.getId())) {
                        BezierCurveLink link = entry.getKey().getBezierCurves().get(neighbor.getId());
                        List<Vector2f> points = link.getRenderPoints();
                        GlStateManager.color(1, 1, 1, 1);
                        GlStateManager.glBegin(GL11.GL_LINE_STRIP);
                        for (int i = 0; i < points.size(); i++) {
                            Vector2f point = points.get(i);
                            if (twoWay)
                                GlStateManager.color(1, 1, 1, 1);
                            else if (i < points.size() - 2)
                                GlStateManager.color(1, 0, 0, 1);
                            else
                                GlStateManager.color(0, 1, 0, 1);
                            float startX = mapPane.getScreenX() + (point.x - viewport.x) * mapPane.getWidth() / viewport.width;
                            float startY = mapPane.getScreenY() + (point.y - viewport.y) * mapPane.getHeight() / viewport.height;
                            GlStateManager.glVertex3f(startX, startY, 5);
                        }
                        GlStateManager.glEnd();
                    } else {
                        float startX = entry.getValue().getScreenX() + 8;
                        float startY = entry.getValue().getScreenY() + 8;
                        float endX = component.getScreenX() + 8;
                        float endY = component.getScreenY() + 8;
                        if (twoWay) {
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
            }
            if (!selectedLink.getControlPoints().isEmpty()) {
                GlStateManager.glBegin(GL11.GL_LINE_STRIP);
                for (int i = 0; i < selectedLink.getRenderPoints().size() - 1; i++) {
                    if (editMode == EditMode.EDIT_LINKS) {
                        GlStateManager.color(1, 1, 1, 1);
                    } else {
                        if (i < selectedLink.getRenderPoints().size() - 2) {
                            GlStateManager.color(1, 0, 0, 1);
                        } else {
                            GlStateManager.color(0, 1, 0, 1);
                        }
                    }
                    Vector2f point1 = selectedLink.getRenderPoints().get(i);
                    Vector2f point2 = selectedLink.getRenderPoints().get(i + 1);
                    float startX = mapPane.getScreenX() + (point1.x - viewport.x) * mapPane.getWidth() / viewport.width;
                    float startY = mapPane.getScreenY() + (point1.y - viewport.y) * mapPane.getHeight() / viewport.height;
                    float endX = mapPane.getScreenX() + (point2.x - viewport.x) * mapPane.getWidth() / viewport.width;
                    float endY = mapPane.getScreenY() + (point2.y - viewport.y) * mapPane.getHeight() / viewport.height;
                    GlStateManager.glVertex3f(startX, startY, 5);
                    GlStateManager.glVertex3f(endX, endY, 5);
                }
                GlStateManager.glEnd();
                float startX = mapPane.getScreenX() + (selectedLink.getControlPoints().get(
                        selectedLink.getControlPoints().size() - 1).x - viewport.x) * mapPane.getWidth() / viewport.width;
                float startY = mapPane.getScreenY() + (selectedLink.getControlPoints().get(
                        selectedLink.getControlPoints().size() - 1).y - viewport.y) * mapPane.getHeight() / viewport.height;
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
        GlStateManager.disableTexture2D();
        GlStateManager.color(87f / 255, 0, 127f / 255, 1);
        GpsNavigator nav = ClientEventHandler.gpsNavigator;
        if (nav.getRoute() != null) {
            Queue<GpsNode> copy = new LinkedList<>(nav.getRoute());
            GpsNode first = copy.poll(), second = null;
            while ((second = copy.poll()) != null) {
                // if (!viewport.contains(first.getPosition().x, first.getPosition().z) && !viewport.contains(second.getPosition().x, second.getPosition().z))
                //   continue;
                /*GuiComponent<?> component1 = first.equals(ClientEventHandler.gpsNavigator.getCustomWaypoint()) ? customMarkerComponent : gpsNodeComponents.get(first);
                GuiComponent<?> component2 = second.equals(ClientEventHandler.gpsNavigator.getCustomWaypoint()) ? customMarkerComponent : gpsNodeComponents.get(second);
                if (component1 == null || component2 == null)
                    continue;*/

                float width = 5;
                if (first.getBezierCurves().containsKey(second.getId())) {
                    BezierCurveLink link = first.getBezierCurves().get(second.getId());
                    List<Vector2f> points = link.getRenderPoints();
                    //GlStateManager.glLineWidth(10);
                    //GlStateManager.glBegin(GL11.GL_LINE_STRIP);
                    for (int i = 0; i < points.size() - 1; i++) {
                        Vector2f point1 = points.get(i);
                        Vector2f point2 = points.get(i + 1);
                        float startX = mapPane.getScreenX() + (point1.x - viewport.x) * mapPane.getWidth() / viewport.width;
                        float startY = mapPane.getScreenY() + (point1.y - viewport.y) * mapPane.getHeight() / viewport.height;
                        float endX = mapPane.getScreenX() + (point2.x - viewport.x) * mapPane.getWidth() / viewport.width;
                        float endY = mapPane.getScreenY() + (point2.y - viewport.y) * mapPane.getHeight() / viewport.height;
                        // GlStateManager.glVertex3f(startX, startY, 5);
                        // GlStateManager.glVertex3f(endX, endY, 5);
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

                        int j;
                        int triangleAmount = 20; //# of triangles used to draw circle
                        //In radians
                        float twicePi = (float) (2.0f * Math.PI);
                        float x = endX;
                        float y = endY;
                        float radius = 2.5f;
                        GlStateManager.glVertex3f(x, y, 0); // center of circle
                        for (j = 0; j <= triangleAmount; j++) {
                            GlStateManager.glVertex3f(
                                    (float) (x + (radius * Math.sin(0 + (float) j * twicePi / (float) triangleAmount))),
                                    (float) (y + (radius * Math.cos(0 + (float) j * twicePi / (float) triangleAmount))), 0
                            );
                        }
                        GlStateManager.glEnd();
                    }
                    //GlStateManager.glEnd();
                    GlStateManager.glLineWidth(1);
                } else {
                    float startX = (first.getPosition().x - GuiBigMap.this.viewport.x) * GuiBigMap.this.mapPane.getWidth() / GuiBigMap.this.viewport.width + mapPane.getScreenX();
                    float startY = (first.getPosition().z - GuiBigMap.this.viewport.y) * GuiBigMap.this.mapPane.getHeight() / GuiBigMap.this.viewport.height + mapPane.getScreenY();
                    float endX = (second.getPosition().x - GuiBigMap.this.viewport.x) * GuiBigMap.this.mapPane.getWidth() / GuiBigMap.this.viewport.width + mapPane.getScreenX();
                    float endY = (second.getPosition().z - GuiBigMap.this.viewport.y) * GuiBigMap.this.mapPane.getHeight() / GuiBigMap.this.viewport.height + mapPane.getScreenY();
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
        //GL11.glDisable(GL_SCISSOR_TEST);
        bindLayerBounds();
        GlStateManager.enableTexture2D();

        super.drawForeground(mouseX, mouseY, partialTicks, renderContext);
        int x = (int) (viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth()));
        int z = (int) (viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight()));
        mc.fontRenderer.drawString("x= " + x + " z=" + z, mouseX + 10, mouseY + 10, Color.WHITE.getRGB());
        if (loadingTiles > 0) {
            mc.fontRenderer.drawString(loadingTiles + " tiles loading", 2, (int) (getHeight() - 21), Color.CYAN.getRGB());
        }
        if (editMode != EditMode.VIEW) {
            mc.fontRenderer.drawString(countx + "*" + county + " (" + (countx * county) + ") tiles displayed", 2, (int) (getHeight() - 11), Color.WHITE.getRGB());
        }
        String modeTitle = null;
        switch (editMode) {
            case VIEW:
                return;
            case VIEW_NODES:
                modeTitle = "Admin view mode. Shift+mouse wheel to change mode.";
                break;
            case ADD_NODES:
                modeTitle = TextFormatting.GREEN + "Right click to add gps nodes. Right click on a node to create a waypoint.";
                break;
            case REMOVE_NODES:
                modeTitle = TextFormatting.RED + "Right click on a gps node/waypoint to remove it.";
                break;
            case EDIT_LINKS:
                modeTitle = TextFormatting.YELLOW + "Right click on two nodes to add/remove a link between them. Right click on a node then on the map to create a node and link it to the first.";
                break;
            case EDIT_ONEWAY_LINK:
                modeTitle = TextFormatting.LIGHT_PURPLE + "Right click on two nodes in a specific order to add/remove a one way link between them";
                break;
        }
        GuiAPIClientHelper.drawHoveringText(Collections.singletonList(modeTitle), (int) (getWidth() - 1 - getScreenX()), 3);
        //mc.fontRenderer.drawString(modeTitle, , nodeColor);
    }

    @Override
    public void guiClose() {
        super.guiClose();
        if (ACsGuiApi.getDisplayHudGuis().stream().noneMatch(gui -> gui.getFrame() instanceof GuiBigMap)) {
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
        boolean update = !viewInitialized;
        if (!update) {
            update = tackedObjectsPosCache.values().stream().anyMatch(EntityPosCache::hasChanged) || lastObjectsCount != (mc.world.playerEntities.size() + GtwMapApi.getTrackedObjects().size());
        }
        if (update) {
            viewInitialized = true;
            updateViewport(viewport);
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
        private GuiTextureSprite icon;

        @Override
        public boolean handleProperty(EnumCssStyleProperty property, EnumSelectorContext context, ComponentStyleManager csm) {
            if (property == EnumCssStyleProperty.LEFT) {
                csm.getXPos().setAbsolute((posX - GuiBigMap.this.viewport.x) * GuiBigMap.this.mapPane.getWidth() / GuiBigMap.this.viewport.width - 8);
                return true;
            }
            if (property == EnumCssStyleProperty.TOP) {
                csm.getYPos().setAbsolute((posZ - GuiBigMap.this.viewport.y) * GuiBigMap.this.mapPane.getHeight() / GuiBigMap.this.viewport.height - 8);
                return true;
            }
            if (property == EnumCssStyleProperty.TEXTURE && icon != null) {
                csm.setTexture(icon);
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
    public static class PartPosAutoStyleHandler implements AutoStyleHandler<ComponentStyleManager> {
        @Getter
        private final GuiPanel partPane;
        private float posX;
        private float posZ;
        private float sizeX;
        private float sizeZ;

        @Override
        public boolean handleProperty(EnumCssStyleProperty property, EnumSelectorContext context, ComponentStyleManager csm) {
            switch (property) {
                case LEFT:
                    csm.getXPos().setAbsolute(posX);
                    return true;
                case TOP:
                    csm.getYPos().setAbsolute(posZ);
                    return true;
                case WIDTH:
                    csm.getWidth().setAbsolute(sizeX);
                    return true;
                case HEIGHT:
                    csm.getHeight().setAbsolute(sizeZ);
                    return true;
            }
            return false;
        }

        @Override
        public Collection<EnumCssStyleProperty> getModifiedProperties(ComponentStyleManager componentStyleManager) {
            return Arrays.asList(EnumCssStyleProperty.LEFT, EnumCssStyleProperty.TOP, EnumCssStyleProperty.WIDTH, EnumCssStyleProperty.HEIGHT);
        }

        public void update(float dx, float dy, float dw, float dh) {
            posX = dx;
            posZ = dy;
            sizeX = dw;
            sizeZ = dh;
        }
    }

    @AllArgsConstructor
    public class EntityPosCache {
        private final ITrackableObject<?> object;
        private final GuiLabel component;
        private final WorldPosAutoStyleHandler styleHandler;

        public boolean hasChanged() {
            //  System.out.println("Checking for change " + Math.abs(styleHandler.posX - player.posX) + " " + Math.abs(styleHandler.posZ - player.posZ));
            return Math.abs(styleHandler.posX - object.getPosX(1)) > 0.05f || Math.abs(styleHandler.posZ - object.getPosZ(1)) > 0.05f;
        }

        public void update() {
            styleHandler.setPosX(object.getPosX(1));
            styleHandler.setPosZ(object.getPosZ(1));
        }
    }
}
