package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a SARtak overlay group in step with a list of item specs, touching
 * only the items that actually changed.
 * <p>
 * Items are plain ATAK {@link Polyline}s. {@code DrawingShape} was used
 * before, but every DrawingShape builds a centre marker that queries terrain
 * elevation on the UI thread; at ~15 ms each on an emulator, rebuilding a few
 * hundred cells froze ATAK for seconds and triggered "ATAK isn't responding".
 */
public class OverlayItemSync {

    /** Description of one overlay item. Two equal signatures draw the same. */
    public static final class Spec {
        final String uid;
        final String title;
        final String kind;
        final GeoPoint[] points;
        final boolean closed;
        final int fillColor;
        final int strokeColor;
        final double strokeWeight;
        final int basicLineStyle;
        private final Map<String, String> meta = new HashMap<>();
        private boolean labelled = true;

        public Spec(String uid, String title, String kind, GeoPoint[] points,
                boolean closed, int fillColor, int strokeColor,
                double strokeWeight) {
            this(uid, title, kind, points, closed, fillColor, strokeColor,
                    strokeWeight, Shape.BASIC_LINE_STYLE_SOLID);
        }

        public Spec(String uid, String title, String kind, GeoPoint[] points,
                boolean closed, int fillColor, int strokeColor,
                double strokeWeight, int basicLineStyle) {
            this.uid = uid;
            this.title = title == null ? "" : title;
            this.kind = kind == null ? "" : kind;
            this.points = points == null ? new GeoPoint[0] : points;
            this.closed = closed;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
            this.strokeWeight = strokeWeight;
            this.basicLineStyle = basicLineStyle;
        }

        public Spec meta(String key, String value) {
            meta.put(key, value == null ? "" : value);
            return this;
        }

        /** Never show a map label for this item, even with labels on. */
        public Spec unlabelled() {
            labelled = false;
            return this;
        }

        String signature(boolean showLabels) {
            StringBuilder builder = new StringBuilder(64 + points.length * 24);
            builder.append(title).append('|').append(kind).append('|')
                    .append(closed).append('|').append(fillColor).append('|')
                    .append(strokeColor).append('|').append(strokeWeight)
                    .append('|').append(basicLineStyle).append('|')
                    .append(showLabels && labelled).append('|').append(meta);
            for (GeoPoint point : points) {
                builder.append('|').append(point.getLatitude()).append(',')
                        .append(point.getLongitude());
            }
            return builder.toString();
        }
    }

    private final Map<String, MapItem> items = new HashMap<>();
    private final Map<String, String> signatures = new HashMap<>();
    private int lastAdded;
    private int lastRemoved;

    /**
     * Makes {@code group} contain exactly {@code specs}. Unchanged items are
     * left alone, so panning across a large area only creates the few items
     * that scrolled into the render window.
     */
    public void apply(MapGroup group, List<Spec> specs, boolean showLabels) {
        lastAdded = 0;
        lastRemoved = 0;
        Set<String> wanted = new HashSet<>();
        for (Spec spec : specs) {
            if (!wanted.add(spec.uid))
                continue;
            String signature = spec.signature(showLabels);
            MapItem existing = items.get(spec.uid);
            if (existing != null && existing.getGroup() == group
                    && signature.equals(signatures.get(spec.uid)))
                continue;
            if (existing != null) {
                group.removeItem(existing);
                lastRemoved++;
            }
            MapItem created = create(spec, showLabels);
            group.addItem(created);
            items.put(spec.uid, created);
            signatures.put(spec.uid, signature);
            lastAdded++;
        }
        Iterator<Map.Entry<String, MapItem>> iterator = items.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MapItem> entry = iterator.next();
            if (wanted.contains(entry.getKey()))
                continue;
            group.removeItem(entry.getValue());
            signatures.remove(entry.getKey());
            iterator.remove();
            lastRemoved++;
        }
    }

    public void clear(MapGroup group) {
        if (group != null)
            group.clearItems();
        items.clear();
        signatures.clear();
    }

    public int size() {
        return items.size();
    }

    public int getLastAdded() {
        return lastAdded;
    }

    public int getLastRemoved() {
        return lastRemoved;
    }

    private MapItem create(Spec spec, boolean labelsOn) {
        boolean showLabels = labelsOn && spec.labelled;
        Polyline line = new Polyline(spec.uid);
        line.setPoints(spec.points);
        int style = line.getStyle() | Shape.STYLE_STROKE_MASK;
        if (spec.closed)
            style |= Polyline.STYLE_CLOSED_MASK;
        else
            style &= ~Polyline.STYLE_CLOSED_MASK;
        if (spec.closed && Color.alpha(spec.fillColor) > 0)
            style |= Shape.STYLE_FILLED_MASK;
        else
            style &= ~Shape.STYLE_FILLED_MASK;
        line.setStyle(style);
        line.setStrokeColor(spec.strokeColor);
        line.setFillColor(spec.fillColor);
        line.setStrokeWeight(spec.strokeWeight);
        line.setBasicLineStyle(spec.basicLineStyle);
        line.setTitle(spec.title);
        if (showLabels && spec.title.length() > 0)
            line.setLineLabel(spec.title);
        line.setClickable(false);
        line.setMetaBoolean("archive", false);
        line.setMetaBoolean("editable", false);
        line.setMetaBoolean("movable", false);
        line.setMetaBoolean("removable", true);
        line.setMetaString("entry", "sartak");
        line.setMetaBoolean("labels_on", showLabels);
        line.setMetaString("callsign", showLabels ? spec.title : "");
        if (spec.kind.length() > 0)
            line.setMetaString("sartak.kind", spec.kind);
        for (Map.Entry<String, String> entry : spec.meta.entrySet())
            line.setMetaString(entry.getKey(), entry.getValue());
        return line;
    }
}
