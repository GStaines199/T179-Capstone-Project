
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
    private static final int TAB_HOME = 0;
    private static final int TAB_GRID = 1;
    private static final int TAB_TEAM = 2;
    private static final int TAB_TRACK = 3;

    private final View templateView;
    private final Context pluginContext;
    private final SARTakMapController mapController;
    private final Button homeTabButton;
    private final Button gridTabButton;
    private final Button teamTabButton;
    private final Button trackTabButton;
    private final Button roleToggleButton;
    private final Button toggleSearchAreaButton;
    private final View homeTabContent;
    private final View gridTabContent;
    private final View teamTabContent;
    private final View trackTabContent;
    private final View leaderTeamControls;
    private final View otherTeamsSection;
    private final View gridStatusControls;
    private final TextView currentCellValue;
    private final TextView homeCellValue;
    private final TextView assignmentValue;
    private final TextView homeAssignmentValue;
    private final TextView teamSizeValue;
    private final TextView teamRosterValue;
    private final TextView teamAlertsValue;
    private final TextView homeAlertsValue;
    private final TextView currentRoleValue;
    private boolean leaderView = true;

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

        homeTabButton = templateView.findViewById(R.id.home_tab_button);
        gridTabButton = templateView.findViewById(R.id.grid_tab_button);
        teamTabButton = templateView.findViewById(R.id.team_tab_button);
        trackTabButton = templateView.findViewById(R.id.track_tab_button);
        roleToggleButton = templateView.findViewById(R.id.role_toggle_button);
        toggleSearchAreaButton = templateView
                .findViewById(R.id.toggle_search_area_button);
        homeTabContent = templateView.findViewById(R.id.home_tab_content);
        gridTabContent = templateView.findViewById(R.id.grid_tab_content);
        teamTabContent = templateView.findViewById(R.id.team_tab_content);
        trackTabContent = templateView.findViewById(R.id.track_tab_content);
        leaderTeamControls = templateView.findViewById(R.id.leader_team_controls);
        otherTeamsSection = templateView.findViewById(R.id.other_teams_section);
        gridStatusControls = templateView.findViewById(R.id.grid_status_controls);
        currentCellValue = templateView.findViewById(R.id.current_cell_value);
        homeCellValue = templateView.findViewById(R.id.home_cell_value);
        assignmentValue = templateView.findViewById(R.id.assignment_value);
        homeAssignmentValue = templateView.findViewById(R.id.home_assignment_value);
        teamSizeValue = templateView.findViewById(R.id.team_size_value);
        teamRosterValue = templateView.findViewById(R.id.team_roster_value);
        teamAlertsValue = templateView.findViewById(R.id.team_alerts_value);
        homeAlertsValue = templateView.findViewById(R.id.home_alerts_value);
        currentRoleValue = templateView.findViewById(R.id.current_role_value);

        homeTabButton.setOnClickListener(this);
        gridTabButton.setOnClickListener(this);
        teamTabButton.setOnClickListener(this);
        trackTabButton.setOnClickListener(this);
        roleToggleButton.setOnClickListener(this);
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
        showTab(TAB_HOME);
        refreshRoleUi();
        refreshGridUi();

    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
        mapController.dispose();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.home_tab_button) {
            showTab(TAB_HOME);
        } else if (id == R.id.grid_tab_button) {
            showTab(TAB_GRID);
        } else if (id == R.id.team_tab_button) {
            showTab(TAB_TEAM);
        } else if (id == R.id.track_tab_button) {
            showTab(TAB_TRACK);
        } else if (id == R.id.role_toggle_button) {
            leaderView = !leaderView;
            refreshRoleUi();
            Toast.makeText(getMapView().getContext(), leaderView
                    ? "Leader presentation view"
                    : "Member presentation view", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.toggle_search_area_button) {
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

    private void showTab(int tab) {
        homeTabContent.setVisibility(tab == TAB_HOME ? View.VISIBLE : View.GONE);
        gridTabContent.setVisibility(tab == TAB_GRID ? View.VISIBLE : View.GONE);
        teamTabContent.setVisibility(tab == TAB_TEAM ? View.VISIBLE : View.GONE);
        trackTabContent.setVisibility(tab == TAB_TRACK ? View.VISIBLE : View.GONE);

        homeTabButton.setEnabled(tab != TAB_HOME);
        gridTabButton.setEnabled(tab != TAB_GRID);
        teamTabButton.setEnabled(tab != TAB_TEAM);
        trackTabButton.setEnabled(tab != TAB_TRACK);
    }

    private void refreshRoleUi() {
        roleToggleButton.setText(leaderView
                ? R.string.leader_view
                : R.string.member_view);
        currentRoleValue.setText(leaderView
                ? R.string.leader_view
                : R.string.member_view);

        int leaderVisibility = leaderView ? View.VISIBLE : View.GONE;
        leaderTeamControls.setVisibility(leaderVisibility);
        otherTeamsSection.setVisibility(leaderVisibility);
        gridStatusControls.setVisibility(leaderVisibility);
    }

    private void refreshGridUi() {
        toggleSearchAreaButton.setText(mapController.isGridOverlayVisible()
                ? R.string.hide_lanes
                : R.string.show_lanes);
        String status = mapController.getSelectedCellStatus();
        String cellStatus = status.length() == 0
                ? mapController.getSelectedCellId()
                : mapController.getSelectedCellId() + " - " + status;
        String assignmentSummary = mapController.getAssignmentSummary();
        String alertSummary = mapController.getConnectionAlertSummary();

        currentCellValue.setText(cellStatus);
        homeCellValue.setText(cellStatus);
        assignmentValue.setText(assignmentSummary);
        homeAssignmentValue.setText(assignmentSummary);
        teamSizeValue.setText(String.valueOf(mapController.getTeamSize()));
        teamRosterValue.setText(mapController.getTeamRosterSummary());
        teamAlertsValue.setText(alertSummary);
        homeAlertsValue.setText(alertSummary);
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
