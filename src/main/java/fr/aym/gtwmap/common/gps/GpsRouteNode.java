package fr.aym.gtwmap.common.gps;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GpsRouteNode implements Comparable<GpsRouteNode> {
    private final GpsNode current;
    private GpsNode previous;
    private double routeScore;
    private double estimatedScore;

    public GpsRouteNode(GpsNode current) {
        this(current, null, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public GpsRouteNode(GpsNode current, GpsNode previous, double routeScore, double estimatedScore) {
        this.current = current;
        this.previous = previous;
        this.routeScore = routeScore;
        this.estimatedScore = estimatedScore;
    }

    @Override
    public int compareTo(GpsRouteNode other) {
        if (this.estimatedScore > other.estimatedScore) {
            return 1;
        } else if (this.estimatedScore < other.estimatedScore) {
            return -1;
        } else {
            return 0;
        }
    }
}
