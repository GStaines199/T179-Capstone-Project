package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;

public class SearchGridCell {

    private final String aggregateId;
    private final String id;
    private final int row;
    private final int column;
    private final String zoneDescriptor;
    private final double west;
    private final double south;
    private final double east;
    private final double north;
    private SearchGridStatus status;

    public SearchGridCell(String aggregateId, String id, int row, int column,
            String zoneDescriptor, double west, double south, double east,
            double north, SearchGridStatus status) {
        this.aggregateId = aggregateId;
        this.id = id;
        this.row = row;
        this.column = column;
        this.zoneDescriptor = zoneDescriptor;
        this.west = west;
        this.south = south;
        this.east = east;
        this.north = north;
        this.status = status;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getId() {
        return id;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public String getZoneDescriptor() {
        return zoneDescriptor;
    }

    public double getWest() {
        return west;
    }

    public double getSouth() {
        return south;
    }

    public double getEast() {
        return east;
    }

    public double getNorth() {
        return north;
    }

    public SearchGridStatus getStatus() {
        return status;
    }

    public void setStatus(SearchGridStatus status) {
        this.status = status;
    }

    public GeoPoint[] toGeoPoints(GridCoordinateConverter converter) {
        return converter.toGeoPoints(this);
    }
}
