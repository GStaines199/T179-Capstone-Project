package com.atakmap.android.plugintemplate.grid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Base64;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

public class SearchLineOverlay {

    private static final String GROUP_NAME = "SARtak Search Line Overlay";

    private final MapView mapView;
    private MapGroup lineGroup;
    private boolean visible = true;

    public SearchLineOverlay(MapView mapView) {
        this.mapView = mapView;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureLineGroup();
        if (!visible) {
            lineGroup.clearItems();
            lineGroup.setVisible(false);
        } else {
            lineGroup.setVisible(true);
        }
    }

    public void render(SearchLineManager manager) {
        if (!visible)
            return;
        ensureLineGroup();
        lineGroup.clearItems();
        if (manager.getState() == SearchLineState.NOT_STARTED
                || manager.getLineStart() == null
                || manager.getLineEnd() == null)
            return;

        int lineColor = manager.getColorOption().getArgb();
        DrawingShape line = createLine("SARtak Search Line",
                new GeoPoint[] { manager.getLineStart(),
                        manager.getLineEnd() },
                lineColor, manager.getState() == SearchLineState.PAUSED
                        ? 5.0 : 4.0);
        line.setMetaString("sartak.kind", "search-line");
        lineGroup.addItem(line);

        renderDirectionIndicator(manager);
        renderMemberReturnMarks(manager.getMemberStatuses(),
                manager.getState() == SearchLineState.PAUSED);
        renderSlowDownMarkers(manager.getMemberStatuses());
    }

    /**
     * Draws the arrow showing which way the line is advancing.
     *
     * <p>The shaft alone was ambiguous: a plain segment crossing the line
     * reads the same whichever end the team is walking towards, and it was
     * labelled only "Direction". Barbs at the tip and the compass name in the
     * label make it answerable at a glance, which is the point of drawing it.
     */
    private void renderDirectionIndicator(SearchLineManager manager) {
        GeoPoint start = manager.getDirectionStart();
        GeoPoint end = manager.getDirectionEnd();
        if (start == null || end == null)
            return;
        int color = Color.argb(220, 255, 255, 255);
        DrawingShape direction = createLine("Search Direction",
                new GeoPoint[] { start, end }, color, 3.0);
        direction.setMetaString("sartak.kind", "search-line-direction");
        lineGroup.addItem(direction);
        renderArrowHead(start, end, color);

        Marker label = createLabel(manager.getDirectionLabel(), end, color);
        label.setMetaString("sartak.kind", "search-line-direction-label");
        lineGroup.addItem(label);
    }

    /**
     * Two barbs swept back from the tip of the direction arrow.
     *
     * <p>Built in latitude and longitude rather than UTM because the overlay
     * has no converter and needs none: the barbs only have to look like an
     * arrowhead, and at the fourteen-metre scale of this arrow the difference
     * from true bearings is not visible. The longitude component is divided by
     * cos(latitude) so the barbs stay symmetric away from the equator instead
     * of splaying as the map is searched further south.
     */
    private void renderArrowHead(GeoPoint start, GeoPoint end, int color) {
        double latScale = Math.cos(Math.toRadians(end.getLatitude()));
        if (latScale < 0.01)
            latScale = 0.01;
        double dLat = end.getLatitude() - start.getLatitude();
        double dLon = (end.getLongitude() - start.getLongitude()) * latScale;
        double length = Math.sqrt(dLat * dLat + dLon * dLon);
        if (length <= 0.0)
            return;

        double unitLat = dLat / length;
        double unitLon = dLon / length;
        double barb = length * 0.35;
        // Rotate the reversed unit vector by +/- 30 degrees to get the barbs.
        double cos = Math.cos(Math.toRadians(30.0));
        double sin = Math.sin(Math.toRadians(30.0));
        addBarb(end, -unitLat, -unitLon, cos, sin, barb, latScale, color);
        addBarb(end, -unitLat, -unitLon, cos, -sin, barb, latScale, color);
    }

    private void addBarb(GeoPoint tip, double unitLat, double unitLon,
            double cos, double sin, double barb, double latScale, int color) {
        double lat = unitLat * cos - unitLon * sin;
        double lon = unitLat * sin + unitLon * cos;
        GeoPoint tail = new GeoPoint(tip.getLatitude() + lat * barb,
                tip.getLongitude() + lon * barb / latScale);
        DrawingShape shape = createLine("Search direction barb",
                new GeoPoint[] { tip, tail }, color, 3.0);
        shape.setMetaString("sartak.kind", "search-line-direction");
        lineGroup.addItem(shape);
    }

