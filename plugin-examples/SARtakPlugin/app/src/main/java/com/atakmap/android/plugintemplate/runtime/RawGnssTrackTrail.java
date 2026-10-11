package com.atakmap.android.plugintemplate.runtime;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.CrumbTrail;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Draws the searcher's track using ATAK's native crumb trail, fed only from raw
 * GNSS fixes.
 *
 * <p>ATAK attaches its own crumb trail to the self marker, whose position comes
 * from ATAK's fused location engine. That is the processed source the raw
 * capture work exists to move away from, so this class leaves it alone.
 * Instead it owns a hidden carrier marker positioned from each
 * {@link RawGnssCapture}, and attaches a {@link CrumbTrail} to that. ATAK's
 * native rendering, visibility handling and track database then all operate on
 * unmodified device fixes.
 *
 * <p>The carrier deliberately takes a UID of its own rather than the self
 * marker's. Sharing that UID would interleave these crumbs with ATAK's fused
 * crumbs inside one track in ATAK's crumb database -- the same blending of two
 * measurement pipelines that was removed from {@code location_points}, just
 * relocated somewhere harder to see.
 *
 * <p>Measurements the receiver did not report travel through here as
 * {@code NaN}, never as a stand-in zero. {@code CrumbDatabase} resolves NaN to
 * its own "unknown" value, whereas a substituted zero would be
 * indistinguishable from a real reading of 0 m/s or a due-north bearing.
 */
public class RawGnssTrackTrail {

    private static final String GROUP_NAME = "SARtak Raw GNSS Track";

    /** Appended to the ATAK UID so this track never merges with ATAK's own. */
    private static final String CARRIER_UID_SUFFIX = "-sartak-raw-gnss";

    private final MapView mapView;
    private final SharedPreferences preferences;

    private MapGroup trackGroup;
    private Marker carrier;
    private CrumbTrail trail;
    private String carrierUid;

    private boolean visible = true;
    private boolean recording = true;
    private long lastFixTimestamp;

    public RawGnssTrackTrail(MapView mapView) {
        this(mapView, PreferenceManager.getDefaultSharedPreferences(
                mapView.getContext()));
    }

    RawGnssTrackTrail(MapView mapView, SharedPreferences preferences) {
        this.mapView = mapView;
        this.preferences = preferences;
    }

    /**
     * Positions the carrier from one raw fix and asks ATAK to drop a crumb.
     *
     * <p>{@link CrumbTrail#crumb} does not consult its own tracking flag --
     * ATAK's BreadcrumbReceiver checks that before calling it -- so the
     * recording pause is enforced here instead. ATAK still applies its own
     * minimum-distance threshold, so a stationary device adds no crumbs even
     * though every fix is still written to {@code location_points}.
     */
    public void onRawFix(String uid, String callsign, RawGnssCapture capture) {
        if (uid == null || capture == null)
            return;

        GeoPoint point = toGeoPoint(capture);
        if (!point.isValid())
            return;

        ensureTrail(uid, callsign, point);
        if (trail == null || carrier == null)
            return;

        carrier.setPoint(point);
        applyReportedMeasurements(capture);
        lastFixTimestamp = capture.getTimestamp();

        if (!recording)
            return;
        trail.crumb(CrumbDatabase.instance());
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (trail != null)
            trail.setVisible(visible);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
        if (trail != null)
            trail.setTracking(recording);
    }

    public boolean isRecording() {
        return recording;
    }

    /** Clears the drawn crumbs. Rows already in ATAK's track database stay. */
    public void clear() {
        if (trail != null)
            trail.clearAllCrumbs();
    }

    /** True once a raw fix has arrived and the trail exists. */
    public boolean isReady() {
        return trail != null;
    }

    public int getCrumbCount() {
        return trail == null ? 0 : trail.size;
    }

    public long getLastFixTimestamp() {
        return lastFixTimestamp;
    }

    public void dispose() {
        if (trail != null) {
            trail.clearAllCrumbs();
            if (trail.getGroup() != null)
                trail.getGroup().removeItem(trail);
        }
        if (carrier != null && carrier.getGroup() != null)
            carrier.getGroup().removeItem(carrier);
        trail = null;
        carrier = null;
        carrierUid = null;
    }

