package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;

public class OperationQrScanResultStore {

    private static final String PREFS_NAME = "sartak_operation_qr_scan";
    private static final String KEY_PENDING_JOIN_CODE = "pending_join_code";

    private OperationQrScanResultStore() {
    }

    public static void save(Context context, String joinCode) {
        if (context == null || joinCode == null
                || joinCode.trim().length() == 0)
            return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_PENDING_JOIN_CODE, joinCode.trim()).commit();
    }

    public static String consume(Context context) {
        if (context == null)
            return "";
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        String joinCode = preferences.getString(KEY_PENDING_JOIN_CODE, "");
        if (joinCode == null || joinCode.trim().length() == 0)
            return "";
        preferences.edit().remove(KEY_PENDING_JOIN_CODE).apply();
        return joinCode.trim();
    }
}
