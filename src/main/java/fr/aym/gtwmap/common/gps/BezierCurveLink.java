package fr.aym.gtwmap.common.gps;

import fr.aym.acslib.utils.nbtserializer.ISerializable;
import fr.aym.acslib.utils.packetserializer.ISerializablePacket;
import lombok.Getter;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class BezierCurveLink implements ISerializable, ISerializablePacket {
    @Getter
    private final List<Vector2f> controlPoints;
    private List<Vector2f> renderPoints;

    public BezierCurveLink() {
        controlPoints = new ArrayList<>();
    }

    public BezierCurveLink(List<Vector2f> controlPoints) {
        this.controlPoints = controlPoints;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Object[] getObjectsToSave() {
        return new Object[]{controlPoints};
    }

    @Override
    public void populateWithSavedObjects(Object[] objects) {
        List<Vector2f> points = (List<Vector2f>) objects[0];
        controlPoints.clear();
        controlPoints.addAll(points);
    }

    // Method to compute a single point on a Bézier curve using de Casteljau's algorithm
    private static float[] deCasteljau(float t, List<Vector2f> controlPointsList) {
        float[][] controlPoints = controlPointsList.stream().map(p -> new float[]{p.x, p.y}).toArray(float[][]::new);
        int n = controlPoints.length;
        float[][] points = new float[n][2];
        System.arraycopy(controlPoints, 0, points, 0, n);
        for (int k = 1; k < n; k++) {
            for (int i = 0; i < n - k; i++) {
                points[i][0] = (1 - t) * points[i][0] + t * points[i + 1][0];
                points[i][1] = (1 - t) * points[i][1] + t * points[i + 1][1];
            }
        }
        return points[0];
    }

    public List<Vector2f> getRenderPoints() {
        if (renderPoints == null) {
            renderPoints = new ArrayList<>();
            int numPoints = Math.min(120, controlPoints.size() * 5);
            for (int i = 0; i <= numPoints; i++) {
                float t = i / (float) numPoints;
                float[] point = deCasteljau(t, controlPoints);
                renderPoints.add(new Vector2f(point[0], point[1]));
            }
        }
        return renderPoints;
    }

    public void clearRenderPoints() {
        renderPoints = null;
    }

    public void addControlPoint(Vector2f point) {
        controlPoints.add(point);
        clearRenderPoints();
    }
}
