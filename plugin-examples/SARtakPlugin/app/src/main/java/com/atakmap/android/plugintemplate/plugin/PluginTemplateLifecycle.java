
package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.plugintemplate.PluginTemplateMapComponent;

import gov.tak.api.plugin.IServiceController;

/**
 * Please note:
 *     Support for versions prior to 4.5.1 can make use of a copy of AbstractPluginLifeCycle shipped with
 *     the plugin.
 */
public class PluginTemplateLifecycle extends AbstractPlugin {

    private final static String TAG = "PluginTemplateLifecycle";

    public PluginTemplateLifecycle(IServiceController serviceController) {
        super(serviceController, new PluginTemplateTool(getPluginContext(serviceController)),
                new PluginTemplateMapComponent());
        PluginNativeLoader.init(getPluginContext(serviceController));
    }

    private static Context getPluginContext(IServiceController serviceController) {
        return serviceController.getService(PluginContextProvider.class)
                .getPluginContext();
    }

}
