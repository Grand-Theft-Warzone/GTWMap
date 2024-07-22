package fr.aym.gtwmap.client.gui;

import fr.aym.acsguis.api.GuiAPIClientHelper;
import fr.aym.acsguis.component.layout.GuiScaler;
import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.acsguis.component.panel.GuiPanel;
import fr.aym.acsguis.component.style.AutoStyleHandler;
import fr.aym.acsguis.component.style.ComponentStyleManager;
import fr.aym.acsguis.component.textarea.GuiLabel;
import fr.aym.acsguis.cssengine.parsing.core.objects.CssValue;
import fr.aym.acsguis.cssengine.selectors.EnumSelectorContext;
import fr.aym.acsguis.cssengine.style.EnumCssStyleProperties;
import fr.aym.acsguis.event.listeners.mouse.IMouseClickListener;
import fr.aym.acsguis.event.listeners.mouse.IMouseExtraClickListener;
import fr.aym.acsguis.event.listeners.mouse.IMouseMoveListener;
import fr.aym.acsguis.utils.GuiTextureSprite;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.MapContainerClient;
import fr.aym.gtwmap.map.MapPart;
import fr.aym.gtwmap.map.MapPartClient;
import fr.aym.gtwmap.network.S19PacketMapPartQuery;
import fr.aym.gtwmap.utils.Config;
import fr.aym.gtwmap.utils.GtwMapConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GuiMapTest extends GuiFrame {
    public static final ResourceLocation STYLE = new ResourceLocation(GtwMapConstants.ID, "css/gui_map.css");
    public static int loadingTiles;

    private final Rectangle defaultViewport;
    private Rectangle viewport;

    public int mouseClickX = 0, mouseClickY = 0;
    private GuiPanel mapPane;

    public GuiMapTest() {
        super(new GuiScaler.AdjustFullScreen());
        setCssId("root");
        setPauseGame(false);
        setNeedsCssReload(true);

        BlockPos pos = mc.player.getPosition();
        this.defaultViewport = new Rectangle(pos.getX() - 200, pos.getZ() - 200, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE);
        this.viewport = defaultViewport;

        final GuiPanel pane = new GuiPanel();
        pane.setCssId("container");
        mapPane = new GuiPanel();
        mapPane.setCssId("map_pane");
        pane.add(mapPane);

        pane.addClickListener(new IMouseClickListener() {
            @Override
            public void onMouseClicked(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton == 1) {
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
                        int worldX = viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth());
                        int worldZ = viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight());
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
                }
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
                    updateViewport(new Rectangle(viewport.x - (mouseX - mouseClickX) / 1, viewport.y - (mouseY - mouseClickY) / 1, viewport.width, viewport.height));
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
            //double oldW = pane.getxSlider().getValue(), oldH = pane.getySlider().getValue();
            float amount = dWheel / 60;
            //System.out.println("From amount " + amount + " " + ((float)(amount*-1/4/100)) + " " + ((float)(amount/4)));
            if (dWheel > 0)
                amount = MathHelper.clamp((float) (amount / 4), 1.111111F, Float.MAX_VALUE);
            else
                amount = MathHelper.clamp((float) (amount * -1 / 2.5F), 0, 0.999999F);
            updateViewport(new Rectangle(viewport.x, viewport.y, (int) (viewport.width * amount), (int) (viewport.getHeight() * amount)));
            //System.out.println(dWheel + " " + amount);
        });

        add(new GuiLabel(2, 2, getWidth() - 4, 20, "Grand Theft Warzone Map. Right-click to set a custom marker.").setCssId("title"));
        updateViewport(viewport);
    }

    /*
     * DrawLife
     * xmin = 6144 ; zmin = 4512
     * xmax = 15232 ; zmax = 13696
     */
    private void updateViewport(Rectangle newViewport) {
        if (/*newViewport.width > 5000 || newViewport.height > 7000 || */newViewport.width < 50 || newViewport.height < 50)
            //DRAWLIFE if(newViewport.x < 6144 || newViewport.width+newViewport.x > 15232 || newViewport.y < 4512 || newViewport.height+newViewport.y > 13696 || newViewport.width < 50 || newViewport.height < 50)
            return;
        if (mapPane.getWidth() == 0 || mapPane.getHeight() == 0) {
            System.out.println("Escaping from 0-world");
            return;
        }
        int xdif = (newViewport.width + newViewport.x) - Config.mapDims[1];
        int ydif = (newViewport.height + newViewport.y) - Config.mapDims[3];
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
        mapPane.removeAllChilds();
       // System.out.println("Clear");
        //System.out.println("Viewporting to " + newViewport);

        int ux = newViewport.x < 0 ? newViewport.x - 400 : newViewport.x;
        int uy = newViewport.y < 0 ? newViewport.y - 400 : newViewport.y;
        int dw = 400 * mapPane.getWidth() / newViewport.width;
        int dh = 400 * mapPane.getHeight() / newViewport.height;

        int bx = -(ux % 400) * mapPane.getWidth() / newViewport.width;
        int by = -(uy % 400) * mapPane.getHeight() / newViewport.height;

        //System.out.println("MPW " + mapPane.getWidth() + " MPH " + mapPane.getHeight() + " " + ux + " " + uy);
        /*mapPane.setLayout(new GridLayout(dw, dh, 0, GridLayout.GridDirection.VERTICAL, (by + mapPane.getHeight()) / dh) {
            @Override
            public boolean handleProperty(EnumCssStyleProperties property, EnumSelectorContext context, ComponentStyleManager target) {
                if (target.getOwner() instanceof GuiLabel && target.getOwner().getCssClass().equals("waypoint") && (property == EnumCssStyleProperties.LEFT || property == EnumCssStyleProperties.TOP)) {
                    //System.out.println("GET WP POS");
                    return false;
                }
                //System.out.println("RSE IN ACTION");
                return super.handleProperty(property, context, target);
            }
        });*/

        //System.out.println("Bases : " + bx + " " + by + " " + dw + " " + dh);

        MapContainerClient mapContainer = (MapContainerClient) MapContainer.getInstance(true);

        countx = 0;
        int x = newViewport.x - (ux % 400) - 400;
        //GuiLabel lab;
        for (int dx = bx - dw; dx < bx + mapPane.getWidth() + dw; dx = dx + dw) {
            int z = newViewport.y - (uy % 400) - 400;
            county = 0;
            for (int dy = by - dh; dy < by + mapPane.getHeight() + dh; dy = dy + dh) {
                MapPartClient part = (MapPartClient) mapContainer.requestTile(x, z, mc.world, mc.player);
                part.refreshMapContents();

                //if(part.getPos().equals(new PartPos(0, 0)))
                //System.out.println("For " + x + " " + z + " : " + dw + " " + dh + " " + dx + " " + dy + " " + part.getPos() + " " + ((MapContainerClient) MapContainer.getINSTANCE()).getLocation(part.getPos()));
                //lab = (GuiLabel) new GuiLabel(1, 1, 200, 20).setText(part.getPos().toString()+" / x="+dx+" y=" +dy);
                int finalDx = dx;
                int finalDy = dy;
                mapPane.add(new GuiPanel()/*.add(lab).setBorderSize(0).setBorderColor(Color.RED.getRGB())*/.getStyle()
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
                        }).addAutoStyleHandler(new AutoStyleHandler<ComponentStyleManager>() {
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
                        }).getOwner());
                z = z + 400;
                county++;
            }
            x = x + 400;
            countx++;
        }
        //lab = (GuiLabel) new GuiLabel(1, 1, 200, 20).setText("Debug pie");
        //mapPane.add(new GuiPanel(0, 0, dw, dh).add(lab).setBorderSize(1).setBorderColor(Color.CYAN.getRGB()).setBackgroundColor(Color.TRANSLUCENT).setZLevel(500));

        //====================== Waypoints ======================
        //long time = System.currentTimeMillis();
        for (final MapApp.MapWaypoint w : MapApp.waypoints) {
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
        //System.out.println("Took " + (System.currentTimeMillis()-time)+ " ms to calculate waypoints !");
        if (MapApp.customPoint != null) {//TODO UPDATE SYSTEM
            mapPane.add(MapApp.customPoint);
            //System.out.println("Add");
        }

        //====================== Geo localisation ======================
        //TODO

        final MapApp.MapWaypoint w = new MapApp.MapWaypoint((float) mc.player.posX, (float) mc.player.posZ, "Votre position", "V", -1);
        mapPane.add(new GuiLabel(w.getIcon()) {
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
        }.setCssId("custom_marker").setCssClass("waypoint").getStyle().addAutoStyleHandler(new AutoStyleHandler<ComponentStyleManager>() {
            @Override
            public boolean handleProperty(EnumCssStyleProperties property, EnumSelectorContext context, ComponentStyleManager csm) {
                if (property == EnumCssStyleProperties.LEFT) {
                    csm.getXPos().setAbsolute((w.getX() - newViewport.x) * mapPane.getWidth() / newViewport.width);
                    return true;
                }
                if (property == EnumCssStyleProperties.TOP) {
                    csm.getYPos().setAbsolute((w.getY() - newViewport.y) * mapPane.getHeight() / newViewport.height);
                    return true;
                }
                return false;
            }

            @Override
            public Collection<EnumCssStyleProperties> getModifiedProperties(ComponentStyleManager componentStyleManager) {
                return Arrays.asList(EnumCssStyleProperties.LEFT, EnumCssStyleProperties.TOP);
            }
        }).getOwner());

        viewport = newViewport;
        //for(GuiComponent comp : mapPane.getChildComponents())
        //comp.updateComponentPosition(mapPane.getWidth(), mapPane.getHeight());
    }

    private int countx, county;

    @Override
    public void drawForeground(int mouseX, int mouseY, float partialTicks) {
        super.drawForeground(mouseX, mouseY, partialTicks);
        int x = viewport.x + (mouseX - mapPane.getRenderMinX()) * viewport.width / (mapPane.getWidth() == 0 ? 1 : mapPane.getWidth());
        int z = viewport.y + (mouseY - mapPane.getRenderMinY()) * viewport.height / (mapPane.getHeight() == 0 ? 1 : mapPane.getHeight());
        mc.fontRenderer.drawString("x= " + x + " z=" + z, mouseX + 10, mouseY + 10, Color.WHITE.getRGB());
        if(loadingTiles > 0) {
            mc.fontRenderer.drawString(loadingTiles + " tiles loading", 2, getHeight() - 21, Color.CYAN.getRGB());
        }
        mc.fontRenderer.drawString(countx + "*" + county + " (" + (countx * county) + ") tiles displayed", 2, getHeight() - 11, Color.WHITE.getRGB());
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
}
