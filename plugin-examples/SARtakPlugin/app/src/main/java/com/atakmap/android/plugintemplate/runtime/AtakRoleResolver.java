package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MetaDataHolder2;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;

import java.util.Locale;

public class AtakRoleResolver {

    private static final String ATAK_CIV_PACKAGE = "com.atakmap.app.civ";

    public enum Role {
        TEAM_LEADER,
        TEAM_MEMBER
    }

    private AtakRoleResolver() {
    }

    public static Role resolve(MapView mapView) {
        String role = "";
        if (mapView != null) {
            role = getPreferenceRole(mapView.getContext());

            Marker self = mapView.getSelfMarker();
            if (isEmpty(role) && self != null) {
                role = firstNonEmpty(
                        self.getMetaString("atakRoleType", ""),
                        self.getMetaString("atakRole", ""),
                        self.getMetaString("role", ""),
                        self.getMetaString("teamRole", ""),
                        self.getMetaString("__groupRole", ""));
            }
            MetaDataHolder2 data = mapView.getMapData();
            if (isEmpty(role) && data != null) {
                role = firstNonEmpty(
                        data.getMetaString("atakRoleType", ""),
                        data.getMetaString("atakRole", ""),
                        data.getMetaString("role", ""),
                        data.getMetaString("teamRole", ""),
                        data.getMetaString("__groupRole", ""));
            }
            if (isEmpty(role) && self != null)
                role = self.getType();
        }

        if (isLeaderRoleValue(role))
            return Role.TEAM_LEADER;
        return Role.TEAM_MEMBER;
    }

    public static String label(Role role) {
        return role == Role.TEAM_LEADER ? "Team Leader" : "Team Member";
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!isEmpty(value))
                return value.trim();
        }
        return "";
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static String getPreferenceRole(Context context) {
        if (context == null)
            return "";
        String role = getPreferenceRoleFromContext(context);
        if (!isEmpty(role))
            return role;
        Context applicationContext = context.getApplicationContext();
        if (applicationContext != null && applicationContext != context) {
            role = getPreferenceRoleFromContext(applicationContext);
            if (!isEmpty(role))
                return role;
        }
        try {
            Context atakContext = context.createPackageContext(
                    ATAK_CIV_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            return getPreferenceRoleFromContext(atakContext);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String getPreferenceRoleFromContext(Context context) {
        if (context == null)
            return "";
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        return firstNonEmpty(
                preferences.getString("atakRoleType", ""),
                preferences.getString("atakRole", ""),
                preferences.getString("role", ""),
                preferences.getString("teamRole", ""));
    }

    private static boolean isLeaderRoleValue(String role) {
        String normalized = role == null ? ""
                : role.toLowerCase(Locale.US).trim();
        return normalized.contains("leader")
                || normalized.contains("lead")
                || normalized.contains("teamlead")
                || normalized.contains("team_lead");
    }
}


