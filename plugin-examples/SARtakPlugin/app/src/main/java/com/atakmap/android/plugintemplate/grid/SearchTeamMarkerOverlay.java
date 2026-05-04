package com.atakmap.android.plugintemplate.grid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Base64;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.ByteArrayOutputStream;

public class SearchTeamMarkerOverlay {

    private static final String GROUP_NAME = "SARtak Team Markers";

    private final MapView mapView;
    private final SearchPartyAssignmentManager assignmentManager;
    private MapGroup markerGroup;
    private TeamMarkerVisibilityMode visibilityMode =
            TeamMarkerVisibilityMode.MY_TEAM;
    private String selectedMemberId;
    private boolean visible = true;

    public SearchTeamMarkerOverlay(MapView mapView,
            SearchPartyAssignmentManager assignmentManager) {
        this.mapView = mapView;
        this.assignmentManager = assignmentManager;
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
        for (SearchTeamMember member : assignmentManager.getVisibleMembers()) {
            if (shouldShow(member))
                markerGroup.addItem(createMarker(member));
        }
    }

    private boolean shouldShow(SearchTeamMember member) {
        if (member.getUniqueId().equals(selectedMemberId))
            return true;
        switch (visibilityMode) {
            case ME_ONLY:
                return assignmentManager.getSelfMemberId()
                        .equals(member.getUniqueId());
            case LEADERS:
                return assignmentManager.getSelfMemberId()
                        .equals(member.getUniqueId()) || member.isTeamLeader();
            case ALL_VISIBLE:
            case MY_TEAM:
            default:
                return true;
        }
    }

    private Marker createMarker(SearchTeamMember member) {
        GeoPoint point = new GeoPoint(member.getLatitude(),
                member.getLongitude());
        Marker marker = new Marker(point, "sartak-team-"
                + member.getUniqueId());
        marker.setTitle(member.getCallsign());
        marker.setType(member.isTeamLeader()
                ? "a-f-G-U-C"
                : "a-f-G-U-C-I");
        marker.setAlwaysShowText(true);
        marker.setTouchable(true);
        marker.setMetaString("callsign", member.getCallsign());
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
        marker.setMetaInteger("color", member.getDisplayColor());
        marker.setIcon(createIcon(member,
                member.getUniqueId().equals(selectedMemberId)));
        return marker;
    }

    private Icon createIcon(SearchTeamMember member, boolean selected) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        int markerColor = member.getDisplayColor();
        if (member.getConnectionStatus()
                == SearchTeamMember.ConnectionStatus.RECONNECTING)
            markerColor = blend(markerColor, Color.rgb(216, 182, 76));
        else if (member.getConnectionStatus()
                != SearchTeamMember.ConnectionStatus.CONNECTED)
            markerColor = Color.rgb(216, 84, 76);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(markerColor);
        canvas.drawOval(new RectF(8, 6, 56, 54), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(selected ? 8 : member.isTeamLeader() ? 6 : 4);
        paint.setColor(selected ? Color.rgb(216, 182, 76) : Color.WHITE);
        canvas.drawOval(new RectF(8, 6, 56, 54), paint);

        if (member.getConnectionStatus()
                != SearchTeamMember.ConnectionStatus.CONNECTED) {
            paint.setStrokeWidth(5);
            paint.setColor(Color.rgb(20, 20, 20));
            canvas.drawLine(18, 18, 46, 46, paint);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        String encoded = "base64://" + Base64.encodeToString(
                stream.toByteArray(), Base64.NO_WRAP | Base64.URL_SAFE);
        return new Icon.Builder().setImageUri(0, encoded).build();
    }

    private int blend(int first, int second) {
        return Color.rgb((Color.red(first) + Color.red(second)) / 2,
                (Color.green(first) + Color.green(second)) / 2,
                (Color.blue(first) + Color.blue(second)) / 2);
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