    private void renderMemberReturnMarks(List<SearchLineMemberStatus> statuses,
            boolean paused) {
        for (SearchLineMemberStatus status : statuses) {
            GeoPoint mark = status.getReturnMark();
            int color = paused ? Color.argb(230, 216, 182, 76)
                    : Color.argb(140, 255, 255, 255);
            double latOffset = 0.000035;
            double lonOffset = 0.000035;
            DrawingShape crossA = createLine("Search line return mark A",
                    new GeoPoint[] {
                            new GeoPoint(mark.getLatitude() - latOffset,
                                    mark.getLongitude() - lonOffset),
                            new GeoPoint(mark.getLatitude() + latOffset,
                                    mark.getLongitude() + lonOffset)
                    },
                    color, paused ? 3.0 : 2.0);
            crossA.setMetaString("sartak.kind", "search-line-return-mark");
            lineGroup.addItem(crossA);

            DrawingShape crossB = createLine("Search line return mark B",
                    new GeoPoint[] {
                            new GeoPoint(mark.getLatitude() - latOffset,
                                    mark.getLongitude() + lonOffset),
                            new GeoPoint(mark.getLatitude() + latOffset,
                                    mark.getLongitude() - lonOffset)
                    },
                    color, paused ? 3.0 : 2.0);
            crossB.setMetaString("sartak.kind", "search-line-return-mark");
            lineGroup.addItem(crossB);
        }
    }

    private void renderSlowDownMarkers(List<SearchLineMemberStatus> statuses) {
        for (SearchLineMemberStatus status : statuses) {
            // Checked explicitly rather than relying on isTooFarAhead alone:
            // this marker is drawn *at the member's coordinates*, so a missing
            // fix would put a "Slow/Hold" label on the map at a place the
            // searcher has never been.
            if (!MemberPositionPolicy.mayDrawSlowDownMarker(
                    status.getMember(),
                    status.isTooFarAhead(SearchLineManager
                            .SLOW_DOWN_THRESHOLD_METERS)))
                continue;
            GeoPoint point = new GeoPoint(status.getMember().getLatitude(),
                    status.getMember().getLongitude());
            Marker marker = createLabel("Slow/Hold", point,
                    Color.argb(230, 216, 84, 76));
            marker.setMetaString("sartak.kind", "search-line-slow-warning");
            lineGroup.addItem(marker);
        }
    }

    private DrawingShape createLine(String title, GeoPoint[] points,
            int strokeColor, double strokeWeight) {
        DrawingShape line = new DrawingShape(mapView, uid(title));
        line.setTitle(title);
        line.setPoints(points);
        line.setClosed(false);
        line.setStrokeColor(strokeColor);
        line.setStrokeWeight(strokeWeight);
        configureMapItem(line);
        return line;
    }

    private void ensureLineGroup() {
        if (lineGroup != null)
            return;
        lineGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (lineGroup == null)
            lineGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        lineGroup.setMetaBoolean("addToObjList", true);
    }

    private Marker createLabel(String title, GeoPoint point, int color) {
        Marker marker = new Marker(point, uid(title));
        marker.setTitle(title);
        marker.setAlwaysShowText(true);
        marker.setMetaString("callsign", title);
        marker.setMetaString("entry", "sartak");
        marker.setMetaBoolean("archive", false);
        marker.setMetaBoolean("editable", false);
        marker.setMetaBoolean("movable", false);
        marker.setMetaBoolean("removable", true);
        marker.setIcon(createDotIcon(color));
        return marker;
    }

    private Icon createDotIcon(int color) {
        Bitmap bitmap = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(14, 14, 8, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(14, 14, 8, paint);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        String encoded = "base64://" + Base64.encodeToString(
                stream.toByteArray(), Base64.NO_WRAP | Base64.URL_SAFE);
        return new Icon.Builder().setImageUri(0, encoded).build();
    }

    private void configureMapItem(MapItem item) {
        item.setMetaBoolean("archive", false);
        item.setMetaBoolean("editable", false);
        item.setMetaBoolean("movable", false);
        item.setMetaBoolean("removable", true);
        item.setMetaString("entry", "sartak");
        item.setMetaString("callsign", item.getTitle());
    }

    private String uid(String title) {
        return "sartak-line-"
                + title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-"
                + UUID.randomUUID();
    }
}
