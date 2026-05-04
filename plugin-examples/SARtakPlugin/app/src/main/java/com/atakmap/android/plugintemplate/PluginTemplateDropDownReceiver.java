
package com.atakmap.android.plugintemplate;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchTeamMember;
import com.atakmap.android.plugintemplate.grid.TeamMarkerVisibilityMode;
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
    private final Button markerModeMeButton;
    private final Button markerModeTeamButton;
    private final Button markerModeLeadersButton;
    private final Button markerModeAllButton;
    private final Button trackRecordButton;
    private final Button trackVisibilityButton;
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
    private final TextView teamNameValue;
    private final LinearLayout teamMemberCardsContainer;
    private final TextView teamMarkerVisibilityValue;
    private final TextView teamAlertsValue;
    private final TextView homeAlertsValue;
    private final TextView currentRoleValue;
    private final TextView trackStatusValue;
    private final TextView trackDetailsValue;
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
        markerModeMeButton = templateView
                .findViewById(R.id.marker_mode_me_button);
        markerModeTeamButton = templateView
                .findViewById(R.id.marker_mode_team_button);
        markerModeLeadersButton = templateView
                .findViewById(R.id.marker_mode_leaders_button);
        markerModeAllButton = templateView
                .findViewById(R.id.marker_mode_all_button);
        trackRecordButton = templateView
                .findViewById(R.id.track_record_button);
        trackVisibilityButton = templateView
                .findViewById(R.id.track_visibility_button);
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
        teamNameValue = templateView.findViewById(R.id.team_name_value);
        teamMemberCardsContainer = templateView
                .findViewById(R.id.team_member_cards_container);
        teamMarkerVisibilityValue = templateView
                .findViewById(R.id.team_marker_visibility_value);
        teamAlertsValue = templateView.findViewById(R.id.team_alerts_value);
        homeAlertsValue = templateView.findViewById(R.id.home_alerts_value);
        currentRoleValue = templateView.findViewById(R.id.current_role_value);
        trackStatusValue = templateView.findViewById(R.id.track_status_value);
        trackDetailsValue = templateView.findViewById(R.id.track_details_value);

        homeTabButton.setOnClickListener(this);
        gridTabButton.setOnClickListener(this);
        teamTabButton.setOnClickListener(this);
        trackTabButton.setOnClickListener(this);
        roleToggleButton.setOnClickListener(this);
        toggleSearchAreaButton.setOnClickListener(this);
        markerModeMeButton.setOnClickListener(this);
        markerModeTeamButton.setOnClickListener(this);
        markerModeLeadersButton.setOnClickListener(this);
        markerModeAllButton.setOnClickListener(this);
        trackRecordButton.setOnClickListener(this);
        trackVisibilityButton.setOnClickListener(this);
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
        } else if (id == R.id.marker_mode_me_button) {
            setTeamMarkerMode(TeamMarkerVisibilityMode.ME_ONLY);
        } else if (id == R.id.marker_mode_team_button) {
            setTeamMarkerMode(TeamMarkerVisibilityMode.MY_TEAM);
        } else if (id == R.id.marker_mode_leaders_button) {
            setTeamMarkerMode(TeamMarkerVisibilityMode.LEADERS);
        } else if (id == R.id.marker_mode_all_button) {
            setTeamMarkerMode(TeamMarkerVisibilityMode.ALL_VISIBLE);
        } else if (id == R.id.track_record_button) {
            boolean recording = mapController.toggleTrackRecording();
            Toast.makeText(getMapView().getContext(), recording
                    ? "Track recording resumed"
                    : "Track recording paused", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.track_visibility_button) {
            boolean visible = mapController.toggleTrackVisibility();
            Toast.makeText(getMapView().getContext(), visible
                    ? "Track shown on map"
                    : "Track hidden from map", Toast.LENGTH_SHORT).show();
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
        teamNameValue.setText(mapController.getTeamName() + " | "
                + mapController.getTeamId());
        teamMarkerVisibilityValue.setText("Showing: "
                + mapController.getTeamMarkerVisibilityLabel());
        renderTeamMemberCards();
        teamAlertsValue.setText(alertSummary);
        homeAlertsValue.setText(alertSummary);
        trackStatusValue.setText(mapController.getTrackStatusSummary());
        trackDetailsValue.setText(mapController.getTrackDetailsSummary());
        trackRecordButton.setText(mapController.isTrackRecording()
                ? R.string.track_pause
                : R.string.track_resume);
        trackVisibilityButton.setText(mapController.isTrackVisible()
                ? R.string.track_hide
                : R.string.track_show);
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
        swatch.setBackground(swatchBackground);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                dp(8), dp(28));
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
        card.addView(header);

        card.addView(createCardText("ID: " + member.getUniqueId()
                + " | Colour: " + member.getColorName()
                + " | " + member.getLaneLabel(), 13, false));
        card.addView(createCardText("GPS: " + member.getGpsCoordinates()
                + " | Alt: " + member.getAltitude(), 13, false));
        card.addView(createCardText("Grid: " + member.getCurrentGridCell()
                + " | Last ping: " + member.getLastPing(), 13, false));
        card.addView(createCardText("From you: "
                + member.getDistanceFromYou() + " | From line: "
                + member.getDistanceFromSearchLine(), 13, false));
        card.addView(createCardText("Membership: "
                + member.getMembershipStatus().name(), 12, false));
        return card;
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
