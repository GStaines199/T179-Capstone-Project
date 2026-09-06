package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.os.Build;

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

    /** Identity values used when ATAK cannot supply one. */
    interface DeviceFallback {
        String getFallbackUid();

        String getFallbackCallsign();
    }

    private final MapView mapView;
    private final SearcherRepository searcherRepository;
    private final DeviceIdentityStore identityStore;
    private Identity currentIdentity;

    private final DeviceFallback deviceFallback = new DeviceFallback() {
        @Override
        public String getFallbackUid() {
            return identityStore.getOrCreateUid();
        }

        @Override
        public String getFallbackCallsign() {
            return identityStore.getFallbackCallsign(Build.MODEL);
        }
    };

    public IdentityManager(Context context, MapView mapView,
            SearcherRepository searcherRepository) {
        this(mapView, searcherRepository, new DeviceIdentityStore(context));
    }

    IdentityManager(MapView mapView, SearcherRepository searcherRepository,
            DeviceIdentityStore identityStore) {
        this.mapView = mapView;
        this.searcherRepository = searcherRepository;
        this.identityStore = identityStore;
    }

    public Identity resolveIdentity() {
        Marker self = mapView.getSelfMarker();
        String selfUid = self == null ? null : self.getUID();
        String selfCallsign = self == null ? null
                : self.getMetaString("callsign", self.getTitle());

        currentIdentity = resolve(MapView.getDeviceUid(),
                mapView.getDeviceCallsign(), selfUid, selfCallsign,
                deviceFallback);
        if (currentIdentity.isResolved())
            remember(searcherRepository, currentIdentity, Build.MODEL,
                    System.currentTimeMillis());
        return currentIdentity;
    }

    /**
     * Stores the resolved identity as this device's self row, and reports
     * whether that succeeded.
     *
     * <p>A storage fault must not propagate out of identity resolution. Who we
     * are comes from ATAK, not from SQLite, and the callers that ask for it --
     * the CoT workflows, the capture loop, the panel -- run on ATAK's thread
     * every few seconds. Before this guard, a database that failed to open
     * turned every one of those into a crash, which meant the INACTIVE state
     * {@code StorageAvailability} exists to report was never on screen long
     * enough to read.
     *
     * <p>Swallowing the failure is only acceptable because it is already
     * reported: the startup probe puts the reason on the health panel and holds
     * the plugin in INACTIVE. This is not a silent catch, it is a second
     * symptom of a fault that is announced elsewhere.
     *
     * <p>Static and package-private so the failure path is reachable from a
     * test; the rest of this class needs a {@code MapView}.
     */
    static boolean remember(SearcherRepository repository, Identity identity,
            String deviceModel, long now) {
        try {
            // The UID changes when ATAK gains or loses an identity mid-session.
            // Without this the old row keeps its self flag and the stored self
            // identity becomes whichever of the two the query happens to hit.
            repository.clearSelfFlagExcept(identity.getUid());
            repository.insertOrUpdate(identity.getUid(),
                    identity.getCallsign(), deviceModel, now, now, true);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Picks the UID and callsign to track under. ATAK's device identity wins,
     * then the self marker, then this install's own persistent identity. Kept
     * free of ATAK types so it can be unit tested on a plain JVM.
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
                uid = fallback.getFallbackUid();
            if (isEmpty(callsign))
                callsign = fallback.getFallbackCallsign();
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