    /**
     * Writes the measurements ATAK reads back off the carrier when it persists
     * a crumb: {@code CrumbDatabase} takes speed from the {@code Speed}
     * metadata or the track speed, and bearing from the track heading.
     */
    private void applyReportedMeasurements(RawGnssCapture capture) {
        double bearing = unreportedAsNaN(capture.getBearingDegrees());
        double speed = unreportedAsNaN(capture.getSpeedMps());

        // Written on every fix, NaN included. Leaving the previous value in
        // place when this fix reported none would attribute an older reading to
        // this position.
        carrier.setTrack(bearing, speed);
        carrier.setMetaDouble("Speed", speed);

        String source = reportedProvider(capture);
        carrier.setMetaString(GeoPointMetaData.GEOPOINT_SOURCE, source);
        carrier.setMetaString(GeoPointMetaData.ALTITUDE_SOURCE,
                capture.getAltitude() == null ? GeoPointMetaData.UNKNOWN
                        : source);
    }

    /**
     * Horizontal accuracy becomes CE90 and vertical accuracy LE90, so the
     * accuracy the receiver reported is carried into ATAK's track database
     * rather than being dropped at the boundary.
     */
    private GeoPoint toGeoPoint(RawGnssCapture capture) {
        return new GeoPoint(capture.getLatitude(), capture.getLongitude(),
                unreportedAsUnknown(capture.getAltitude()),
                GeoPoint.AltitudeReference.HAE,
                unreportedAsUnknown(capture.getAccuracyMeters()),
                unreportedAsUnknown(capture.getVerticalAccuracyMeters()));
    }

    private void ensureTrail(String uid, String callsign,
            GeoPoint initialPoint) {
        String desiredUid = uid + CARRIER_UID_SUFFIX;
        if (trail != null && desiredUid.equals(carrierUid)) {
            carrier.setTitle(trackTitle(callsign));
            return;
        }
        if (trail != null)
            dispose();

        ensureGroup();
        carrierUid = desiredUid;
        carrier = new Marker(initialPoint, carrierUid);
        carrier.setTitle(trackTitle(callsign));
        carrier.setMetaString("callsign", trackTitle(callsign));
        carrier.setMetaString("entry", "sartak");
        carrier.setMetaString("sartak.kind", "raw-gnss-carrier");
        carrier.setMetaBoolean("addToObjList", false);
        carrier.setMetaBoolean("archive", false);
        carrier.setMetaBoolean("editable", false);
        carrier.setMetaBoolean("movable", false);
        carrier.setMetaBoolean("removable", true);
        // The carrier exists only to give the crumb trail something to follow.
        // ATAK already draws the searcher's own position, so showing this as
        // well would put two markers on one person; crumbs are separate map
        // items and stay visible regardless.
        carrier.setVisible(false);
        trackGroup.addItem(carrier);

        trail = new CrumbTrail(mapView, carrier, preferences,
                carrierUid + "-trail");
        carrier.setCrumbTrail(trail);
        // Crumbs are added to the trail's own group, so it has to be in one
        // before the first crumb is dropped.
        trackGroup.addItem(trail);
        trail.setTracking(recording);
        trail.setVisible(visible);
    }

    private void ensureGroup() {
        if (trackGroup != null)
            return;
        trackGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (trackGroup == null)
            trackGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        trackGroup.setMetaBoolean("addToObjList", true);
    }

    private String trackTitle(String callsign) {
        String name = callsign == null || callsign.trim().length() == 0
                ? "SARtak" : callsign.trim();
        return name + " (SARtak raw GNSS)";
    }

    private String reportedProvider(RawGnssCapture capture) {
        String provider = capture.getProvider();
        if (provider == null || provider.trim().length() == 0)
            return GeoPointMetaData.UNKNOWN;
        return provider.trim();
    }

    private static double unreportedAsUnknown(Double value) {
        return value == null ? GeoPoint.UNKNOWN : value;
    }

    private static double unreportedAsNaN(Double value) {
        return value == null ? Double.NaN : value;
    }
}
