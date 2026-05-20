package com.atakmap.android.plugintemplate.grid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Base64;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class SearchTeamMarkerOverlay {

    private static final String GROUP_NAME = "SARtak Team Markers";
    private static final int ICON_SIZE = 36;
    private static final int ICON_CENTER = ICON_SIZE / 2;

    private final MapView mapView;
    private final SearchPartyAssignmentManager assignmentManager;
    private final SearchLineManager searchLineManager;
    private MapGroup markerGroup;
    private TeamMarkerVisibilityMode visibilityMode =
            TeamMarkerVisibilityMode.MY_TEAM;
    private String selectedMemberId;
    private boolean showCallsigns = true;
    private boolean visible = true;

    public SearchTeamMarkerOverlay(MapView mapView,
            SearchPartyAssignmentManager assignmentManager,
            SearchLineManager searchLineManager) {
        this.mapView = mapView;
        this.assignmentManager = assignmentManager;
        this.searchLineManager = searchLineManager;
    }

    public void setVisibilityMode(TeamMarkerVisibilityMode visibilityMode) {
        this.visibilityMode = visibilityMode;
        render();
    }

    public TeamMarkerVisibilityMode getVisibilityMode() {
        return visibilityMode;
    }

    public void setSelectedMemberId(String selectedMemberId) {
        this.selectedMemberId = selectedMemberId;
        render();
    }

    public String getSelectedMemberId() {
        return selectedMemberId;
    }

    public boolean toggleCallsigns() {
        showCallsigns = !showCallsigns;
        render();
        return showCallsigns;
    }

    public boolean isShowingCallsigns() {
        return showCallsigns;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureMarkerGroup();
        if (!visible) {
            markerGroup.clearItems();
            markerGroup.setVisible(false);
        } else {
            markerGroup.setVisible(true);
            render();
        }
    }

    public void render() {
        if (!visible)
            return;

        ensureMarkerGroup();
        markerGroup.clearItems();
        List<SearchLineMemberStatus> lineStatuses = searchLineManager
                .getMemberStatuses();
        for (SearchTeamMember member : assignmentManager.getVisibleMembers()) {
            if (shouldShow(member))
                markerGroup.addItem(createMarker(member, lineStatuses));
        }
    }

    private boolean shouldShow(SearchTeamMember member) {
        // ATAK already draws this device's own location arrow. SARtak only adds
        // plugin markers for other team members to avoid duplicate self-icons.
        if (assignmentManager.getSelfMemberId().equals(member.getUniqueId()))
            return false;
        // Do not fabricate team-member positions. If ATAK has not supplied a
        // live contact location, keep the member in the roster but do not draw
        // a SARtak arrow on the map.
        if (!member.hasLiveAtakContact())
            return false;
        if (member.getUniqueId().equals(selectedMemberId))
            return true;
        switch (visibilityMode) {
            case ME_ONLY:
                return false;
            case LEADERS:
                return member.isTeamLeader();
            case ALL_VISIBLE:
            case MY_TEAM:
            default:
                return true;
        }
    }

    private Marker createMarker(SearchTeamMember member,
            List<SearchLineMemberStatus> lineStatuses) {
        GeoPoint point = new GeoPoint(member.getLatitude(),
                member.getLongitude());
        Marker marker = new Marker(point, "sartak-team-"
                + member.getUniqueId());
        marker.setTitle(showCallsigns ? member.getCallsign() : "");
        marker.setType(member.isTeamLeader()
                ? "a-f-G-U-C"
                : "a-f-G-U-C-I");
        marker.setAlwaysShowText(showCallsigns);
        marker.setTouchable(true);
        marker.setMetaString("callsign",
                showCallsigns ? member.getCallsign() : "");
        marker.setMetaString("entry", "sartak");
        marker.setMetaString("sartak.kind", "team-member-marker");
        marker.setMetaString("sartak.member.uid", member.getUniqueId());
        marker.setMetaString("sartak.member.role", member.getRoleLabel());
        marker.setMetaString("sartak.member.connection",
                member.getConnectionStatus().name());
        marker.setMetaBoolean("sartak.member.selected",
                member.getUniqueId().equals(selectedMemberId));
        marker.setMetaBoolean("archive", false);
        marker.setMetaBoolean("editable", false);
        marker.setMetaBoolean("movable", false);
        marker.setMetaBoolean("removable", true);
        marker.setMetaBoolean("adapt_marker_icon", false);
        marker.setIcon(createIcon(member,
                member.getUniqueId().equals(selectedMemberId),
                hasWarningOutline(member, lineStatuses)));
        return marker;
    }

    private Icon createIcon(SearchTeamMember member, boolean selected,
            boolean warningOutline) {
        Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(member.getDisplayColor());
        int outlineColor = getOutlineColor(member, warningOutline);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(selected ? 4 : 3);
        paint.setColor(outlineColor);

        if (member.hasReliableHeading()) {
            canvas.save();
            canvas.rotate((float) member.getHeadingDegrees(), ICON_CENTER,
                    ICON_CENTER);

            Path arrow = new Path();
            arrow.moveTo(ICON_CENTER, 3);
            arrow.lineTo(ICON_SIZE - 4, ICON_SIZE - 3);
            arrow.lineTo(ICON_CENTER, ICON_SIZE - 10);
            arrow.lineTo(4, ICON_SIZE - 3);
            arrow.close();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(member.getDisplayColor());
            canvas.drawPath(arrow, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(selected ? 4 : 3);
            paint.setColor(outlineColor);
            canvas.drawPath(arrow, paint);
            canvas.restore();
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(member.getDisplayColor());
            canvas.drawCircle(ICON_CENTER, ICON_CENTER, 10, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(selected ? 4 : 3);
            paint.setColor(outlineColor);
            canvas.drawCircle(ICON_CENTER, ICON_CENTER, 11, paint);
        }

        if (selected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.rgb(216, 182, 76));
            canvas.drawCircle(ICON_CENTER, ICON_CENTER, ICON_CENTER - 2,
                    paint);
        }

        if (member.getConnectionStatus()
                != SearchTeamMember.ConnectionStatus.CONNECTED) {
            paint.setStrokeWidth(3);
            paint.setColor(Color.rgb(20, 20, 20));
            canvas.drawLine(10, 10, ICON_SIZE - 10, ICON_SIZE - 10, paint);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        String encoded = "base64://" + Base64.encodeToString(
                stream.toByteArray(), Base64.NO_WRAP | Base64.URL_SAFE);
        return new Icon.Builder()
                .setAnchor(ICON_CENTER, ICON_CENTER)
                .setSize(ICON_SIZE, ICON_SIZE)
                .setImageUri(0, encoded)
                .build();
    }

    private int getOutlineColor(SearchTeamMember member,
            boolean warningOutline) {
        if (warningOutline)
            return Color.rgb(216, 84, 76);
        return member.getTeamColorArgb();
    }

    private boolean hasWarningOutline(SearchTeamMember member,
            List<SearchLineMemberStatus> lineStatuses) {
        if (member.needsConnectionAlert())
            return true;
        if (!searchLineManager.isStarted())
            return false;
        for (SearchLineMemberStatus status : lineStatuses) {
            if (member.getUniqueId().equals(status.getMember().getUniqueId())
                    && Math.abs(status.getDistanceFromLineMeters())
                            > SearchLineManager.SLOW_DOWN_THRESHOLD_METERS)
                return true;
        }
        return false;
    }

    private void ensureMarkerGroup() {
        if (markerGroup != null)
            return;
        markerGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (markerGroup == null)
            markerGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        markerGroup.setMetaBoolean("addToObjList", true);
    }
}
