
package com.atakmap.android.plugintemplate;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.dropdown.DropDownMapComponent;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotDetailHandler;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.plugintemplate.plugin.R;

public class PluginTemplateMapComponent extends DropDownMapComponent {

    private static final String TAG = "PluginTemplateMapComponent";

    private Context pluginContext;

    private PluginTemplateDropDownReceiver ddr;

    private DatabaseHelper database;
    private SearchTeamCotDetailHandler teamCotDetailHandler;

    public void onCreate(final Context context, Intent intent,
                         final MapView view) {
        context.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(context, intent, view);
        pluginContext = context;

        // Store SARtak runtime data in ATAK's app context. The plugin resource
        // context may not have its own writable databases directory.
        database = DatabaseHelper.getInstance(view.getContext());
        teamCotDetailHandler = new SearchTeamCotDetailHandler();
        CotDetailManager.getInstance().registerHandler(
                SearchTeamCotDetailHandler.DETAIL_NAME,
                teamCotDetailHandler);

        ddr = new PluginTemplateDropDownReceiver(view, context);

        Log.d(TAG, "registering the plugin filter");
        DocumentedIntentFilter ddFilter = new DocumentedIntentFilter();
        ddFilter.addAction(PluginTemplateDropDownReceiver.SHOW_PLUGIN);
        registerDropDownReceiver(ddr, ddFilter);
    }
    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
    }

}
