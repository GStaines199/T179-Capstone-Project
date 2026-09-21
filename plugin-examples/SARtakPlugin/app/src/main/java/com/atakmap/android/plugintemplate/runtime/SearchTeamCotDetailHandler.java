package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.cot.MarkerDetailHandler;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

public class SearchTeamCotDetailHandler implements MarkerDetailHandler {

    public static final String DETAIL_NAME = "__sartak_team";
    public static final String META_PREFIX = "sartak.team.";

    @Override
    public void toCotDetail(Marker marker, CotDetail detail) {
        if (!marker.hasMetaValue(META_PREFIX + "action"))
            return;
        CotDetail sartak = new CotDetail(DETAIL_NAME);
        copy(marker, sartak, "messageUid");
        copy(marker, sartak, "action");
        copy(marker, sartak, "operationId");
        copy(marker, sartak, "teamId");
        copy(marker, sartak, "teamName");
        copy(marker, sartak, "leaderUid");
        copy(marker, sartak, "leaderCallsign");
        copy(marker, sartak, "senderUid");
        copy(marker, sartak, "senderCallsign");
        copy(marker, sartak, "targetUid");
        copy(marker, sartak, "targetCallsign");
        copy(marker, sartak, "teamColorName");
        copy(marker, sartak, "teamColorArgb");
        copy(marker, sartak, "memberColorName");
        copy(marker, sartak, "memberColorArgb");
        copy(marker, sartak, "memberRole");
        copy(marker, sartak, "created");
        detail.addChild(sartak);
    }

    @Override
    public void toMarkerMetadata(Marker marker, CotEvent event,
            CotDetail detail) {
        restore(marker, detail, "messageUid");
        restore(marker, detail, "action");
        restore(marker, detail, "operationId");
        restore(marker, detail, "teamId");
        restore(marker, detail, "teamName");
        restore(marker, detail, "leaderUid");
        restore(marker, detail, "leaderCallsign");
        restore(marker, detail, "senderUid");
        restore(marker, detail, "senderCallsign");
        restore(marker, detail, "targetUid");
        restore(marker, detail, "targetCallsign");
        restore(marker, detail, "teamColorName");
        restore(marker, detail, "teamColorArgb");
        restore(marker, detail, "memberColorName");
        restore(marker, detail, "memberColorArgb");
        restore(marker, detail, "memberRole");
        restore(marker, detail, "created");
    }

    private void copy(Marker marker, CotDetail detail, String key) {
        detail.setAttribute(key, marker.getMetaString(META_PREFIX + key, ""));
    }

    private void restore(Marker marker, CotDetail detail, String key) {
        String value = detail.getAttribute(key);
        if (value != null)
            marker.setMetaString(META_PREFIX + key, value);
    }
}
