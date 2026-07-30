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

    /** Device identifiers used when ATAK cannot supply an identity. */
    interface DeviceFallback {
        String getDeviceId();

        String getDeviceModel();
    }

    private final Context context;
    private final MapView mapView;
    private final SearcherRepository searcherRepository;
    private Identity currentIdentity;

    private final DeviceFallback deviceFallback = new DeviceFallback() {
        @Override
        public String getDeviceId() {
            return Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        }

        @Override
        public String getDeviceModel() {
            return Build.MODEL;
        }
    };

    public IdentityManager(Context context, MapView mapView,
            SearcherRepository searcherRepository) {
        this.context = context;
        this.mapView = mapView;
        this.searcherRepository = searcherRepository;
    }

    public Identity resolveIdentity() {
        Marker self = mapView.getSelfMarker();
        String selfUid = self == null ? null : self.getUID();
        String selfCallsign = self == null ? null
                : self.getMetaString("callsign", self.getTitle());

        currentIdentity = resolve(MapView.getDeviceUid(),
                mapView.getDeviceCallsign(), selfUid, selfCallsign,
                deviceFallback);
        if (currentIdentity.isResolved()) {
            long now = System.currentTimeMillis();
            searcherRepository.insertOrUpdate(currentIdentity.getUid(),
                    currentIdentity.getCallsign(), Build.MODEL, now, now,
                    true);
        }
        return currentIdentity;
    }

    /**
     * Picks the UID and callsign to track under. ATAK's device identity wins,
     * then the self marker, then the device fallback. Kept free of ATAK types
     * so it can be unit tested on a plain JVM.
     */
    static Identity resolve(String atakUid, String atakCallsign, String selfUid,
            String selfCallsign, DeviceFallback fallback) {
        String uid = isEmpty(atakUid) ? selfUid : atakUid;
        String callsign = isEmpty(atakCallsign) ? selfCallsign : atakCallsign;

        boolean atakIdentity = !isEmpty(uid) && !isEmpty(callsign);
        String message;
        if (atakIdentity) {
            message = "Identity: " + callsign;
        } else {
            if (isEmpty(uid))
                uid = fallback.getDeviceId();
            if (isEmpty(callsign))
                callsign = fallback.getDeviceModel();
            message = "Using device identity fallback";
        }

        return new Identity(uid, callsign, message, atakIdentity);
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

    private static boolean isEmpty(String value) {
        return value == null || value.trim().length() == 0;
    }
}
