package fr.aym.gtwmap.client.gui;

import fr.aym.acsguis.component.GuiComponent;
import fr.aym.acsguis.utils.GuiTextureSprite;
import fr.aym.gtwmap.utils.GtwMapConstants;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class MapApp {
    public static final GuiTextureSprite TEXTURE = new GuiTextureSprite(new ResourceLocation(GtwMapConstants.ID, "textures/gui/apps/map.png"), 0, 0, 2231, 1307);

    public static GuiComponent<?> customPoint;
    double scrollX;
    double scrollY;
    private int mapWidth = 2280, mapHeight = 1303;

    public static final List<MapWaypoint> waypoints = new ArrayList<>();

    public static class MapWaypoint {
        private float x, y, imgX, imgY;
        private final int color;
        private final String label, icon;

        public MapWaypoint(float x, float y, String label, String icon) {
            this.x = x;
            this.y = y;
            this.label = label;
            this.icon = icon;
            this.color = -1;
            this.imgX = x;
            this.imgY = y;
        }

        public MapWaypoint(float x, float y, String label, String icon, int color) {
            this.x = x;
            this.y = y;
            this.label = label;
            this.icon = icon;
            this.color = color;

            //System.out.println("Read x: " + x);
            x -= -1850; //Convert x pos in a pos from spawn coords IG
            //System.out.println("Dist-x from spawn: " + x);
            x /= 2; //Adapt to the scale of the image from IG
            //System.out.println("Dist-x from spawn on img: " + x);
            x += 813;//Old : 797; //Add spawn coords on texture, to let the left upper corner the 0;0 point
            //System.out.println("Dist-x from origin on img: " + x);
            x -= 5; //To be centered in the middle (the label is 10x10 sized)
            imgX = x;

            //System.out.println("Read y: " + x);
            y -= -177;
            //System.out.println("Dist-y from spawn: " + y);
            y /= 2;
            //System.out.println("Dist-y from spawn on img: " + y);
            y += 267;//Old : 252;
            //System.out.println("Dist-y from origin on img: " + y);
            y -= 5; //To be centered in the middle (the label is 10x10 sized)
            imgY = y;
        }

        public float calculateRelX(int totalWidth) {
            return (float) (imgX / ((float) totalWidth));
        }

        public float calculateRelY(int totalHeight) {
            return (float) (imgY / ((float) totalHeight));
        }

        public String getLabel() {
            return label;
        }

        public String getIcon() {
            return icon;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public int getColor() {
            return color;
        }

        public void setImgX(float imgX) {
            this.imgX = imgX;
        }

        public void setImgY(float imgY) {
            this.imgY = imgY;
        }
    }
}
