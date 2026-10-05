package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts between geographic points and ATAK's UTM metre coordinates.
 *
 * Version zero does not have a public ATAK API for "the currently visible
 * 100 m grid square", so SARtak snaps the selected point to UTM 100 m
 * boundaries. ATAK's tactical grid is MGRS/UTM based, so this is a better
 * fallback than Web Mercator metre snapping and should visually track the
 * generated ATAK 100 m grid far more closely.
 */
public class GridCoordinateConverter {

    public static final double BASE_CELL_SIZE_METERS = 100.0;
    public static final double AGGREGATE_GRID_SIZE_METERS = 1000.0;
    public static final int AGGREGATE_CELLS_PER_SIDE = 10;
    private static final Pattern CELL_ID_PATTERN = Pattern.compile(
            "^utm-([^-]+)-c100-e(\\d+)-n(\\d+)$");

    public SearchGridCell cellForPoint(GeoPoint point,
            SearchGridStateStore stateStore) {
        UTMPoint utm = UTMPoint.fromGeoPoint(point);
        return cellForUtmPoint(utm.getZoneDescriptor(), utm.getEasting(),
                utm.getNorthing(), stateStore);
    }

    /**
     * Same as {@link #cellForPoint(GeoPoint, SearchGridStateStore)} but takes
     * pre-converted UTM coordinates. Kept free of ATAK types so it can be unit
     * tested on a plain JVM.
     */
    public SearchGridCell cellForUtmPoint(String zone, double easting,
            double northing, SearchGridStateStore stateStore) {
        double cellWest = floorToGrid(easting, BASE_CELL_SIZE_METERS);
        double cellSouth = floorToGrid(northing, BASE_CELL_SIZE_METERS);
        double aggregateWest = floorToGrid(easting, AGGREGATE_GRID_SIZE_METERS);
        double aggregateSouth = floorToGrid(northing,
                AGGREGATE_GRID_SIZE_METERS);

        int column = clamp((int) Math.floor((cellWest - aggregateWest)
                / BASE_CELL_SIZE_METERS), 0, AGGREGATE_CELLS_PER_SIDE - 1);
        int row = clamp((int) Math.floor((cellSouth - aggregateSouth)
                / BASE_CELL_SIZE_METERS), 0, AGGREGATE_CELLS_PER_SIDE - 1);

        String aggregateId = aggregateId(zone, aggregateWest, aggregateSouth);
        return createCell(aggregateId, zone, cellWest, cellSouth, row, column,
                stateStore);
    }

    public SearchGridCell createCell(String aggregateId, String zone,
            double west, double south, int row, int column,
            SearchGridStateStore stateStore) {
        double east = west + BASE_CELL_SIZE_METERS;
        double north = south + BASE_CELL_SIZE_METERS;
        String id = cellId(zone, west, south);
        return new SearchGridCell(aggregateId, id, row, column, zone, west,
                south, east, north, stateStore.getStatus(id));
    }

    public SearchGridCell cellForId(String cellId,
            SearchGridStateStore stateStore) {
        ParsedCellId parsed = parseCellId(cellId);
        if (parsed == null)
            return null;
        double aggregateWest = floorToGrid(parsed.west,
                AGGREGATE_GRID_SIZE_METERS);
        double aggregateSouth = floorToGrid(parsed.south,
                AGGREGATE_GRID_SIZE_METERS);
        int column = clamp((int) Math.floor((parsed.west - aggregateWest)
                / BASE_CELL_SIZE_METERS), 0, AGGREGATE_CELLS_PER_SIDE - 1);
        int row = clamp((int) Math.floor((parsed.south - aggregateSouth)
                / BASE_CELL_SIZE_METERS), 0, AGGREGATE_CELLS_PER_SIDE - 1);
        return createCell(aggregateId(parsed.zone, aggregateWest,
                aggregateSouth), parsed.zone, parsed.west, parsed.south, row,
                column, stateStore);
    }

    public GeoPoint toGeoPoint(String zone, double easting, double northing) {
        return new UTMPoint(zone, easting, northing).toGeoPoint();
    }

    public GeoPoint[] toGeoPoints(SearchGridCell cell) {
        return new GeoPoint[] {
                toGeoPoint(cell.getZoneDescriptor(), cell.getWest(),
                        cell.getSouth()),
                toGeoPoint(cell.getZoneDescriptor(), cell.getEast(),
                        cell.getSouth()),
                toGeoPoint(cell.getZoneDescriptor(), cell.getEast(),
                        cell.getNorth()),
                toGeoPoint(cell.getZoneDescriptor(), cell.getWest(),
                        cell.getNorth())
        };
    }

    public String cellIdForPoint(GeoPoint point) {
        UTMPoint utm = UTMPoint.fromGeoPoint(point);
        return cellIdForUtmPoint(utm.getZoneDescriptor(), utm.getEasting(),
                utm.getNorthing());
    }

    /**
     * Same as {@link #cellIdForPoint(GeoPoint)} but takes pre-converted UTM
     * coordinates. Kept free of ATAK types so it can be unit tested on a plain
     * JVM.
     */
    public String cellIdForUtmPoint(String zone, double easting,
            double northing) {
        double cellWest = floorToGrid(easting, BASE_CELL_SIZE_METERS);
        double cellSouth = floorToGrid(northing, BASE_CELL_SIZE_METERS);
        return cellId(zone, cellWest, cellSouth);
    }

    public String aggregateId(String zone, double west, double south) {
        return "utm-" + zone + "-agg1k-e" + Math.round(west) + "-n"
                + Math.round(south);
    }

    private String cellId(String zone, double west, double south) {
        return "utm-" + zone + "-c100-e" + Math.round(west) + "-n"
                + Math.round(south);
    }

    private double floorToGrid(double value, double gridSize) {
        return Math.floor(value / gridSize) * gridSize;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ParsedCellId parseCellId(String cellId) {
        if (cellId == null)
            return null;
        Matcher matcher = CELL_ID_PATTERN.matcher(cellId);
        if (!matcher.matches())
            return null;
        try {
            return new ParsedCellId(matcher.group(1),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class ParsedCellId {
        final String zone;
        final double west;
        final double south;

        ParsedCellId(String zone, double west, double south) {
            this.zone = zone;
            this.west = west;
            this.south = south;
        }
    }
}
