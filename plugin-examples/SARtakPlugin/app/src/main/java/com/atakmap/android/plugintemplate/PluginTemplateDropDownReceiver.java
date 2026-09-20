
package com.atakmap.android.plugintemplate;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;
import com.atakmap.android.plugintemplate.grid.SearchTeamMember;
import com.atakmap.android.plugintemplate.grid.TeamMarkerVisibilityMode;
import com.atakmap.android.plugintemplate.runtime.AtakTeamContactDataSource;
import com.atakmap.android.plugintemplate.runtime.DeviceConnectivitySnapshot;
import com.atakmap.android.plugintemplate.runtime.DittoCredentialProfile;
import com.atakmap.android.plugintemplate.runtime.OperationQrCodeGenerator;
import com.atakmap.android.plugintemplate.runtime.OperationQrScanResultStore;
import com.atakmap.android.plugintemplate.runtime.SearchAreaAssignment;
import com.atakmap.android.plugintemplate.runtime.SearchAlertMessage;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotMessage;
import com.atakmap.android.plugintemplate.plugin.R;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.coremap.log.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PluginTemplateDropDownReceiver extends DropDownReceiver implements
        OnStateListener, View.OnClickListener {

    public static final String TAG = PluginTemplateDropDownReceiver.class
            .getSimpleName();

    public static final String SHOW_PLUGIN = "com.atakmap.android.plugintemplate.SHOW_PLUGIN";
    private static final int TAB_HOME = 0;
    private static final int TAB_GRID = 1;
    private static final int TAB_TEAM = 2;
    private static final int TAB_ALERTS = 3;
    private static final int TAB_DEVICES = 4;
    private static final int TAB_TRACK = 5;

    private final View templateView;
    private final Context pluginContext;
    private final SARTakMapController mapController;
    private final Button homeTabButton;
    private final Button gridTabButton;
    private final Button teamTabButton;
    private final Button alertsTabButton;
    private final Button devicesTabButton;
    private final Button trackTabButton;
    private final Switch toggleSearchAreaSwitch;
    private final Switch toggleGridLabelsSwitch;
    private final Button planSearchAreaButton;
    private final Button clearPlannedAreaButton;
    private final Button assignSearchAreaButton;
    private final Button addRouteCellButton;
    private final Button routeSelectionModeButton;
    private final Button toggleAssignmentOverlayButton;
    private final Button gridColourButton;
    private final Button generateRouteButton;
    private final Button toggleRouteButton;
    private final Button clearRouteButton;
    private final Button markerModeMeButton;
    private final Button markerModeTeamButton;
    private final Button markerModeLeadersButton;
    private final Button markerModeAllButton;
    private final Switch trackRecordSwitch;
    private final Switch trackVisibilitySwitch;
    private final Button trackClearButton;
    private final Button startSearchLineButton;
    private final Button pauseSearchLineButton;
    private final Switch toggleCallsignsSwitch;
    private final Button searchLineColourButton;
    private final Button saveTeamSetupButton;
    private final Button removeTeamButton;
    private final Button createOperationButton;
    private final Button showOperationJoinCodeButton;
    private final Button archiveOperationButton;
    private final Button joinOperationButton;
    private final Button leaveOperationButton;
    private final Button manageDittoButton;
    private final Button refreshAtakContactsButton;
    private final Button resetSyncStateButton;
    private final Button alertHoldPositionButton;
    private final Button alertRequestLeaderButton;
    private final Button alertEmergencyStopButton;
    private final EditText searchLineToleranceInput;
    private final EditText teamNameInput;
    private final EditText teamIdInput;
    private final EditText memberUidInput;
    private final EditText memberCallsignInput;
    private final View homeTabContent;
    private final View gridTabContent;
    private final View teamTabContent;
    private final View alertsTabContent;
    private final View devicesTabContent;
    private final View trackTabContent;
    private final View leaderTeamControls;
    private final View otherTeamsSection;
    private final View teamRosterSection;
    private final View gridStatusControls;
    private final View routePlanControls;
    private final View searchLineControls;
    private final View teamMarkerVisibilitySection;
    private final TextView currentCellValue;
    private final TextView homeCellValue;
    private final TextView assignmentValue;
    private final TextView homeAssignmentValue;
    private final TextView homeSearchLineValue;
    private final TextView searchLineStatusValue;
    private final TextView searchLineMembersValue;
    private final TextView pluginHealthValue;
    private final TextView operationSummaryValue;
    private final TextView dittoCredentialValue;
    private final TextView readinessChecklistValue;
    private final TextView identityValue;
    private final TextView homeGpsValue;
    private final TextView plannedAreaValue;
    private final TextView gridProgressValue;
    private final TextView routePlanValue;
    private final TextView nextCellValue;
    private final TextView teamSizeValue;
    private final TextView teamNameValue;
    private final LinearLayout teamMemberCardsContainer;
    private final LinearLayout gridReviewContainer;
    private final LinearLayout invitesRequestsContainer;
    private final LinearLayout hqTeamsContainer;
    private final LinearLayout hqAssignmentsContainer;
    private final LinearLayout alertsCardsContainer;
    private final LinearLayout devicesCardsContainer;
    private final TextView teamMarkerVisibilityValue;
    private final TextView atakContactStatusValue;
    private final TextView hqOperationSummaryValue;
    private final TextView devicesSummaryValue;
    private final TextView alertsSummaryValue;
    private final TextView teamAlertsValue;
    private final TextView homeAlertsValue;
    private final TextView currentRoleValue;
    private final TextView trackStatusValue;
    private final TextView trackDetailsValue;
    private final Handler uiRefreshHandler = new Handler(Looper.getMainLooper());
    private final Set<String> handledTeamMessages = new HashSet<>();
    private final Set<String> resolvedTeamMessages = new HashSet<>();
    private final Set<String> handledAlertMessages = new HashSet<>();
    private final Runnable uiRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshGridUi();
            uiRefreshHandler.postDelayed(this, 5000L);
        }
    };
    private int currentTab = TAB_HOME;
    private boolean leaderView = true;
    private boolean hqView;
    private boolean suppressToleranceUpdate;
    private boolean suppressSwitchUpdate;
    private boolean consumingPendingQrScan;
    private String activeGridReviewPromptCellId = "";

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
        alertsTabButton = templateView.findViewById(R.id.alerts_tab_button);
        devicesTabButton = templateView.findViewById(R.id.devices_tab_button);
        trackTabButton = templateView.findViewById(R.id.track_tab_button);
        toggleSearchAreaSwitch = templateView
                .findViewById(R.id.toggle_search_area_button);
        toggleGridLabelsSwitch = templateView
                .findViewById(R.id.toggle_grid_labels_button);
        planSearchAreaButton = templateView.findViewById(
                R.id.plan_search_area_button);
        clearPlannedAreaButton = templateView.findViewById(
                R.id.clear_planned_area_button);
        assignSearchAreaButton = templateView.findViewById(
                R.id.assign_search_area_button);
        addRouteCellButton = templateView.findViewById(
                R.id.add_route_cell_button);
        routeSelectionModeButton = templateView.findViewById(
                R.id.route_selection_mode_button);
        toggleAssignmentOverlayButton = templateView.findViewById(
                R.id.toggle_assignment_overlay_button);
        gridColourButton = templateView.findViewById(
                R.id.grid_colour_button);
        generateRouteButton = templateView.findViewById(
                R.id.generate_route_button);
        toggleRouteButton = templateView.findViewById(
                R.id.toggle_route_button);
        clearRouteButton = templateView.findViewById(
                R.id.clear_route_button);
        markerModeMeButton = templateView
                .findViewById(R.id.marker_mode_me_button);
        markerModeTeamButton = templateView
                .findViewById(R.id.marker_mode_team_button);
        markerModeLeadersButton = templateView
                .findViewById(R.id.marker_mode_leaders_button);
        markerModeAllButton = templateView
                .findViewById(R.id.marker_mode_all_button);
        trackRecordSwitch = templateView
                .findViewById(R.id.track_record_button);
        trackVisibilitySwitch = templateView
                .findViewById(R.id.track_visibility_button);
        trackClearButton = templateView.findViewById(R.id.track_clear_button);
        startSearchLineButton = templateView
                .findViewById(R.id.start_search_line_button);
        pauseSearchLineButton = templateView
                .findViewById(R.id.pause_search_line_button);
        toggleCallsignsSwitch = templateView
                .findViewById(R.id.toggle_callsigns_button);
        searchLineColourButton = templateView
                .findViewById(R.id.search_line_colour_button);
        saveTeamSetupButton = templateView
                .findViewById(R.id.save_team_setup_button);
        removeTeamButton = templateView
                .findViewById(R.id.remove_team_button);
        createOperationButton = templateView
                .findViewById(R.id.create_operation_button);
        showOperationJoinCodeButton = templateView
                .findViewById(R.id.show_operation_join_code_button);
        archiveOperationButton = templateView
                .findViewById(R.id.archive_operation_button);
        joinOperationButton = templateView
                .findViewById(R.id.join_operation_button);
        leaveOperationButton = templateView
                .findViewById(R.id.leave_operation_button);
        manageDittoButton = templateView.findViewById(
                R.id.manage_ditto_button);
        refreshAtakContactsButton = templateView
                .findViewById(R.id.refresh_atak_contacts_button);
        resetSyncStateButton = templateView
                .findViewById(R.id.reset_sync_state_button);
        alertHoldPositionButton = templateView
                .findViewById(R.id.alert_hold_position_button);
        alertRequestLeaderButton = templateView
                .findViewById(R.id.alert_request_leader_button);
        alertEmergencyStopButton = templateView
                .findViewById(R.id.alert_emergency_stop_button);
        searchLineToleranceInput = templateView
                .findViewById(R.id.search_line_tolerance_input);
        teamNameInput = templateView.findViewById(R.id.team_name_input);
        teamIdInput = templateView.findViewById(R.id.team_id_input);
        memberUidInput = templateView.findViewById(R.id.member_uid_input);
        memberCallsignInput = templateView
                .findViewById(R.id.member_callsign_input);
        homeTabContent = templateView.findViewById(R.id.home_tab_content);
        gridTabContent = templateView.findViewById(R.id.grid_tab_content);
        teamTabContent = templateView.findViewById(R.id.team_tab_content);
        alertsTabContent = templateView.findViewById(R.id.alerts_tab_content);
        devicesTabContent = templateView.findViewById(
                R.id.devices_tab_content);
        trackTabContent = templateView.findViewById(R.id.track_tab_content);
        leaderTeamControls = templateView.findViewById(R.id.leader_team_controls);
        otherTeamsSection = templateView.findViewById(R.id.other_teams_section);
        teamRosterSection = templateView.findViewById(R.id.team_roster_section);
        gridStatusControls = templateView.findViewById(R.id.grid_status_controls);
        routePlanControls = templateView.findViewById(R.id.route_plan_controls);
        searchLineControls = templateView.findViewById(R.id.search_line_controls);
        teamMarkerVisibilitySection = templateView
                .findViewById(R.id.team_marker_visibility_section);
        currentCellValue = templateView.findViewById(R.id.current_cell_value);
        homeCellValue = templateView.findViewById(R.id.home_cell_value);
        assignmentValue = templateView.findViewById(R.id.assignment_value);
        homeAssignmentValue = templateView.findViewById(R.id.home_assignment_value);
        homeSearchLineValue = templateView
                .findViewById(R.id.home_search_line_value);
        searchLineStatusValue = templateView
                .findViewById(R.id.search_line_status_value);
        searchLineMembersValue = templateView
                .findViewById(R.id.search_line_members_value);
        pluginHealthValue = templateView.findViewById(
                R.id.plugin_health_value);
        operationSummaryValue = templateView.findViewById(
                R.id.operation_summary_value);
        dittoCredentialValue = templateView.findViewById(
                R.id.ditto_credential_value);
        readinessChecklistValue = templateView.findViewById(
                R.id.readiness_checklist_value);
        identityValue = templateView.findViewById(R.id.identity_value);
        homeGpsValue = templateView.findViewById(R.id.home_gps_value);
        plannedAreaValue = templateView.findViewById(R.id.planned_area_value);
        gridProgressValue = templateView.findViewById(R.id.grid_progress_value);
        routePlanValue = templateView.findViewById(R.id.route_plan_value);
        nextCellValue = templateView.findViewById(R.id.next_cell_value);
        teamSizeValue = templateView.findViewById(R.id.team_size_value);
        teamNameValue = templateView.findViewById(R.id.team_name_value);
        teamMemberCardsContainer = templateView
                .findViewById(R.id.team_member_cards_container);
        gridReviewContainer = templateView.findViewById(
                R.id.grid_review_container);
        invitesRequestsContainer = templateView
                .findViewById(R.id.invites_requests_container);
        hqTeamsContainer = templateView.findViewById(
                R.id.hq_teams_container);
        hqAssignmentsContainer = templateView.findViewById(
                R.id.hq_assignments_container);
        alertsCardsContainer = templateView
                .findViewById(R.id.alerts_cards_container);
        devicesCardsContainer = templateView
                .findViewById(R.id.devices_cards_container);
        teamMarkerVisibilityValue = templateView
                .findViewById(R.id.team_marker_visibility_value);
        atakContactStatusValue = templateView
                .findViewById(R.id.atak_contact_status_value);
        hqOperationSummaryValue = templateView.findViewById(
                R.id.hq_operation_summary_value);
        devicesSummaryValue = templateView
                .findViewById(R.id.devices_summary_value);
        alertsSummaryValue = templateView
                .findViewById(R.id.alerts_summary_value);
        teamAlertsValue = templateView.findViewById(R.id.team_alerts_value);
        homeAlertsValue = templateView.findViewById(R.id.home_alerts_value);
        currentRoleValue = templateView.findViewById(R.id.current_role_value);
        trackStatusValue = templateView.findViewById(R.id.track_status_value);
        trackDetailsValue = templateView.findViewById(R.id.track_details_value);

        homeTabButton.setOnClickListener(this);
        gridTabButton.setOnClickListener(this);
        teamTabButton.setOnClickListener(this);
        alertsTabButton.setOnClickListener(this);
        devicesTabButton.setOnClickListener(this);
        trackTabButton.setOnClickListener(this);
        markerModeMeButton.setOnClickListener(this);
        markerModeTeamButton.setOnClickListener(this);
        markerModeLeadersButton.setOnClickListener(this);
        markerModeAllButton.setOnClickListener(this);
        trackClearButton.setOnClickListener(this);
        planSearchAreaButton.setOnClickListener(this);
        clearPlannedAreaButton.setOnClickListener(this);
        assignSearchAreaButton.setOnClickListener(this);
        addRouteCellButton.setOnClickListener(this);
        routeSelectionModeButton.setOnClickListener(this);
        toggleAssignmentOverlayButton.setOnClickListener(this);
        gridColourButton.setOnClickListener(this);
        generateRouteButton.setOnClickListener(this);
        toggleRouteButton.setOnClickListener(this);
        clearRouteButton.setOnClickListener(this);
        startSearchLineButton.setOnClickListener(this);
        pauseSearchLineButton.setOnClickListener(this);
        searchLineColourButton.setOnClickListener(this);
        saveTeamSetupButton.setOnClickListener(this);
        removeTeamButton.setOnClickListener(this);
        createOperationButton.setOnClickListener(this);
        showOperationJoinCodeButton.setOnClickListener(this);
        archiveOperationButton.setOnClickListener(this);
        joinOperationButton.setOnClickListener(this);
        leaveOperationButton.setOnClickListener(this);
        manageDittoButton.setOnClickListener(this);
        refreshAtakContactsButton.setOnClickListener(this);
        resetSyncStateButton.setOnClickListener(this);
        alertHoldPositionButton.setOnClickListener(this);
        alertRequestLeaderButton.setOnClickListener(this);
        alertEmergencyStopButton.setOnClickListener(this);
        setupToggleSwitches();
        setupToleranceInput();
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
        initialiseTeamInputs();
        showTab(TAB_HOME);

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
        } else if (id == R.id.alerts_tab_button) {
            showTab(TAB_ALERTS);
        } else if (id == R.id.devices_tab_button) {
            showTab(TAB_DEVICES);
        } else if (id == R.id.track_tab_button) {
            showTab(TAB_TRACK);
        } else if (id == R.id.marker_mode_me_button) {
            setTeamMarkerMode(TeamMarkerVisibilityMode.ME_ONLY);
        } else if (id == R.id.marker_mode_team_button) {
            setTeamMarkerMode(TeamMarkerVisibilityMode.MY_TEAM);
        } else if (id == R.id.marker_mode_leaders_button) {
            setTeamMarkerMode(TeamMarkerVisibilityMode.LEADERS);
        } else if (id == R.id.marker_mode_all_button) {
            setTeamMarkerMode(TeamMarkerVisibilityMode.ALL_VISIBLE);
        } else if (id == R.id.track_clear_button) {
            mapController.clearTrackHistory();
            Toast.makeText(getMapView().getContext(),
                    "Track history cleared", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.plan_search_area_button) {
            showPlanSearchAreaDialog();
        } else if (id == R.id.clear_planned_area_button) {
            mapController.clearPlannedSearchArea();
            Toast.makeText(getMapView().getContext(),
                    "Planned search area cleared", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.assign_search_area_button) {
            showAssignSearchAreaDialog();
        } else if (id == R.id.add_route_cell_button) {
            boolean added = mapController.addSelectedCellToRoute();
            Toast.makeText(getMapView().getContext(),
                    added ? "Selected/current cell added to route"
                            : "Select a cell and create a team first",
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.route_selection_mode_button) {
            boolean enabled = mapController.toggleRouteSelectionMode();
            Toast.makeText(getMapView().getContext(),
                    enabled ? "Tap visible grid cells to build the team route"
                            : "Route cell selection disabled",
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.toggle_assignment_overlay_button) {
            boolean visible = mapController.toggleAssignmentOverlay();
            Toast.makeText(getMapView().getContext(),
                    visible ? "Team assignment overlay shown"
                            : "Team assignment overlay hidden",
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.grid_colour_button) {
            String label = mapController.cycleGridColor();
            Toast.makeText(getMapView().getContext(),
                    "Grid colour: " + label, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.generate_route_button) {
            boolean generated = mapController
                    .generateSerpentineRouteFromPlannedArea();
            Toast.makeText(getMapView().getContext(),
                    generated ? "Serpentine route generated"
                            : "Plan a search area and create a team first",
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.toggle_route_button) {
            boolean visible = mapController.toggleRouteOverlay();
            Toast.makeText(getMapView().getContext(),
                    visible ? "Route guide shown" : "Route guide hidden",
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.clear_route_button) {
            mapController.clearRoutePlan();
            Toast.makeText(getMapView().getContext(),
                    "Route cleared", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.start_search_line_button) {
            if (mapController.isSearchLineStarted()) {
                mapController.endSearchLine();
                Toast.makeText(getMapView().getContext(),
                        "Search line ended", Toast.LENGTH_SHORT).show();
            } else {
                mapController.startSearchLine();
                Toast.makeText(getMapView().getContext(),
                        "Search line started", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.pause_search_line_button) {
            handlePauseResumeSearchLine();
        } else if (id == R.id.search_line_colour_button) {
            showLineColourDialog();
        } else if (id == R.id.save_team_setup_button) {
            if (leaderView) {
                if (mapController.isTeamCreated())
                    showTeamSetupDialog();
                else
                    showCreateTeamDialog();
            } else {
                if (mapController.isTeamCreated())
                    showLeaveTeamDialog();
                else
                    showJoinTeamDialog();
            }
        } else if (id == R.id.remove_team_button) {
            showRemoveTeamDialog();
        } else if (id == R.id.create_operation_button) {
            showCreateOperationDialog();
        } else if (id == R.id.show_operation_join_code_button) {
            showOperationJoinCodeDialog();
        } else if (id == R.id.archive_operation_button) {
            showArchiveOperationDialog();
        } else if (id == R.id.join_operation_button) {
            showJoinOperationDialog();
        } else if (id == R.id.leave_operation_button) {
            showLeaveOperationDialog();
        } else if (id == R.id.manage_ditto_button) {
            showDittoSetupDialog();
        } else if (id == R.id.refresh_atak_contacts_button) {
            Toast.makeText(getMapView().getContext(),
                    mapController.refreshAtakTeamContacts(),
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.reset_sync_state_button) {
            showResetSyncStateDialog();
        } else if (id == R.id.alert_hold_position_button) {
            handleAlertButton(SearchAlertMessage.TYPE_HOLD_POSITION);
        } else if (id == R.id.alert_request_leader_button) {
            handleAlertButton(SearchAlertMessage.TYPE_REQUEST_LEADER);
        } else if (id == R.id.alert_emergency_stop_button) {
            handleAlertButton(SearchAlertMessage.TYPE_EMERGENCY_STOP);
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
            Toast.makeText(getMapView().getContext(),
                    "Use the X on a member card to remove them.",
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.team_size_plus_button) {
            showAddMemberDialog();
        }

        refreshGridUi();
    }

    private void showTab(int tab) {
        currentTab = tab;
        homeTabContent.setVisibility(tab == TAB_HOME ? View.VISIBLE : View.GONE);
        gridTabContent.setVisibility(tab == TAB_GRID ? View.VISIBLE : View.GONE);
        teamTabContent.setVisibility(tab == TAB_TEAM ? View.VISIBLE : View.GONE);
        alertsTabContent.setVisibility(tab == TAB_ALERTS
                ? View.VISIBLE : View.GONE);
        devicesTabContent.setVisibility(tab == TAB_DEVICES
                ? View.VISIBLE : View.GONE);
        trackTabContent.setVisibility(tab == TAB_TRACK ? View.VISIBLE : View.GONE);

        homeTabButton.setEnabled(tab != TAB_HOME);
        gridTabButton.setEnabled(tab != TAB_GRID);
        teamTabButton.setEnabled(tab != TAB_TEAM);
        alertsTabButton.setEnabled(tab != TAB_ALERTS);
        devicesTabButton.setEnabled(tab != TAB_DEVICES);
        trackTabButton.setEnabled(tab != TAB_TRACK);
    }

    private void refreshRoleUi() {
        leaderView = mapController.isLeaderRole();
        hqView = mapController.isHqRole();
        if (currentRoleValue != null)
            currentRoleValue.setText(mapController.getRoleLabel());
        if (teamTabButton != null)
            teamTabButton.setText(hqView ? "Operation"
                    : pluginContext.getString(R.string.tab_team));
        if (trackTabButton != null)
            trackTabButton.setVisibility(hqView ? View.GONE : View.VISIBLE);
        if (hqView && currentTab == TAB_TRACK)
            showTab(TAB_TEAM);

        if (leaderTeamControls != null)
            leaderTeamControls.setVisibility(hqView ? View.GONE
                    : View.VISIBLE);
        if (teamRosterSection != null)
            teamRosterSection.setVisibility(hqView ? View.GONE
                    : View.VISIBLE);
        int leaderVisibility = leaderView && mapController.isTeamCreated()
                ? View.VISIBLE : View.GONE;
        // Cross-team hierarchy is intentionally hidden until it is backed by
        // confirmed SARtak/ATAK data. Showing placeholder teams caused users to
        // trust information that was not actually connected.
        if (otherTeamsSection != null)
            otherTeamsSection.setVisibility(hqView ? View.VISIBLE
                    : View.GONE);
        if (gridStatusControls != null)
            gridStatusControls.setVisibility(leaderVisibility);
        if (searchLineControls != null)
            searchLineControls.setVisibility(leaderVisibility);
        if (teamMarkerVisibilitySection != null)
            teamMarkerVisibilitySection.setVisibility(hqView
                    || mapController.isTeamCreated() ? View.VISIBLE
                            : View.GONE);
    }

    private void refreshGridUi() {
        consumePendingOperationQrScan();
        refreshRoleUi();
        boolean teamCreated = mapController.isTeamCreated();
        boolean hasOperation = mapController.hasActiveOperation();
        boolean archivedOperation = mapController.isOperationArchived();
        if (!leaderView && !hqView
                && (mapController.getTeamMarkerVisibilityMode()
                        == TeamMarkerVisibilityMode.LEADERS
                        || mapController.getTeamMarkerVisibilityMode()
                                == TeamMarkerVisibilityMode.ALL_VISIBLE)) {
            mapController.setTeamMarkerVisibilityMode(
                    TeamMarkerVisibilityMode.MY_TEAM);
        }
        updateSwitch(toggleSearchAreaSwitch,
                mapController.isGridOverlayVisible());
        updateSwitch(toggleGridLabelsSwitch,
                mapController.isShowingGridMapLabels());
        String cellStatus = mapController.getSelectedCellDisplaySummary();
        String assignmentSummary = mapController.getAssignmentSummary();
        String lineWarningSummary = mapController.getSearchLineWarningSummary();
        String alertSummary = mapController.getAlertSummary();
        String searchLineSummary = mapController.getSearchLineSummary();
        String searchLineMembers = mapController.getSearchLineMemberSummary();

        currentCellValue.setText(cellStatus);
        homeCellValue.setText(cellStatus);
        plannedAreaValue.setText(mapController.getPlannedSearchAreaSummary()
                + "\n\n" + mapController.getSearchAreaAssignmentSummary());
        nextCellValue.setText(mapController.getNextCellDisplaySummary());
        routePlanValue.setText(mapController.getRoutePlanSummary());
        assignmentValue.setText(assignmentSummary);
        homeAssignmentValue.setText(assignmentSummary);
        homeSearchLineValue.setText(searchLineSummary);
        searchLineStatusValue.setText(searchLineSummary);
        searchLineMembersValue.setText(searchLineMembers);
        pluginHealthValue.setText(mapController.getPluginHealthSummary());
        operationSummaryValue.setText(mapController.getOperationSummary());
        operationSummaryValue.setTextColor(hasOperation
                ? Color.rgb(66, 195, 106)
                : Color.rgb(216, 182, 76));
        dittoCredentialValue.setText(mapController.getDittoCredentialSummary());
        dittoCredentialValue.setTextColor(hasOperation || mapController
                .canCreateOperationFromLocalDittoConfig()
                        ? Color.rgb(66, 195, 106)
                        : Color.rgb(216, 84, 76));
        readinessChecklistValue.setText(colorReadinessSummary(mapController
                .getOperationReadinessSummary()));
        boolean canManageOperation = mapController.canManageOperation();
        boolean canManageSearchArea = mapController.canManageSearchArea();
        createOperationButton.setVisibility(!hasOperation && canManageOperation
                ? View.VISIBLE : View.GONE);
        joinOperationButton.setVisibility(hasOperation || canManageOperation
                ? View.GONE : View.VISIBLE);
        showOperationJoinCodeButton.setVisibility(hasOperation
                && canManageOperation && !archivedOperation
                ? View.VISIBLE : View.GONE);
        archiveOperationButton.setVisibility(hasOperation
                && canManageOperation && !archivedOperation
                ? View.VISIBLE : View.GONE);
        leaveOperationButton.setVisibility(hasOperation
                ? View.VISIBLE : View.GONE);
        manageDittoButton.setVisibility(canManageOperation
                ? View.VISIBLE : View.GONE);
        identityValue.setText(mapController.getIdentitySummary());
        homeGpsValue.setText(mapController.getGpsSummary());
        homeGpsValue.setTextColor(mapController.isGpsActive()
                ? Color.rgb(66, 195, 106)
                : Color.rgb(216, 84, 76));
        gridProgressValue.setText(mapController.getGridProgressSummary());
        teamSizeValue.setText(teamCreated
                ? String.valueOf(mapController.getTeamSize()) : "0");
        teamNameValue.setText(teamCreated
                ? mapController.getTeamName() + " | Colour: "
                        + mapController.getTeamColorName() + " | "
                        + mapController.getTeamId()
                : (hqView ? "HQ device: not assigned to a field team"
                        : leaderView ? "No team created" : "Not in a team"));
        int visibleTeamCount = mapController.getVisibleTeamAdvertisementCount();
        saveTeamSetupButton.setText(hqView
                ? "HQ monitors operation teams"
                : leaderView
                ? (!hasOperation ? "Select Operation First"
                        : teamCreated ? "Edit Team Setup"
                        : "Create Team")
                : (!hasOperation ? "Select Operation First"
                        : teamCreated ? "Leave Team" : "Join Team ("
                        + visibleTeamCount + " visible)"));
        saveTeamSetupButton.setEnabled(hasOperation && !hqView
                && !archivedOperation);
        refreshAtakContactsButton.setVisibility(leaderView
                && teamCreated ? View.VISIBLE : View.GONE);
        removeTeamButton.setVisibility(leaderView && teamCreated
                ? View.VISIBLE : View.GONE);
        templateView.findViewById(R.id.team_size_plus_button).setVisibility(
                leaderView && teamCreated
                        ? View.VISIBLE : View.GONE);
        boolean hasSelectedCell = !"No cell selected".equals(mapController
                .getSelectedCellId());
        gridStatusControls.setVisibility(((leaderView && teamCreated)
                || hqView) && hasSelectedCell ? View.VISIBLE : View.GONE);
        planSearchAreaButton.setVisibility(canManageSearchArea
                ? View.VISIBLE : View.GONE);
        clearPlannedAreaButton.setVisibility(canManageSearchArea
                ? View.VISIBLE : View.GONE);
        assignSearchAreaButton.setVisibility(hqView
                ? View.VISIBLE : View.GONE);
        routePlanControls.setVisibility(leaderView && teamCreated
                ? View.VISIBLE : View.GONE);
        planSearchAreaButton.setEnabled(hasOperation && !archivedOperation);
        clearPlannedAreaButton.setEnabled(hasOperation
                && !archivedOperation);
        assignSearchAreaButton.setEnabled(hasOperation && !archivedOperation);
        boolean canManageRoute = mapController.canManageRoutePlan();
        addRouteCellButton.setEnabled(canManageRoute);
        routeSelectionModeButton.setEnabled(canManageRoute);
        routeSelectionModeButton.setText(mapController.isRouteSelectionMode()
                ? "Stop Selecting Cells" : "Select Route Cells");
        toggleAssignmentOverlayButton.setText(mapController
                .isAssignmentOverlayVisible()
                        ? "Hide Team Areas" : "Show Team Areas");
        gridColourButton.setText("Grid Colour: "
                + mapController.getGridColorLabel());
        generateRouteButton.setEnabled(canManageRoute);
        clearRouteButton.setEnabled(canManageRoute);
        toggleRouteButton.setText(mapController.isRouteOverlayVisible()
                ? "Hide Route" : "Show Route");
        setInputTextIfIdle(teamNameInput, mapController.getTeamName());
        setInputTextIfIdle(teamIdInput, mapController.getTeamId());
        teamMarkerVisibilityValue.setText("Showing: "
                + mapController.getTeamMarkerVisibilityLabel());
        updateMarkerVisibilityButtons();
        atakContactStatusValue.setText(mapController.getTeamSyncSummary());
        if (hqOperationSummaryValue != null)
            hqOperationSummaryValue.setText(mapController
                    .getOperationDashboardSummary() + "\n\n"
                    + mapController.getSearchAreaAssignmentSummary());
        if (currentTab == TAB_TEAM && hqView)
            renderHqOperationCards();
        if (currentTab == TAB_TEAM) {
            renderTeamMemberCards();
            renderInvitesAndRequests();
        }
        if (currentTab == TAB_DEVICES)
            renderDeviceCards();
        if (currentTab == TAB_ALERTS)
            renderAlertCards();
        updateAlertButtons(teamCreated);
        teamAlertsValue.setText(alertSummary);
        homeAlertsValue.setText(alertSummary);
        alertsSummaryValue.setText(alertSummary);
        if (!"No team connection alerts".equals(lineWarningSummary)
                && lineWarningSummary.length() > 0) {
            teamAlertsValue.setText(alertSummary + "\n" + lineWarningSummary);
            homeAlertsValue.setText(alertSummary + "\n" + lineWarningSummary);
        }
        trackStatusValue.setText(mapController.getTrackStatusSummary());
        trackDetailsValue.setText(mapController.getTrackDetailsSummary());
        pollTeamCotMessages();
        pollGridProgressPrompt();
        if (currentTab == TAB_GRID)
            renderGridReviewCards();
        updateSwitch(trackRecordSwitch, mapController.isTrackRecording());
        updateSwitch(trackVisibilitySwitch, mapController.isTrackVisible());
        trackClearButton.setVisibility(mapController.hasVisibleTrackData()
                ? View.VISIBLE : View.GONE);
        startSearchLineButton.setText(mapController.isSearchLineStarted()
                ? R.string.search_line_end
                : R.string.search_line_start);
        pauseSearchLineButton.setText(mapController.isSearchLinePaused()
                ? R.string.search_line_resume
                : R.string.search_line_pause);
        pauseSearchLineButton.setVisibility(mapController.isSearchLineStarted()
                ? View.VISIBLE : View.GONE);
        updateSwitch(toggleCallsignsSwitch,
                mapController.isShowingTeamCallsigns());
        searchLineColourButton.setText("Line Colour: "
                + mapController.getSearchLineColorLabel());
        String toleranceText = String.valueOf(Math.round(mapController
                .getSearchLineToleranceMeters()));
        if (!toleranceText.contentEquals(searchLineToleranceInput.getText())) {
            suppressToleranceUpdate = true;
            searchLineToleranceInput.setText(toleranceText);
            searchLineToleranceInput.setSelection(searchLineToleranceInput
                    .getText().length());
            suppressToleranceUpdate = false;
        }
    }

    private void showLineColourDialog() {
        final SearchLineColorOption[] values = SearchLineColorOption.values();
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Search line colour")
                .setSingleChoiceItems(getLineColourLabels(),
                        mapController.getSearchLineColorIndex(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (which >= 0 && which < values.length) {
                                    mapController.setSearchLineColor(
                                            values[which]);
                                    refreshGridUi();
                                }
                                dialog.dismiss();
                            }
                        })
                .show();
    }

    private String[] getLineColourLabels() {
        SearchLineColorOption[] values = SearchLineColorOption.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].getLabel();
        }
        return labels;
    }

    private void showPlanSearchAreaDialog() {
        LinearLayout content = new LinearLayout(getMapView().getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), 0);

        final Spinner shapeInput = new Spinner(getMapView().getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getMapView()
                .getContext(), android.R.layout.simple_spinner_item,
                new String[] { "Circle radius", "Box width / height" });
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        shapeInput.setAdapter(adapter);
        content.addView(shapeInput);

        final EditText radiusInput = addDialogInput(content,
                "Radius in km", "1", true);
        final EditText widthInput = addDialogInput(content,
                "Box width in km", "1", true);
        final EditText heightInput = addDialogInput(content,
                "Box height in km", "1", true);
        widthInput.setVisibility(View.GONE);
        heightInput.setVisibility(View.GONE);

        shapeInput.setOnItemSelectedListener(new AdapterView
                .OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                boolean circle = position == 0;
                radiusInput.setVisibility(circle ? View.VISIBLE : View.GONE);
                widthInput.setVisibility(circle ? View.GONE : View.VISIBLE);
                heightInput.setVisibility(circle ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Plan search area")
                .setView(content)
                .setPositiveButton("Create",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (shapeInput.getSelectedItemPosition() == 0) {
                                    mapController.planCircleSearchArea(parseKm(
                                            radiusInput, 1.0));
                                    Toast.makeText(getMapView().getContext(),
                                            "Planned circle search area",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    double width = parseKm(widthInput, 1.0);
                                    mapController.planBoxSearchArea(width,
                                            parseKm(heightInput, width));
                                    Toast.makeText(getMapView().getContext(),
                                            "Planned box search area",
                                            Toast.LENGTH_SHORT).show();
                                }
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAssignSearchAreaDialog() {
        if (!mapController.isHqRole()) {
            Toast.makeText(getMapView().getContext(),
                    "Only HQ devices can assign search areas",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final java.util.List<SearchTeamCotMessage> teams = mapController
                .getActiveTeamAdvertisements();
        if (teams.isEmpty()) {
            Toast.makeText(getMapView().getContext(),
                    "No SARtak teams visible to assign yet",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[teams.size()];
        for (int i = 0; i < teams.size(); i++) {
            SearchTeamCotMessage team = teams.get(i);
            labels[i] = team.getTeamName() + "\nLeader: "
                    + team.getLeaderCallsign();
        }
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Assign Search Area")
                .setMessage("Assign the current planned search area to a team.")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SearchTeamCotMessage team = teams.get(which);
                        boolean assigned = mapController
                                .assignPlannedAreaToTeam(team);
                        Toast.makeText(getMapView().getContext(),
                                assigned ? "Search area assigned to "
                                        + team.getTeamName()
                                        : "Plan a search area before assigning",
                                Toast.LENGTH_LONG).show();
                        refreshGridUi();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupToggleSwitches() {
        toggleSearchAreaSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (suppressSwitchUpdate)
                            return;
                        if (mapController.isGridOverlayVisible() != isChecked)
                            mapController.toggleGridOverlay();
                        Toast.makeText(getMapView().getContext(), isChecked
                                ? "SARtak search grid shown"
                                : "SARtak search grid hidden",
                                Toast.LENGTH_SHORT).show();
                        refreshGridUi();
                    }
                });

        toggleGridLabelsSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (suppressSwitchUpdate)
                            return;
                        if (mapController.isShowingGridMapLabels()
                                != isChecked)
                            mapController.toggleGridMapLabels();
                        Toast.makeText(getMapView().getContext(), isChecked
                                ? "SARtak grid labels shown"
                                : "SARtak grid labels hidden",
                                Toast.LENGTH_SHORT).show();
                        refreshGridUi();
                    }
                });

        toggleCallsignsSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (suppressSwitchUpdate)
                            return;
                        if (mapController.isShowingTeamCallsigns()
                                != isChecked)
                            mapController.toggleTeamCallsigns();
                        Toast.makeText(getMapView().getContext(), isChecked
                                ? "Callsigns shown" : "Callsigns hidden",
                                Toast.LENGTH_SHORT).show();
                        refreshGridUi();
                    }
                });

        trackRecordSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (suppressSwitchUpdate)
                            return;
                        if (mapController.isTrackRecording() != isChecked)
                            mapController.toggleTrackRecording();
                        Toast.makeText(getMapView().getContext(), isChecked
                                ? "Track recording resumed"
                                : "Track recording paused",
                                Toast.LENGTH_SHORT).show();
                        refreshGridUi();
                    }
                });

        trackVisibilitySwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (suppressSwitchUpdate)
                            return;
                        if (mapController.isTrackVisible() != isChecked)
                            mapController.toggleTrackVisibility();
                        Toast.makeText(getMapView().getContext(), isChecked
                                ? "Track shown on map"
                                : "Track hidden from map",
                                Toast.LENGTH_SHORT).show();
                        refreshGridUi();
                    }
                });
    }

    private void updateSwitch(Switch toggleSwitch, boolean checked) {
        if (toggleSwitch == null || toggleSwitch.isChecked() == checked)
            return;
        suppressSwitchUpdate = true;
        toggleSwitch.setChecked(checked);
        suppressSwitchUpdate = false;
    }

    private void setupToleranceInput() {
        searchLineToleranceInput.setText(String.valueOf(Math.round(
                mapController.getSearchLineToleranceMeters())));
        searchLineToleranceInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (suppressToleranceUpdate)
                    return;
                String value = editable.toString().trim();
                if (value.length() == 0)
                    return;
                try {
                    mapController.setSearchLineTolerance(Double
                            .parseDouble(value));
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }

    private void showTeamSetupDialog() {
        final EditText input = new EditText(getMapView().getContext());
        input.setSingleLine(true);
        input.setText(mapController.getTeamName());
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Edit team setup")
                .setMessage("Team ID is fixed to this leader device:\n"
                        + mapController.getTeamId())
                .setView(input)
                .setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.updateTeamSetup(input.getText()
                                        .toString().trim());
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCreateTeamDialog() {
        final EditText input = new EditText(getMapView().getContext());
        input.setSingleLine(true);
        input.setHint("Team name");
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Create Team")
                .setMessage("Team ID will be generated from this leader device:\n"
                        + mapController.getTeamId())
                .setView(input)
                .setPositiveButton("Create",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                String name = input.getText().toString()
                                        .trim();
                                if (name.length() == 0)
                                    name = "SAR Team";
                                mapController.createTeam(name);
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDittoSetupDialog() {
        DittoCredentialProfile selected = mapController
                .getSelectedDittoCredentialProfile();
        List<DittoCredentialProfile> profiles = mapController
                .getDittoCredentialProfiles();

        LinearLayout content = new LinearLayout(getMapView().getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), 0);

        TextView summary = new TextView(getMapView().getContext());
        summary.setText(mapController.getDittoCredentialSummary());
        summary.setTextColor(Color.LTGRAY);
        content.addView(summary);

        Button addButton = createDialogButton("Add New Ditto Profile");
        content.addView(addButton);

        Button updateButton = createDialogButton("Update Selected Profile");
        updateButton.setEnabled(selected != null);
        content.addView(updateButton);

        Button selectButton = createDialogButton("Select Saved Profile");
        selectButton.setEnabled(!profiles.isEmpty());
        content.addView(selectButton);

        Button removeButton = createDialogButton("Remove Selected Profile");
        removeButton.setEnabled(selected != null);
        content.addView(removeButton);

        final AlertDialog dialog = new AlertDialog.Builder(getMapView()
                .getContext())
                .setTitle("Ditto Setup")
                .setMessage("Save Ditto credentials on this device for creating SARtak operation QR/join codes.")
                .setView(content)
                .setNegativeButton("Close", null)
                .create();

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showDittoProfileEditor(null);
            }
        });
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showDittoProfileEditor(mapController
                        .getSelectedDittoCredentialProfile());
            }
        });
        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showSelectDittoProfileDialog();
            }
        });
        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showRemoveDittoProfileDialog();
            }
        });

        dialog.show();
    }

    private void showDittoProfileEditor(
            final DittoCredentialProfile existing) {
        LinearLayout content = new LinearLayout(getMapView().getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), 0);

        addDialogLabel(content, "Connection type");
        final Spinner connectionTypeInput = new Spinner(getMapView()
                .getContext());
        final String[] connectionTypes = new String[] {
                DittoCredentialProfile.CONNECTION_SDK,
                DittoCredentialProfile.CONNECTION_HTTP
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getMapView()
                .getContext(), android.R.layout.simple_spinner_item,
                connectionTypes);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        connectionTypeInput.setAdapter(adapter);
        connectionTypeInput.setSelection(existing != null
                && existing.isHttpProfile() ? 1 : 0);
        content.addView(connectionTypeInput);

        final TextView modeHelp = new TextView(getMapView().getContext());
        modeHelp.setTextColor(Color.LTGRAY);
        modeHelp.setTextSize(12);
        content.addView(modeHelp);

        final EditText labelInput = addDialogInput(content, "Profile name",
                existing == null ? "" : existing.getRawLabel(), true);
        final EditText databaseInput = addDialogInput(content, "Database ID",
                existing == null ? "" : existing.getDatabaseId(), true);
        final EditText authUrlInput = addDialogInput(content,
                "Auth URL / HTTP URL",
                existing == null ? "https://" : existing.getAuthUrl(), true);
        final EditText tokenInput = addDialogInput(content,
                "Development token / HTTP token",
                existing == null ? "" : existing.getDevelopmentToken(), false);
        updateDittoProfileEditorHints(connectionTypeInput, modeHelp,
                authUrlInput, tokenInput);
        connectionTypeInput.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view, int position, long id) {
                        updateDittoProfileEditorHints(connectionTypeInput,
                                modeHelp, authUrlInput, tokenInput);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        new AlertDialog.Builder(getMapView().getContext())
                .setTitle(existing == null ? "Add Ditto Profile"
                        : "Update Ditto Profile")
                .setMessage("SDK profiles are used for current SARtak offline mesh sync. HTTP profiles can be saved for later server/API workflows.")
                .setView(content)
                .setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                boolean saved = mapController
                                        .saveDittoCredentialProfile(
                                                existing == null ? ""
                                                        : existing.getId(),
                                                labelInput.getText()
                                                        .toString(),
                                                connectionTypeInput
                                                        .getSelectedItem()
                                                        .toString(),
                                                databaseInput.getText()
                                                        .toString(),
                                                authUrlInput.getText()
                                                        .toString(),
                                                tokenInput.getText()
                                                        .toString());
                                Toast.makeText(getMapView().getContext(),
                                        saved ? "Ditto profile saved"
                                                : "Ditto profile incomplete",
                                        Toast.LENGTH_LONG).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSelectDittoProfileDialog() {
        final List<DittoCredentialProfile> profiles = mapController
                .getDittoCredentialProfiles();
        if (profiles.isEmpty()) {
            Toast.makeText(getMapView().getContext(),
                    "No Ditto profiles saved", Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] labels = new CharSequence[profiles.size()];
        for (int i = 0; i < profiles.size(); i++)
            labels[i] = profiles.get(i).getLabel();
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Select Ditto Profile")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mapController.selectDittoCredentialProfile(profiles
                                .get(which).getId());
                        Toast.makeText(getMapView().getContext(),
                                "Ditto profile selected", Toast.LENGTH_SHORT)
                                .show();
                        refreshGridUi();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRemoveDittoProfileDialog() {
        DittoCredentialProfile selected = mapController
                .getSelectedDittoCredentialProfile();
        if (selected == null) {
            Toast.makeText(getMapView().getContext(),
                    "No Ditto profile selected", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Remove Ditto Profile")
                .setMessage("Remove \"" + selected.getLabel()
                        + "\" from this device?")
                .setPositiveButton("Remove",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                boolean removed = mapController
                                        .removeSelectedDittoCredentialProfile();
                                Toast.makeText(getMapView().getContext(),
                                        removed ? "Ditto profile removed"
                                                : "No profile removed",
                                        Toast.LENGTH_LONG).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCreateOperationDialog() {
        if (!mapController.canManageOperation()) {
            Toast.makeText(getMapView().getContext(),
                    "Only HQ devices can create SARtak operations",
                    Toast.LENGTH_LONG).show();
            refreshGridUi();
            return;
        }
        if (!mapController.canCreateOperationFromLocalDittoConfig()) {
            Toast.makeText(getMapView().getContext(),
                    "Add a Ditto profile in Ditto Setup before creating an operation",
                    Toast.LENGTH_LONG).show();
            return;
        }
        ScrollView scroll = new ScrollView(getMapView().getContext());
        LinearLayout content = new LinearLayout(getMapView().getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), 0);
        scroll.addView(content);

        final EditText nameInput = addDialogInput(content, "Operation name",
                "SAR Operation", true);
        final EditText incidentInput = addDialogInput(content,
                "Incident / reference number", "", true);

        addDialogLabel(content, "Search type");
        final Spinner typeInput = new Spinner(getMapView().getContext());
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(getMapView()
                .getContext(), android.R.layout.simple_spinner_item,
                new String[] { "Missing person", "Evidence search",
                        "Welfare check", "Training", "Other" });
        typeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        typeInput.setAdapter(typeAdapter);
        content.addView(typeInput);

        addDialogLabel(content, "Priority");
        final Spinner priorityInput = new Spinner(getMapView().getContext());
        ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(getMapView()
                .getContext(), android.R.layout.simple_spinner_item,
                new String[] { "Normal", "High", "Emergency" });
        priorityAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        priorityInput.setAdapter(priorityAdapter);
        content.addView(priorityInput);

        final EditText stagingInput = addDialogInput(content,
                "Staging area / location", "", false);
        final EditText notesInput = addDialogInput(content,
                "Briefing notes / key info", "", false);
        final EditText endHoursInput = addDialogInput(content,
                "Planned duration in hours (optional)", "", true);

        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Create Operation")
                .setMessage("This creates the HQ-owned operation profile and scopes SARtak sync data to that operation.")
                .setView(scroll)
                .setPositiveButton("Create",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                String name = nameInput.getText().toString()
                                        .trim();
                                if (name.length() == 0)
                                    name = "SAR Operation";
                                long plannedEndAt = parsePlannedEndHours(
                                        endHoursInput);
                                boolean created = mapController
                                        .createOperation(name, incidentInput
                                                .getText().toString().trim(),
                                                selectedSpinnerText(typeInput),
                                                stagingInput.getText()
                                                        .toString().trim(),
                                                selectedSpinnerText(
                                                        priorityInput),
                                                notesInput.getText().toString()
                                                        .trim(),
                                                plannedEndAt);
                                Toast.makeText(getMapView().getContext(),
                                        created ? "Operation created"
                                                : "Operation requires HQ role and Ditto config",
                                        Toast.LENGTH_LONG).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOperationJoinCodeDialog() {
        final String joinCode = mapController.getOperationJoinCode();
        if (joinCode.length() == 0) {
            Toast.makeText(getMapView().getContext(),
                    "No operation join code available", Toast.LENGTH_LONG)
                    .show();
            return;
        }
        LinearLayout content = new LinearLayout(getMapView().getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), 0);
        try {
            Bitmap qrCode = OperationQrCodeGenerator.create(joinCode, dp(220));
            ImageView qrView = new ImageView(getMapView().getContext());
            qrView.setImageBitmap(qrCode);
            qrView.setAdjustViewBounds(true);
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(
                    dp(220), dp(220));
            qrParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            qrParams.setMargins(0, 0, 0, dp(8));
            content.addView(qrView, qrParams);
        } catch (Exception exception) {
            Log.w(TAG, "Failed to render operation QR code", exception);
        }
        final EditText codeView = new EditText(getMapView().getContext());
        codeView.setText(joinCode);
        codeView.setSelectAllOnFocus(true);
        content.addView(codeView);
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Operation Join Code")
                .setMessage("Share this QR/code with devices that should join this search operation.")
                .setView(content)
                .setPositiveButton("Copy",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                ClipboardManager clipboard =
                                        (ClipboardManager) getMapView()
                                                .getContext()
                                                .getSystemService(Context
                                                        .CLIPBOARD_SERVICE);
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(ClipData
                                            .newPlainText(
                                                    "SARtak operation code",
                                                    joinCode));
                                    Toast.makeText(getMapView().getContext(),
                                            "Join code copied",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showArchiveOperationDialog() {
        if (!mapController.canManageOperation()) {
            Toast.makeText(getMapView().getContext(),
                    "Only HQ devices can archive SARtak operations",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Archive Operation")
                .setMessage("Archive this operation? Existing grid, team, track, alert, and marker data stays stored for review, but the operation becomes read-only.")
                .setPositiveButton("Archive",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                boolean archived = mapController
                                        .archiveOperation();
                                Toast.makeText(getMapView().getContext(),
                                        archived ? "Operation archived"
                                                : "Operation was not archived",
                                        Toast.LENGTH_LONG).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showJoinOperationDialog() {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Join Operation")
                .setMessage("Scan the HQ operation QR code or paste the operation join code.")
                .setPositiveButton("Scan QR",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                startOperationQrScanner();
                            }
                        })
                .setNeutralButton("Paste Code",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                showPasteOperationJoinCodeDialog();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPasteOperationJoinCodeDialog() {
        final EditText input = new EditText(getMapView().getContext());
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setHint("Paste SARtak operation join code");
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Join Operation")
                .setMessage("Paste the join code from the operation organiser. Team setup will start after this device joins the operation.")
                .setView(input)
                .setPositiveButton("Join",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                boolean joined = mapController
                                        .joinOperationFromCode(input.getText()
                                                .toString().trim());
                                Toast.makeText(getMapView().getContext(),
                                        joined ? "Operation joined"
                                                : "Invalid operation code",
                                        Toast.LENGTH_LONG).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startOperationQrScanner() {
        Intent intent = new Intent(pluginContext,
                OperationQrScanActivity.class);
        intent.putExtra(OperationQrScanActivity.EXTRA_HOST_PACKAGE,
                getMapView().getContext().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            pluginContext.startActivity(intent);
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to open SARtak QR scanner", throwable);
            Toast.makeText(getMapView().getContext(),
                    "QR scanner unavailable. Paste the join code instead.",
                    Toast.LENGTH_LONG).show();
            showPasteOperationJoinCodeDialog();
        }
    }

    private void joinOperationFromScannedCode(String joinCode) {
        boolean joined = mapController.joinOperationFromCode(joinCode);
        Toast.makeText(getMapView().getContext(),
                joined ? "Operation joined from QR"
                        : "Invalid SARtak operation QR",
                Toast.LENGTH_LONG).show();
        if (joined)
            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);
        refreshGridUi();
    }

    private void consumePendingOperationQrScan() {
        if (consumingPendingQrScan)
            return;
        String joinCode = OperationQrScanResultStore.consume(pluginContext);
        if (joinCode.length() == 0)
            return;
        consumingPendingQrScan = true;
        try {
            joinOperationFromScannedCode(joinCode);
        } finally {
            consumingPendingQrScan = false;
        }
    }

    private void showLeaveOperationDialog() {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Leave Operation")
                .setMessage("Leave the active operation? This clears local SARtak team membership for this device, but does not delete ATAK data.")
                .setPositiveButton("Leave",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.leaveOperation();
                                handledTeamMessages.clear();
                                resolvedTeamMessages.clear();
                                handledAlertMessages.clear();
                                Toast.makeText(getMapView().getContext(),
                                        "Operation left", Toast.LENGTH_LONG)
                                        .show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRemoveTeamDialog() {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Remove Team")
                .setMessage("Remove " + mapController.getTeamName()
                        + "? This clears the local SARtak team setup and lets this leader create a new team.")
                .setPositiveButton("Remove",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.removeTeam();
                                Toast.makeText(getMapView().getContext(),
                                        "Team removed", Toast.LENGTH_SHORT)
                                        .show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showJoinTeamDialog() {
        final java.util.List<SearchTeamCotMessage> teams = mapController
                .getActiveTeamAdvertisements();
        if (teams.isEmpty()) {
            Toast.makeText(getMapView().getContext(),
                    "No active SARtak teams visible yet",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[teams.size()];
        for (int i = 0; i < teams.size(); i++)
            labels[i] = teams.get(i).getDisplayLabel();
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Join Team")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SearchTeamCotMessage team = teams.get(which);
                        mapController.requestJoinTeam(team);
                        Toast.makeText(getMapView().getContext(),
                                "Join request sent to "
                                        + team.getLeaderCallsign(),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showLeaveTeamDialog() {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Leave Team")
                .setMessage("Leave " + mapController.getTeamName()
                        + "? You can join another SARtak team afterwards.")
                .setPositiveButton("Leave",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.leaveTeam();
                                handledAlertMessages.clear();
                                Toast.makeText(getMapView().getContext(),
                                        "Left team", Toast.LENGTH_SHORT)
                                        .show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pollTeamCotMessages() {
        mapController.advertiseTeamIfDue();

        for (SearchTeamCotMessage removal
                : mapController.getMemberRemovalsForMe()) {
            if (handledTeamMessages.add(removal.getUid()))
                handleMemberRemoval(removal);
        }

        if (leaderView && mapController.isTeamCreated()) {
            for (SearchTeamCotMessage left
                    : mapController.getMemberLeavesForLeader()) {
                if (handledTeamMessages.add(left.getUid()))
                    handleMemberLeft(left);
            }
            for (SearchTeamCotMessage request
                    : mapController.getPendingJoinRequests()) {
                if (handledTeamMessages.add(request.getUid()))
                    showJoinRequestDialog(request);
            }
            for (SearchTeamCotMessage response
                    : mapController.getInviteResponsesForLeader()) {
                if (handledTeamMessages.add(response.getUid()))
                    handleInviteResponse(response);
            }
        }

        if (!leaderView && !hqView) {
            for (SearchTeamCotMessage invite : mapController.getInvitesForMe()) {
                if (handledTeamMessages.add(invite.getUid()))
                    showTeamInviteDialog(invite);
            }
            for (SearchTeamCotMessage response
                    : mapController.getJoinResponsesForMe()) {
                if (handledTeamMessages.add(response.getUid()))
                    handleJoinResponse(response);
            }
        }

        for (SearchAlertMessage alert : mapController
                .getUnacknowledgedAlertsForMe()) {
            if (handledAlertMessages.add(alert.getAlertId()))
                showTeamAlertDialog(alert);
        }
    }

    private void pollGridProgressPrompt() {
        if (!leaderView || !mapController.isTeamCreated())
            return;
        final String cellId = mapController.getPendingGridReviewCellId();
        if (cellId.length() == 0 || cellId.equals(activeGridReviewPromptCellId))
            return;
        activeGridReviewPromptCellId = cellId;
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Review previous cell")
                .setMessage("You have moved into a new search cell.\n\n"
                        + mapController.getPendingGridReviewCellSummary()
                        + "\n\nMark the previous cell complete, or leave it as partial?")
                .setPositiveButton("Complete",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.markGridCellComplete(cellId);
                                activeGridReviewPromptCellId = "";
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Partial",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.markGridCellPartial(cellId);
                                activeGridReviewPromptCellId = "";
                                refreshGridUi();
                            }
                        })
                .setNeutralButton("Later",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.deferGridCellReview(cellId);
                                activeGridReviewPromptCellId = "";
                            }
                        })
                .show();
    }

    private void updateMarkerVisibilityButtons() {
        TeamMarkerVisibilityMode mode = mapController
                .getTeamMarkerVisibilityMode();
        markerModeMeButton.setEnabled(mode != TeamMarkerVisibilityMode.ME_ONLY);
        markerModeTeamButton.setEnabled(mode
                != TeamMarkerVisibilityMode.MY_TEAM);
        markerModeLeadersButton.setVisibility(leaderView || hqView
                ? View.VISIBLE : View.GONE);
        markerModeAllButton.setVisibility(leaderView || hqView
                ? View.VISIBLE : View.GONE);
        markerModeLeadersButton.setEnabled(mode
                != TeamMarkerVisibilityMode.LEADERS);
        markerModeAllButton.setEnabled(mode
                != TeamMarkerVisibilityMode.ALL_VISIBLE);
    }

    private void showJoinRequestDialog(final SearchTeamCotMessage request) {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Join request")
                .setMessage(request.getSenderCallsign()
                        + " would like to join "
                        + request.getTeamName())
                .setPositiveButton("Accept",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.respondToJoinRequest(request,
                                        true);
                                resolvedTeamMessages.add(request.getUid());
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Decline",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.respondToJoinRequest(request,
                                        false);
                                resolvedTeamMessages.add(request.getUid());
                                refreshGridUi();
                            }
                        })
                .show();
    }

    private void showTeamInviteDialog(final SearchTeamCotMessage invite) {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Team invite")
                .setMessage(invite.getLeaderCallsign()
                        + " would like you to join "
                        + invite.getTeamName())
                .setPositiveButton("Accept",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.respondToInvite(invite, true);
                                resolvedTeamMessages.add(invite.getUid());
                                Toast.makeText(getMapView().getContext(),
                                        "Joined " + invite.getTeamName(),
                                        Toast.LENGTH_LONG).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Decline",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.respondToInvite(invite, false);
                                resolvedTeamMessages.add(invite.getUid());
                                refreshGridUi();
                            }
                        })
                .show();
    }

    private void handleJoinResponse(SearchTeamCotMessage response) {
        if (SearchTeamCotMessage.ACTION_JOIN_ACCEPT.equals(
                response.getAction())) {
            mapController.acceptJoinResponse(response);
            Toast.makeText(getMapView().getContext(),
                    "Joined " + response.getTeamName(), Toast.LENGTH_LONG)
                    .show();
            refreshGridUi();
        } else if (SearchTeamCotMessage.ACTION_JOIN_DECLINE.equals(
                response.getAction())) {
            Toast.makeText(getMapView().getContext(),
                    "Join request declined by "
                            + response.getLeaderCallsign(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handleInviteResponse(SearchTeamCotMessage response) {
        if (SearchTeamCotMessage.ACTION_INVITE_ACCEPT.equals(
                response.getAction())) {
            boolean added = mapController.acceptInviteResponse(response);
            resolvedTeamMessages.add(response.getUid());
            Toast.makeText(getMapView().getContext(),
                    added ? response.getSenderCallsign() + " joined the team"
                            : response.getSenderCallsign()
                                    + " accepted, but ATAK contact is not visible",
                    Toast.LENGTH_LONG).show();
            refreshGridUi();
        } else if (SearchTeamCotMessage.ACTION_INVITE_DECLINE.equals(
                response.getAction())) {
            resolvedTeamMessages.add(response.getUid());
            Toast.makeText(getMapView().getContext(),
                    response.getSenderCallsign() + " declined the invite",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handleMemberRemoval(SearchTeamCotMessage removal) {
        if (!mapController.applyMemberRemoval(removal))
            return;
        resolvedTeamMessages.clear();
        Toast.makeText(getMapView().getContext(),
                removal.getLeaderCallsign() + " removed you from "
                        + removal.getTeamName(),
                Toast.LENGTH_LONG).show();
        refreshGridUi();
    }

    private void handleMemberLeft(SearchTeamCotMessage left) {
        String callsign = mapController.applyMemberLeft(left);
        if (callsign.length() == 0)
            return;
        Toast.makeText(getMapView().getContext(),
                callsign + " has left the team",
                Toast.LENGTH_LONG).show();
        refreshGridUi();
    }

    private void showAddMemberDialog() {
        final java.util.List<AtakTeamContactDataSource.ContactSnapshot>
                contacts = mapController.getAvailableContacts();
        if (contacts.isEmpty()) {
            Toast.makeText(getMapView().getContext(),
                    "No connected ATAK devices visible yet",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++)
            labels[i] = addGroupBreakLabel(contacts, i);

        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Add connected member")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AtakTeamContactDataSource.ContactSnapshot contact =
                                contacts.get(which);
                        confirmAddMember(contact);
                    }
                })
                .show();
    }

    private String addGroupBreakLabel(
            java.util.List<AtakTeamContactDataSource.ContactSnapshot> contacts,
            int index) {
        AtakTeamContactDataSource.ContactSnapshot contact = contacts.get(index);
        String label = contact.getDisplayLabel();
        if (index == 0 || !contact.getAtakGroupName().equals(
                contacts.get(index - 1).getAtakGroupName()))
            return "== " + contact.getAtakGroupName() + " ==\n" + label;
        return label;
    }

    private void confirmAddMember(
            final AtakTeamContactDataSource.ContactSnapshot contact) {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Invite " + contact.getCallsign() + "?")
                .setMessage("ATAK UID:\n" + contact.getUid()
                        + "\n\nAn invite will be sent to this device. They "
                        + "must accept before SARtak adds them to the team.")
                .setPositiveButton("Send Invite",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.inviteTeamMember(
                                        contact.getUid());
                                Toast.makeText(getMapView().getContext(),
                                        "Invite sent to "
                                                + contact.getCallsign(),
                                        Toast.LENGTH_LONG).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void initialiseTeamInputs() {
        teamNameInput.setText(mapController.getTeamName());
        teamIdInput.setText(mapController.getTeamId());
    }

    private void setInputTextIfIdle(EditText input, String value) {
        if (input == null || input.hasFocus())
            return;
        String next = value == null ? "" : value;
        if (!next.contentEquals(input.getText()))
            input.setText(next);
    }

    private String getText(EditText input) {
        if (input == null || input.getText() == null)
            return "";
        return input.getText().toString().trim();
    }

    private void handlePauseResumeSearchLine() {
        if (!mapController.isSearchLineStarted()) {
            Toast.makeText(getMapView().getContext(),
                    "Start the search line first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!mapController.isSearchLinePaused()) {
            mapController.pauseSearchLine();
            Toast.makeText(getMapView().getContext(),
                    "Search line paused", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean resumed = mapController.resumeSearchLine();
        if (resumed) {
            Toast.makeText(getMapView().getContext(),
                    "Search line resumed", Toast.LENGTH_SHORT).show();
        } else {
            showForceResumeDialog();
        }
    }

    private void showForceResumeDialog() {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Restart search line?")
                .setMessage(mapController.getSearchLineRestartPrompt())
                .setPositiveButton("Force resume",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.forceResumeSearchLine();
                                Toast.makeText(getMapView().getContext(),
                                        "Search line force resumed",
                                        Toast.LENGTH_SHORT).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Wait", null)
                .show();
    }

    private void setTeamMarkerMode(TeamMarkerVisibilityMode mode) {
        mapController.setTeamMarkerVisibilityMode(mode);
        Toast.makeText(getMapView().getContext(),
                "Showing " + mode.getLabel() + " markers",
                Toast.LENGTH_SHORT).show();
    }

    private void renderTeamMemberCards() {
        teamMemberCardsContainer.removeAllViews();
        for (SearchTeamMember member : mapController.getTeamMembers()) {
            teamMemberCardsContainer.addView(createTeamMemberCard(member));
        }
    }

    private void renderDeviceCards() {
        devicesCardsContainer.removeAllViews();
        java.util.List<DeviceConnectivitySnapshot> devices =
                mapController.getVisibleDevices();
        int atakCount = 0;
        int dittoCount = 0;
        int staleCount = 0;
        for (DeviceConnectivitySnapshot device : devices) {
            String connection = device.getConnectionSummary();
            if (connection.contains("ATAK"))
                atakCount++;
            if (connection.contains("Ditto"))
                dittoCount++;
            if (connection.toLowerCase(java.util.Locale.US).contains("stale"))
                staleCount++;
            devicesCardsContainer.addView(createDeviceCard(device));
        }
        devicesSummaryValue.setText(mapController.getDeviceDiagnosticsSummary(
                devices.size(), atakCount, dittoCount, staleCount));
        if (devices.isEmpty())
            devicesCardsContainer.addView(createCardText(
                    "No ATAK contacts or Ditto peers visible yet", 13,
                    false));
    }

    private void renderAlertCards() {
        alertsCardsContainer.removeAllViews();
        java.util.List<SearchAlertMessage> alerts = mapController
                .getActiveTeamAlerts();
        if (alerts.isEmpty()) {
            alertsCardsContainer.addView(createCardText(
                    "No active team alerts", 13, false));
            return;
        }

        for (SearchAlertMessage alert : alerts)
            alertsCardsContainer.addView(createAlertCard(alert));
    }

    private View createAlertCard(final SearchAlertMessage alert) {
        LinearLayout card = createActionCard();
        boolean fromMe = alert.getSenderUid().equals(mapController
                .getSelfMemberId());
        card.addView(createCardText(alert.getTitle(), 15, true));
        card.addView(createCardText("From: " + alert.getSenderCallsign(),
                13, false));
        card.addView(createCardText(alert.getMessage(), 13, false));
        if (alert.requiresHalt())
            card.addView(createCardText("Search line paused while active",
                    12, false));
        if (!Double.isNaN(alert.getLatitude())
                && !Double.isNaN(alert.getLongitude()))
            card.addView(createCardText(String.format(
                    java.util.Locale.US, "Sender location: %.6f, %.6f",
                    alert.getLatitude(), alert.getLongitude()), 12, false));

        if (fromMe) {
            card.addView(createCardText(mapController.getAlertAckSummary(alert),
                    12, false));
            card.addView(createCancelButton("Clear alert / resume",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mapController.cancelTeamAlert(alert);
                            refreshGridUi();
                        }
                    }));
        } else if (mapController.hasAcknowledgedAlert(alert)) {
            card.addView(createCardText("Acknowledged by this device", 12,
                    false));
        } else {
            card.addView(createCancelButton("Acknowledge",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            acknowledgeAlert(alert);
                        }
                    }));
        }
        return card;
    }

    private void updateAlertButtons(boolean teamCreated) {
        updateAlertButton(alertHoldPositionButton,
                SearchAlertMessage.TYPE_HOLD_POSITION, "Hold Position",
                teamCreated);
        updateAlertButton(alertRequestLeaderButton,
                SearchAlertMessage.TYPE_REQUEST_LEADER,
                "Request Team Leader", teamCreated);
        updateAlertButton(alertEmergencyStopButton,
                SearchAlertMessage.TYPE_EMERGENCY_STOP, "Emergency Stop",
                teamCreated);
    }

    private void updateAlertButton(Button button, String alertType,
            String label, boolean enabled) {
        if (button == null)
            return;
        button.setEnabled(enabled);
        button.setText(mapController.hasActiveAlertType(alertType)
                ? "Clear " + label : label);
    }

    private void handleAlertButton(String alertType) {
        SearchAlertMessage active = mapController.getOutgoingAlertOfType(
                alertType);
        if (active != null) {
            boolean cleared = mapController.cancelTeamAlert(active);
            Toast.makeText(getMapView().getContext(),
                    cleared ? "Alert cleared" : "Unable to clear alert",
                    Toast.LENGTH_LONG).show();
            return;
        }
        boolean sent = mapController.sendTeamAlert(alertType);
        Toast.makeText(getMapView().getContext(),
                sent ? SearchAlertMessage.titleForType(alertType) + " sent"
                        : "Join or create a team with Ditto active before sending alerts",
                Toast.LENGTH_LONG).show();
    }

    private void showTeamAlertDialog(final SearchAlertMessage alert) {
        String message = alert.getMessage();
        if (!Double.isNaN(alert.getLatitude())
                && !Double.isNaN(alert.getLongitude()))
            message += String.format(java.util.Locale.US,
                    "\n\nSender location:\n%.6f, %.6f",
                    alert.getLatitude(), alert.getLongitude());
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle(alert.getTitle())
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Acknowledge",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                acknowledgeAlert(alert);
                            }
                        })
                .show();
    }

    private void acknowledgeAlert(SearchAlertMessage alert) {
        boolean acknowledged = mapController.acknowledgeAlert(alert);
        Toast.makeText(getMapView().getContext(),
                acknowledged ? "Alert acknowledged"
                        : "Unable to acknowledge alert",
                Toast.LENGTH_LONG).show();
        refreshGridUi();
    }

    private void renderInvitesAndRequests() {
        invitesRequestsContainer.removeAllViews();
        int count = 0;
        if (leaderView && mapController.isTeamCreated()) {
            java.util.List<SearchTeamCotMessage> responses = mapController
                    .getInviteResponsesForLeader();
            for (SearchTeamCotMessage request
                    : mapController.getPendingJoinRequests()) {
                if (resolvedTeamMessages.contains(request.getUid()))
                    continue;
                invitesRequestsContainer.addView(createJoinRequestCard(request));
                count++;
            }
            for (SearchTeamCotMessage invite : mapController
                    .getOutgoingInvites()) {
                if (resolvedTeamMessages.contains(invite.getUid())
                        || hasInviteResponse(invite, responses))
                    continue;
                invitesRequestsContainer.addView(createOutgoingInviteCard(invite));
                count++;
            }
        } else if (!leaderView && !hqView) {
            java.util.List<SearchTeamCotMessage> joinResponses = mapController
                    .getJoinResponsesForMe();
            for (SearchTeamCotMessage invite : mapController.getInvitesForMe()) {
                if (resolvedTeamMessages.contains(invite.getUid()))
                    continue;
                invitesRequestsContainer.addView(createIncomingInviteCard(invite));
                count++;
            }
            for (SearchTeamCotMessage request
                    : mapController.getOutgoingJoinRequests()) {
                if (resolvedTeamMessages.contains(request.getUid())
                        || hasJoinResponse(request, joinResponses))
                    continue;
                invitesRequestsContainer.addView(
                        createOutgoingJoinRequestCard(request));
                count++;
            }
        }

        if (count == 0)
            invitesRequestsContainer.addView(createCardText(
                    "No pending invites or requests", 13, false));
    }

    private View createJoinRequestCard(final SearchTeamCotMessage request) {
        LinearLayout card = createActionCard();
        card.addView(createCardText(request.getSenderCallsign()
                + " wants to join " + request.getTeamName(), 14, true));
        card.addView(createCardText("UID: " + request.getSenderUid(),
                12, false));
        card.addView(createDecisionButtons("Accept", "Decline",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mapController.respondToJoinRequest(request, true);
                        resolvedTeamMessages.add(request.getUid());
                        refreshGridUi();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mapController.respondToJoinRequest(request, false);
                        resolvedTeamMessages.add(request.getUid());
                        refreshGridUi();
                    }
                }));
        return card;
    }

    private View createIncomingInviteCard(final SearchTeamCotMessage invite) {
        LinearLayout card = createActionCard();
        card.addView(createCardText(invite.getLeaderCallsign()
                + " invited you to " + invite.getTeamName(), 14, true));
        card.addView(createCardText("Team ID: " + invite.getTeamId(),
                12, false));
        card.addView(createDecisionButtons("Accept", "Decline",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mapController.respondToInvite(invite, true);
                        resolvedTeamMessages.add(invite.getUid());
                        refreshGridUi();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mapController.respondToInvite(invite, false);
                        resolvedTeamMessages.add(invite.getUid());
                        refreshGridUi();
                    }
                }));
        return card;
    }

    private View createOutgoingInviteCard(final SearchTeamCotMessage invite) {
        LinearLayout card = createActionCard();
        String target = invite.getTargetCallsign().length() > 0
                ? invite.getTargetCallsign() : invite.getTargetUid();
        card.addView(createCardText("Invite sent to " + target, 14, true));
        card.addView(createCardText("Waiting for accept / decline", 12, false));
        card.addView(createCancelButton("Cancel invite",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mapController.cancelInvite(invite);
                        resolvedTeamMessages.add(invite.getUid());
                        Toast.makeText(getMapView().getContext(),
                                "Invite cancelled", Toast.LENGTH_SHORT).show();
                        refreshGridUi();
                    }
                }));
        return card;
    }

    private View createOutgoingJoinRequestCard(
            final SearchTeamCotMessage request) {
        LinearLayout card = createActionCard();
        card.addView(createCardText("Request sent to "
                + request.getLeaderCallsign(), 14, true));
        card.addView(createCardText("Waiting to join "
                + request.getTeamName(), 12, false));
        card.addView(createCancelButton("Cancel request",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mapController.cancelJoinRequest(request);
                        resolvedTeamMessages.add(request.getUid());
                        Toast.makeText(getMapView().getContext(),
                                "Join request cancelled", Toast.LENGTH_SHORT)
                                .show();
                        refreshGridUi();
                    }
                }));
        return card;
    }

    private Button createCancelButton(String label,
            View.OnClickListener listener) {
        Button button = new Button(pluginContext);
        button.setText(label);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout createActionCard() {
        LinearLayout card = new LinearLayout(pluginContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(43, 48, 52));
        background.setStroke(dp(1), Color.rgb(216, 182, 76));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout createDecisionButtons(String positiveLabel,
            String negativeLabel, View.OnClickListener positive,
            View.OnClickListener negative) {
        LinearLayout row = new LinearLayout(pluginContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);

        Button positiveButton = new Button(pluginContext);
        positiveButton.setText(positiveLabel);
        positiveButton.setOnClickListener(positive);
        row.addView(positiveButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button negativeButton = new Button(pluginContext);
        negativeButton.setText(negativeLabel);
        negativeButton.setOnClickListener(negative);
        LinearLayout.LayoutParams negativeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        negativeParams.setMargins(dp(6), 0, 0, 0);
        row.addView(negativeButton, negativeParams);
        return row;
    }

    private void renderHqOperationCards() {
        if (!hqView)
            return;
        if (hqTeamsContainer == null || hqAssignmentsContainer == null)
            return;
        hqTeamsContainer.removeAllViews();
        hqAssignmentsContainer.removeAllViews();
        java.util.List<SearchTeamCotMessage> teams = mapController
                .getActiveTeamAdvertisements();
        if (teams.isEmpty()) {
            hqTeamsContainer.addView(createCardText(
                    "No SARtak teams visible yet", 14, false));
        } else {
            for (SearchTeamCotMessage team : teams) {
                hqTeamsContainer.addView(createCardText(team.getTeamName()
                        + "\nLeader: " + team.getLeaderCallsign()
                        + "\nTeam ID: " + team.getTeamId(), 14, false));
            }
        }

        java.util.List<SearchAreaAssignment> assignments = mapController
                .getAreaAssignments();
        if (assignments.isEmpty()) {
            hqAssignmentsContainer.addView(createCardText(
                    "No search areas assigned yet", 14, false));
        } else {
            for (SearchAreaAssignment assignment : assignments) {
                hqAssignmentsContainer.addView(createCardText(assignment
                        .getDisplaySummary(), 14, false));
            }
        }
    }

    private boolean hasInviteResponse(SearchTeamCotMessage invite,
            java.util.List<SearchTeamCotMessage> responses) {
        for (SearchTeamCotMessage response : responses) {
            if (response.getCreated() >= invite.getCreated()
                    && (invite.getTargetUid().equals(response.getSenderUid())
                            || invite.getTargetCallsign().equalsIgnoreCase(
                                    response.getSenderCallsign())))
                return true;
        }
        return false;
    }

    private boolean hasJoinResponse(SearchTeamCotMessage request,
            java.util.List<SearchTeamCotMessage> responses) {
        for (SearchTeamCotMessage response : responses) {
            if (response.getCreated() >= request.getCreated()
                    && request.getTeamId().equals(response.getTeamId()))
                return true;
        }
        return false;
    }

    private View createTeamMemberCard(SearchTeamMember member) {
        LinearLayout card = new LinearLayout(pluginContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SearchTeamMember selected = mapController.selectTeamMember(
                        member.getUniqueId());
                if (selected != null) {
                    Toast.makeText(getMapView().getContext(),
                            "Selected " + selected.getCallsign(),
                            Toast.LENGTH_SHORT).show();
                    refreshGridUi();
                }
            }
        });

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(43, 48, 52));
        int strokeColor = member.getUniqueId().equals(
                mapController.getSelectedTeamMemberId())
                        ? Color.rgb(216, 182, 76)
                        : member.getDisplayColor();
        background.setStroke(dp(member.getUniqueId().equals(
                mapController.getSelectedTeamMemberId()) ? 3 : 1),
                strokeColor);
        card.setBackground(background);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(cardParams);

        LinearLayout header = new LinearLayout(pluginContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView swatch = new TextView(pluginContext);
        GradientDrawable swatchBackground = new GradientDrawable();
        swatchBackground.setColor(member.getDisplayColor());
        swatchBackground.setStroke(dp(3), member.getTeamColorArgb());
        swatch.setBackground(swatchBackground);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                dp(18), dp(28));
        swatchParams.setMargins(0, 0, dp(8), 0);
        header.addView(swatch, swatchParams);

        TextView name = createCardText(member.getCallsign() + " - "
                + member.getRoleLabel(), 15, true);
        header.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView status = createCardText(member.getConnectionStatus().name(),
                12, true);
        status.setTextColor(getConnectionColor(member));
        header.addView(status);
        if (leaderView && !member.getUniqueId().equals(
                mapController.getSelfMemberId())) {
            Button removeButton = new Button(pluginContext);
            removeButton.setText("X");
            removeButton.setTextColor(Color.rgb(242, 245, 247));
            removeButton.setTextSize(12);
            removeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showRemoveMemberDialog(member);
                }
            });
            header.addView(removeButton, new LinearLayout.LayoutParams(
                    dp(42), dp(36)));
        }
        card.addView(header);

        card.addView(createCardText("ID: " + member.getUniqueId()
                + " | SARtak team colour: " + member.getTeamColorName()
                + " | Personal: " + member.getColorName()
                + " | " + member.getLaneLabel(), 13, false));
        card.addView(createCardText("Native ATAK team: "
                + member.getAtakGroupName(), 13, false));
        card.addView(createCardText("GPS: " + member.getGpsCoordinates()
                + " | Alt: " + member.getAltitude(), 13, false));
        card.addView(createCardText("Grid: "
                + member.getCurrentGridCellDisplay()
                + " | Last ping: " + member.getLastPing(), 13, false));
        card.addView(createCardText(member.hasReliableHeading()
                ? "Heading: " + Math.round(member.getHeadingDegrees())
                        + " deg | Speed: "
                        + String.format(java.util.Locale.US, "%.1f m/s",
                                member.getSpeedMetersPerSecond())
                : "Heading unavailable - neutral map marker", 13, false));
        card.addView(createCardText("From you: "
                + member.getDistanceFromYou() + " | From line: "
                + mapController.getMemberSearchLineSummary(
                        member.getUniqueId()), 13, false));
        card.addView(createCardText("Membership: "
                + member.getMembershipStatus().name(), 12, false));
        return card;
    }

    private View createDeviceCard(DeviceConnectivitySnapshot device) {
        LinearLayout card = createActionCard();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(43, 48, 52));
        background.setStroke(dp(device.isSelf() ? 2 : 1),
                device.isSelf() ? Color.rgb(216, 182, 76)
                        : deviceConnectionColor(device));
        card.setBackground(background);

        card.addView(createCardText(device.getCallsign(), 15, true));
        card.addView(createCardText("Connected by: "
                + device.getConnectionSummary(), 13, false));
        card.addView(createCardText(device.getLastUpdateSummary(),
                13, false));
        card.addView(createCardText("SARtak team: "
                + device.getTeamSummary(), 13, false));
        card.addView(createCardText("Native ATAK team: "
                + device.getAtakGroupName(), 13, false));
        card.addView(createCardText("Role: " + device.getRole(),
                13, false));
        card.addView(createCardText("UID: " + device.getUid(),
                12, false));
        return card;
    }

    private int deviceConnectionColor(DeviceConnectivitySnapshot device) {
        String connection = device.getConnectionSummary();
        if (connection.contains("stale"))
            return Color.rgb(216, 182, 76);
        if (connection.contains("ATAK") && connection.contains("Ditto"))
            return Color.rgb(66, 195, 106);
        if (connection.contains("Ditto"))
            return Color.rgb(74, 163, 255);
        if (connection.contains("ATAK"))
            return Color.rgb(180, 124, 255);
        return Color.rgb(138, 143, 152);
    }

    private void showResetSyncStateDialog() {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Reset local SARtak sync state?")
                .setMessage("This clears this device's local SARtak team, "
                        + "pending messages, and visible sync caches. It does "
                        + "not delete another device's data.")
                .setPositiveButton("Reset",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                handledTeamMessages.clear();
                                resolvedTeamMessages.clear();
                                handledAlertMessages.clear();
                                mapController.resetLocalSyncState();
                                Toast.makeText(getMapView().getContext(),
                                        "Local SARtak sync state reset",
                                        Toast.LENGTH_SHORT).show();
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRemoveMemberDialog(final SearchTeamMember member) {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Remove " + member.getCallsign() + "?")
                .setMessage("Are you sure you want to remove "
                        + member.getCallsign() + " from this SARtak team?")
                .setPositiveButton("Confirm",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mapController.removeTeamMemberFromSetup(
                                        member.getUniqueId());
                                refreshGridUi();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView createCardText(String text, int sizeSp, boolean bold) {
        TextView textView = new TextView(pluginContext);
        textView.setText(text);
        textView.setTextColor(Color.rgb(242, 245, 247));
        textView.setTextSize(sizeSp);
        if (bold)
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        return textView;
    }

    private Button createDialogButton(String label) {
        Button button = new Button(getMapView().getContext());
        button.setText(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private EditText addDialogInput(LinearLayout content, String label,
            String value, boolean singleLine) {
        addDialogLabel(content, label);

        EditText input = new EditText(getMapView().getContext());
        input.setText(value);
        input.setSingleLine(singleLine);
        if (!singleLine)
            input.setMinLines(2);
        content.addView(input);
        return input;
    }

    private double parseKm(EditText input, double fallback) {
        if (input == null)
            return fallback;
        try {
            double value = Double.parseDouble(input.getText().toString()
                    .trim());
            return Math.max(0.1, value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parsePlannedEndHours(EditText input) {
        if (input == null)
            return 0L;
        String value = input.getText().toString().trim();
        if (value.length() == 0)
            return 0L;
        try {
            double hours = Double.parseDouble(value);
            if (hours <= 0)
                return 0L;
            return System.currentTimeMillis()
                    + (long) (hours * 60.0 * 60.0 * 1000.0);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String selectedSpinnerText(Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null)
            return "";
        return spinner.getSelectedItem().toString();
    }

    private void addDialogLabel(LinearLayout content, String label) {
        TextView labelView = new TextView(getMapView().getContext());
        labelView.setText(label);
        labelView.setTextColor(Color.LTGRAY);
        labelView.setTextSize(12);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(8), 0, 0);
        content.addView(labelView, labelParams);
    }

    private void updateDittoProfileEditorHints(Spinner connectionTypeInput,
            TextView modeHelp, EditText authUrlInput, EditText tokenInput) {
        boolean http = DittoCredentialProfile.CONNECTION_HTTP.equals(
                connectionTypeInput.getSelectedItem().toString());
        if (http) {
            modeHelp.setText("HTTP profiles are saved for future server/API workflows and are not used by the current offline mesh runtime.");
            authUrlInput.setHint("HTTP API URL");
            tokenInput.setHint("HTTP access token");
        } else {
            modeHelp.setText("SDK profiles are used by SARtak's current Ditto offline mesh sync.");
            authUrlInput.setHint("Ditto auth URL");
            tokenInput.setHint("Development / playground token");
        }
    }

    private void renderGridReviewCards() {
        gridReviewContainer.removeAllViews();
        List<SearchGridCell> cells = mapController.getGridReviewCells();
        if (cells.isEmpty()) {
            gridReviewContainer.addView(createCardText(
                    "Partial and completed cells will appear here.", 13,
                    false));
            return;
        }
        for (final SearchGridCell cell : cells)
            gridReviewContainer.addView(createGridReviewCard(cell));
    }

    private View createGridReviewCard(final SearchGridCell cell) {
        LinearLayout card = createActionCard();
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mapController.focusGridCell(cell.getId());
                Toast.makeText(getMapView().getContext(),
                        "Focused " + cell.getId(), Toast.LENGTH_SHORT).show();
                refreshGridUi();
            }
        });
        card.addView(createCardText(
                com.atakmap.android.plugintemplate.grid.SearchGridDisplayFormatter
                        .formatCellCompact(cell), 14, true));
        card.addView(createCardText("Status: "
                + com.atakmap.android.plugintemplate.grid.SearchGridDisplayFormatter
                        .formatStatus(cell.getStatus()), 13, false));
        LinearLayout row = new LinearLayout(pluginContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        Button partialButton = new Button(pluginContext);
        partialButton.setText("Partial");
        partialButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mapController.markGridCellPartial(cell.getId());
                refreshGridUi();
            }
        });
        row.addView(partialButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button completeButton = new Button(pluginContext);
        completeButton.setText("Complete");
        completeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mapController.markGridCellComplete(cell.getId());
                refreshGridUi();
            }
        });
        LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        completeParams.setMargins(dp(6), 0, 0, 0);
        row.addView(completeButton, completeParams);

        Button clearButton = new Button(pluginContext);
        clearButton.setText("Clear");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mapController.clearGridCellStatus(cell.getId());
                refreshGridUi();
            }
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        clearParams.setMargins(dp(6), 0, 0, 0);
        row.addView(clearButton, clearParams);
        card.addView(row);
        return card;
    }

    private int readinessColor(int readinessLevel) {
        if (readinessLevel == SARTakMapController.READINESS_READY)
            return Color.rgb(66, 195, 106);
        if (readinessLevel == SARTakMapController.READINESS_WAITING)
            return Color.rgb(216, 182, 76);
        return Color.rgb(216, 84, 76);
    }

    private SpannableString colorReadinessSummary(String summary) {
        String value = summary == null ? "" : summary;
        SpannableString colored = new SpannableString(value);
        int start = 0;
        while (start < value.length()) {
            int end = value.indexOf('\n', start);
            if (end < 0)
                end = value.length();
            String line = value.substring(start, end);
            int color = Color.LTGRAY;
            if (line.startsWith("[OK]"))
                color = readinessColor(SARTakMapController.READINESS_READY);
            else if (line.startsWith("[WAIT]"))
                color = readinessColor(SARTakMapController.READINESS_WAITING);
            else if (line.startsWith("[NO]"))
                color = readinessColor(SARTakMapController.READINESS_BLOCKED);
            colored.setSpan(new ForegroundColorSpan(color), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = end + 1;
        }
        return colored;
    }

    private int getConnectionColor(SearchTeamMember member) {
        if (member.getConnectionStatus()
                == SearchTeamMember.ConnectionStatus.CONNECTED)
            return Color.rgb(66, 195, 106);
        if (member.getConnectionStatus()
                == SearchTeamMember.ConnectionStatus.RECONNECTING)
            return Color.rgb(216, 182, 76);
        return Color.rgb(216, 84, 76);
    }

    private int dp(int value) {
        float density = pluginContext.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(OperationQrScanActivity.ACTION_SCAN_RESULT)) {
            String joinCode = intent.getStringExtra(
                    OperationQrScanActivity.EXTRA_JOIN_CODE);
            if (joinCode != null) {
                OperationQrScanResultStore.consume(pluginContext);
                joinOperationFromScannedCode(joinCode);
            } else {
                consumePendingOperationQrScan();
            }
            return;
        }

        if (action.equals(SHOW_PLUGIN)) {

            Log.d(TAG, "showing plugin drop down");
            try {
                refreshGridUi();
                showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                        HALF_HEIGHT, false, this);
                startUiRefresh();
                String joinCode = intent.getStringExtra(
                        OperationQrScanActivity.EXTRA_JOIN_CODE);
                if (joinCode != null)
                    joinOperationFromScannedCode(joinCode);
                else
                    consumePendingOperationQrScan();
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to open SARtak drop down", throwable);
                Toast.makeText(getMapView().getContext(),
                        "SARtak could not open: "
                                + throwable.getClass().getSimpleName(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v)
            startUiRefresh();
        else
            stopUiRefresh();
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        stopUiRefresh();
    }

    private void startUiRefresh() {
        uiRefreshHandler.removeCallbacks(uiRefreshRunnable);
        uiRefreshHandler.post(uiRefreshRunnable);
    }

    private void stopUiRefresh() {
        uiRefreshHandler.removeCallbacks(uiRefreshRunnable);
    }

}


