
package com.atakmap.android.plugintemplate;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.plugin.R;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.coremap.log.Log;

public class PluginTemplateDropDownReceiver extends DropDownReceiver implements
        OnStateListener, View.OnClickListener {

    public static final String TAG = PluginTemplateDropDownReceiver.class
            .getSimpleName();

    public static final String SHOW_PLUGIN = "com.atakmap.android.plugintemplate.SHOW_PLUGIN";
    private final View templateView;
    private final Context pluginContext;
    private final SARTakMapController mapController;
    private final Button toggleSearchAreaButton;
    private final TextView currentCellValue;
    private final TextView assignmentValue;
    private final TextView teamSizeValue;

    /**************************** CONSTRUCTOR *****************************/

    public PluginTemplateDropDownReceiver(final MapView mapView,
            final Context context) {
        super(mapView);
        this.pluginContext = context;
        this.mapController = new SARTakMapController(mapView, context);

        // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
        // In this case, using it is not necessary - but I am putting it here to remind
        // developers to look at this Inflator
        templateView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);

        toggleSearchAreaButton = templateView
                .findViewById(R.id.toggle_search_area_button);
        currentCellValue = templateView.findViewById(R.id.current_cell_value);
        assignmentValue = templateView.findViewById(R.id.assignment_value);
        teamSizeValue = templateView.findViewById(R.id.team_size_value);

        toggleSearchAreaButton.setOnClickListener(this);
        templateView.findViewById(R.id.select_current_cell_button)
                .setOnClickListener(this);
        templateView.findViewById(R.id.mark_partial_button)
                .setOnClickListener(this);
        templateView.findViewById(R.id.mark_complete_button)
                .setOnClickListener(this);
        templateView.findViewById(R.id.clear_cell_button)
                .setOnClickListener(this);
        templateView.findViewById(R.id.team_size_minus_button)
                .setOnClickListener(this);
        templateView.findViewById(R.id.team_size_plus_button)
                .setOnClickListener(this);
        refreshGridUi();

    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
        mapController.dispose();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.toggle_search_area_button) {
            boolean visible = mapController.toggleGridOverlay();
            Toast.makeText(getMapView().getContext(), visible
                    ? "SARtak search grid shown"
                    : "SARtak search grid hidden", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.select_current_cell_button) {
            mapController.selectCurrentCell();
            Toast.makeText(getMapView().getContext(),
                    "Selected current GPS search cell", Toast.LENGTH_SHORT)
                    .show();
        } else if (id == R.id.mark_partial_button) {
            mapController.markSelectedPartial();
        } else if (id == R.id.mark_complete_button) {
            mapController.markSelectedComplete();
        } else if (id == R.id.clear_cell_button) {
            mapController.clearSelectedStatus();
        } else if (id == R.id.team_size_minus_button) {
            mapController.decreaseTeamSize();
        } else if (id == R.id.team_size_plus_button) {
            mapController.increaseTeamSize();
        }

        refreshGridUi();
    }

    private void refreshGridUi() {
        toggleSearchAreaButton.setText(mapController.isGridOverlayVisible()
                ? R.string.hide_lanes
                : R.string.show_lanes);
        String status = mapController.getSelectedCellStatus();
        currentCellValue.setText(status.length() == 0
                ? mapController.getSelectedCellId()
                : mapController.getSelectedCellId() + " - " + status);
        assignmentValue.setText(mapController.getAssignmentSummary());
        teamSizeValue.setText(String.valueOf(mapController.getTeamSize()));
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_PLUGIN)) {

            Log.d(TAG, "showing plugin drop down");
            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

}
