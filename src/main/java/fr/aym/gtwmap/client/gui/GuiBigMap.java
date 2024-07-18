package fr.aym.gtwmap.client.gui;

import fr.aym.acsguis.api.GuiAPIClientHelper;
import fr.aym.acsguis.component.GuiComponent;
import fr.aym.acsguis.component.layout.GuiScaler;
import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.acsguis.component.panel.GuiPanel;
import fr.aym.acsguis.component.panel.GuiScrollPane;
import fr.aym.acsguis.component.textarea.GuiLabel;
import fr.aym.acsguis.cssengine.parsing.core.objects.CssIntValue;
import fr.aym.acsguis.cssengine.parsing.core.objects.CssValue;
import fr.aym.acsguis.cssengine.style.CssComponentStyleManager;
import fr.aym.acsguis.event.listeners.mouse.IMouseClickListener;
import fr.aym.acsguis.event.listeners.mouse.IMouseExtraClickListener;
import fr.aym.acsguis.event.listeners.mouse.IMouseMoveListener;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GuiBigMap extends GuiFrame {
    public int mouseClickX = -1, mouseClickY = -1;

    @Override
    public void resize(APIGuiScreen gui, int screenWidth, int screenHeight) {
        super.resize(gui, screenWidth, screenHeight);
        //this.setWidth(screenWidth-30);
        //this.setHeight(screenHeight-30);
    }

    public GuiBigMap(ScaledResolution res) {
        super(0, 0, res.getScaledWidth(), res.getScaledHeight(), new GuiScaler.AdjustFullScreen());
        this.getStyle().setBackgroundColor(Color.TRANSLUCENT);

        final GuiPanel map = (GuiPanel) new GuiPanel(0, 0, 2231, 1307).getStyle().setTexture(MapApp.TEXTURE).getOwner();
        map.addClickListener(new IMouseClickListener() {
            @Override
            public void onMouseClicked(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton == 1) {
                    boolean b = MapApp.customPoint != null && MapApp.customPoint.isHovered();
                    if (MapApp.customPoint != null) {
                        map.remove(MapApp.customPoint);
                        MapApp.customPoint = null;
                    }
                    if (!b) {
                        final MapApp.MapWaypoint w = new MapApp.MapWaypoint(mouseX - map.getScreenX() - 4, mouseY - map.getScreenY() - 2, "Custom point", "X");
                        map.add(MapApp.customPoint = new GuiLabel(0, 0, 10, 10, w.getIcon()) {
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
                        }
                                .getStyle().setPaddingLeft(1).setPaddingTop(1).setFontColor(TextFormatting.RED).getOwner());
                        MapApp.customPoint.getStyle().getXPos().setRelative(w.calculateRelX(map.getWidth()), CssValue.Unit.RELATIVE_INT);
                        MapApp.customPoint.getStyle().getYPos().setRelative(w.calculateRelY(map.getHeight()), CssValue.Unit.RELATIVE_INT);
                    }
                }
            }
        });
        final GuiScrollPane pane = new GuiScrollPane(15, 15, getWidth() - 30, getHeight() - 30);
        add(pane);
        pane.add(map);
        pane.updateSlidersVisibility();
        pane.getxSlider().setStep(0.01F);
        pane.getySlider().setStep(0.01F);
        pane.getxSlider().setWheelStep(0);
        pane.getySlider().setWheelStep(0);

        pane.getStyle().setBackgroundColor(Color.TRANSLUCENT);
        pane.getStyle().setBorderColor(Color.LIGHT_GRAY.getRGB());
        pane.getStyle().setBorderSize(new CssIntValue(2));

        pane.addMoveListener(new IMouseMoveListener() {
            @Override
            public void onMouseUnhover(int mouseX, int mouseY) {
                mouseClickX = 0;
                mouseClickY = 0;
            }

            @Override
            public void onMouseMoved(int mouseX, int mouseY) {
                if (mouseClickX != 0 && (mouseX != mouseClickX || mouseY != mouseClickY)) {
                    pane.scrollXBy(-(mouseX - mouseClickX));
                    pane.scrollYBy(-(mouseY - mouseClickY));
                    mouseClickX = mouseX;
                    mouseClickY = mouseY;

                    //Save values to keep when reopening
                    //getApp().scrollX = pane.getxSlider().getValue();
                    //getApp().scrollY = pane.getySlider().getValue();
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
            double oldW = pane.getxSlider().getValue(), oldH = pane.getySlider().getValue();
            float amount = dWheel / 60;
            //System.out.println("From amount " + amount + " " + ((float)(amount*-1/4/100)) + " " + ((float)(amount/4)));
            if (dWheel > 0)
                amount = MathHelper.clamp((float) (amount / 4), 1.111111F, Float.MAX_VALUE);
            else
                amount = MathHelper.clamp((float) (amount * -1 / 2.5F), 0, 0.999999F);
            //System.out.println(dWheel + " " + amount);
            map.getStyle().getWidth().setAbsolute((int) (map.getWidth() * amount));
            map.getStyle().getHeight().setAbsolute((int) (map.getHeight() * amount));

            if (map.getHeight() < 200) {
                map.getStyle().getWidth().setAbsolute(350);
                map.getStyle().getHeight().setAbsolute(200);
            } else if (map.getHeight() > 2500) {
                map.getStyle().getWidth().setAbsolute(4374);
                map.getStyle().getHeight().setAbsolute(2500);
            } else {
                pane.getxSlider().setValue(oldW * amount);
                pane.getySlider().setValue(oldH * amount);
            }
            for (GuiComponent<?> comp : map.getChildComponents())
                ((CssComponentStyleManager) comp.getStyle()).updateComponentPosition(map.getWidth(), map.getHeight());
            //Save values to keep when reopening
            /**getApp().mapWidth = map.getWidth();
             getApp().mapHeight = map.getHeight();
             getApp().scrollX = pane.getxSlider().getValue();
             getApp().scrollY = pane.getySlider().getValue();*/
        });
        //Restore saved values
        /**pane.getxSlider().setValue(getApp().scrollX);
         pane.getySlider().setValue(getApp().scrollY);*/
		
		/*if(getApp().mapWidth != 0 && getApp().mapHeight != 0)
		{
			map.setWidth(getApp().mapWidth);
			map.setHeight(getApp().mapHeight);
			for(GuiComponent comp : map.getChildComponents())
				comp.updateComponentPosition(map.getWidth(), map.getHeight());
		}*/

        //====================== Waypoints ======================
        long time = System.currentTimeMillis();
        for (final MapApp.MapWaypoint w : MapApp.waypoints) {
            GuiLabel label = new GuiLabel(0, 0, 10, 10, w.getIcon()) {
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
            label.getStyle().setPaddingLeft(1).setPaddingTop(1);
            label.getStyle().setForegroundColor(w.getColor());
            label.getStyle().getXPos().setRelative(w.calculateRelX(map.getWidth()), CssValue.Unit.RELATIVE_INT);
            label.getStyle().getYPos().setRelative(w.calculateRelY(map.getHeight()), CssValue.Unit.RELATIVE_INT);
            map.add(label);
        }
        System.out.println("Took " + (System.currentTimeMillis() - time) + " ms to calculate waypoints !");
        if (MapApp.customPoint != null)
            map.add(MapApp.customPoint);

        //====================== Geo localisation ======================
        final MapApp.MapWaypoint w = new MapApp.MapWaypoint((float) mc.player.posX, (float) mc.player.posZ, "Votre position", "V", -1);
        GuiLabel label = new GuiLabel(0, 0, 10, 10, w.getIcon()) {
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
        };
        label.getStyle().setPaddingLeft(1).setPaddingTop(1);
        label.getStyle().setFontColor(TextFormatting.RED);
        label.getStyle().getXPos().setRelative(w.calculateRelX(map.getWidth()), CssValue.Unit.RELATIVE_INT);
        label.getStyle().getYPos().setRelative(w.calculateRelY(map.getHeight()), CssValue.Unit.RELATIVE_INT);
        map.add(label);

        add(new GuiLabel(2, 2, getWidth() - 4, 20, "Carte de DrawLife, appuyez sur echap pour sortir. Clic droit pour mettre un marqueur personnalisé."));
    }

    @Override
    public List<ResourceLocation> getCssStyles() {
        return Collections.emptyList();
    }
}
