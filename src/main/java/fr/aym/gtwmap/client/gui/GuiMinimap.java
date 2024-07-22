package fr.aym.gtwmap.client.gui;

import fr.aym.acsguis.component.layout.GuiScaler;
import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.acsguis.component.panel.GuiPanel;
import fr.aym.acsguis.component.style.AutoStyleHandler;
import fr.aym.acsguis.component.style.ComponentStyleManager;
import fr.aym.acsguis.component.textarea.GuiLabel;
import fr.aym.acsguis.cssengine.selectors.EnumSelectorContext;
import fr.aym.acsguis.cssengine.style.EnumCssStyleProperties;
import fr.aym.acsguis.utils.GuiTextureSprite;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.MapContainerClient;
import fr.aym.gtwmap.map.MapPartClient;
import fr.aym.gtwmap.map.PartPos;
import fr.aym.gtwmap.network.S19PacketMapPartQuery;
import fr.aym.gtwmap.utils.Config;
import fr.aym.gtwmap.utils.GtwMapConstants;
import lombok.AllArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.List;
import java.util.*;

public class GuiMinimap extends GuiFrame {
    public static final ResourceLocation STYLE = new ResourceLocation(GtwMapConstants.ID, "css/gui_mini_map.css");

    private final GuiPanel mapPane;
    private final Map<PartPos, GuiPanel> partsStore = new HashMap<>();

    private final GuiLabel myPos;

    private Viewport viewport;
    private float playerX, playerZ;

    public GuiMinimap() {
        super(new GuiScaler.AdjustToScreenSize(true, 0.3f, 0.3f));
        setCssId("root");
        setPauseGame(false);
        setNeedsCssReload(true);

        this.viewport = new Viewport((float) (mc.player.posX - 40), (float) (mc.player.posZ - 40), 80, 80);

        final GuiPanel pane = new GuiPanel();
        pane.setCssId("container");
        mapPane = new GuiPanel();
        mapPane.setCssId("map_pane");
        pane.add(mapPane);
        add(pane);
        pane.addWheelListener(dWheel -> {
            float amount = dWheel / 60;
            if (dWheel > 0)
                amount = MathHelper.clamp((float) (amount / 4), 1.111111F, 2f);
            else
                amount = MathHelper.clamp((float) (amount * -1 / 2.5F), 0, 0.999999F);
            updateViewport(new Viewport(viewport.x, viewport.y, (int) (viewport.width * amount), (int) (viewport.height * amount)));
        });
        mapPane.add(myPos = (GuiLabel) new GuiLabel("V").setCssId("custom_marker").setCssClass("waypoint"));
    }

    /*
     * DrawLife
     * xmin = 6144 ; zmin = 4512
     * xmax = 15232 ; zmax = 13696
     */
    private void updateViewport(Viewport newViewport) {
        if (/*newViewport.width > 5000 || newViewport.height > 7000 || */newViewport.width < 50 || newViewport.height < 50)
            //DRAWLIFE if(newViewport.x < 6144 || newViewport.width+newViewport.x > 15232 || newViewport.y < 4512 || newViewport.height+newViewport.y > 13696 || newViewport.width < 50 || newViewport.height < 50)
            return;
        if (newViewport.width > 160 || newViewport.height > 160)
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

        List<PartPos> oldPoses = new ArrayList<>(partsStore.keySet());
        int x = (int) (newViewport.x - (ux % 400) - 400);
        for (int dx = bx - dw; dx < bx + mapPane.getWidth() + dw; dx = dx + dw) {
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
            }
            x = x + 400;
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

        myPos.getStyle().getAutoStyleHandlers().clear();
        myPos.getStyle().addAutoStyleHandler(new AutoStyleHandler<ComponentStyleManager>() {
            @Override
            public boolean handleProperty(EnumCssStyleProperties property, EnumSelectorContext context, ComponentStyleManager csm) {
                if (property == EnumCssStyleProperties.LEFT) {
                    csm.getXPos().setAbsolute((playerX - newViewport.x) * mapPane.getWidth() / newViewport.width - 4);
                    return true;
                }
                if (property == EnumCssStyleProperties.TOP) {
                    csm.getYPos().setAbsolute((playerZ - newViewport.y) * mapPane.getHeight() / newViewport.height - 4);
                    return true;
                }
                return false;
            }

            @Override
            public Collection<EnumCssStyleProperties> getModifiedProperties(ComponentStyleManager componentStyleManager) {
                return Arrays.asList(EnumCssStyleProperties.LEFT, EnumCssStyleProperties.TOP);
            }
        });

        viewport = newViewport;
        mapPane.getStyle().refreshCss(false, "viewport_upd");
    }

    @Override
    public void drawForeground(int mouseX, int mouseY, float partialTicks) {
        super.drawForeground(mouseX, mouseY, partialTicks);
        int x = (int) (viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth()));
        int z = (int) (viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight()));
        mc.fontRenderer.drawString("x= " + x + " z=" + z, mouseX + 10, mouseY + 10, Color.WHITE.getRGB());
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
        float pX = (float) mc.player.posX;
        float pZ = (float) mc.player.posZ;
        if (Math.abs(pX - playerX) > 0.03f || Math.abs(pZ - playerZ) > 0.03f) {
            updateViewport(new Viewport(pX - viewport.width / 2, pZ - viewport.height / 2, viewport.width, viewport.height));
        }
    }

    @AllArgsConstructor
    public static class Viewport {
        private float x, y, width, height;
    }
}
