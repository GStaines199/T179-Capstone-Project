package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

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

    public SearchGridCell cellForPoint(GeoPoint point,
            SearchGridStateStore stateStore) {
        UTMPoint utm = UTMPoint.fromGeoPoint(point);
        String zone = utm.getZoneDescriptor();
        double cellWest = floorToGrid(utm.getEasting(), BASE_CELL_SIZE_METERS);
        double cellSouth = floorToGrid(utm.getNorthing(), BASE_CELL_SIZE_METERS);
        double aggregateWest = floorToGrid(utm.getEasting(),
                AGGREGATE_GRID_SIZE_METERS);
        double aggregateSouth = floorToGrid(utm.getNorthing(),
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
}
