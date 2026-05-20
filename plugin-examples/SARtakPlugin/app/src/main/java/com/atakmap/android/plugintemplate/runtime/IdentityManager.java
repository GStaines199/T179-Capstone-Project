package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.plugintemplate.database.SearcherRepository;

public class IdentityManager {

    public static class Identity {
        private final String uid;
        private final String callsign;
        private final String message;
        private final boolean atakIdentity;

        Identity(String uid, String callsign, String message,
                boolean atakIdentity) {
            this.uid = uid;
            this.callsign = callsign;
            this.message = message;
            this.atakIdentity = atakIdentity;
        }

        public String getUid() {
            return uid;
        }

        public String getCallsign() {
            return callsign;
        }

        public String getMessage() {
            return message;
        }

        public boolean isResolved() {
            return uid != null && uid.length() > 0
                    && callsign != null && callsign.length() > 0;
        }

        public boolean isAtakIdentity() {
            return atakIdentity;
        }
    }

    private final Context context;
    private final MapView mapView;
    private final SearcherRepository searcherRepository;
    private Identity currentIdentity;

    public IdentityManager(Context context, MapView mapView,
            SearcherRepository searcherRepository) {
        this.context = context;
        this.mapView = mapView;
        this.searcherRepository = searcherRepository;
    }

    public Identity resolveIdentity() {
        String uid = MapView.getDeviceUid();
        String callsign = mapView.getDeviceCallsign();
        Marker self = mapView.getSelfMarker();
        if (self != null) {
            if (isEmpty(uid))
                uid = self.getUID();
            if (isEmpty(callsign))
                callsign = self.getMetaString("callsign", self.getTitle());
        }

        boolean atakIdentity = !isEmpty(uid) && !isEmpty(callsign);
        String message = atakIdentity ? "Identity: " + callsign
                : "ATAK identity unavailable";

        if (!atakIdentity) {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            uid = isEmpty(uid) ? androidId : uid;
            callsign = isEmpty(callsign) ? Build.MODEL : callsign;
            message = "Using device identity fallback";
        }

        currentIdentity = new Identity(uid, callsign, message, atakIdentity);
        if (currentIdentity.isResolved()) {
            long now = System.currentTimeMillis();
            searcherRepository.insertOrUpdate(currentIdentity.getUid(),
                    currentIdentity.getCallsign(), Build.MODEL, now, now,
                    true);
        }
        return currentIdentity;
    }

    public Identity getCurrentIdentity() {
        if (currentIdentity == null)
            return resolveIdentity();
        return currentIdentity;
    }

    public String getIdentitySummary() {
        Identity identity = getCurrentIdentity();
        if (!identity.isResolved())
            return "Identity unavailable";
        return identity.getCallsign() + " | " + identity.getUid();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().length() == 0;
    }
}
