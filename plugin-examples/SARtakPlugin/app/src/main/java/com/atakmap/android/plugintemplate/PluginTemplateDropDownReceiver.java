
package com.atakmap.android.plugintemplate;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;
import com.atakmap.android.plugintemplate.grid.SearchTeamMember;
import com.atakmap.android.plugintemplate.grid.TeamMarkerVisibilityMode;
import com.atakmap.android.plugintemplate.runtime.AtakTeamContactDataSource;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotMessage;
import com.atakmap.android.plugintemplate.plugin.R;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.coremap.log.Log;

import java.util.HashSet;
import java.util.Set;

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
    private final Button toggleSearchAreaButton;
    private final Button markerModeMeButton;
    private final Button markerModeTeamButton;
    private final Button markerModeLeadersButton;
    private final Button markerModeAllButton;
    private final Button trackRecordButton;
    private final Button trackVisibilityButton;
    private final Button trackClearButton;
    private final Button startSearchLineButton;
    private final Button pauseSearchLineButton;
    private final Button toggleCallsignsButton;
    private final Button searchLineColourButton;
    private final Button saveTeamSetupButton;
    private final Button removeTeamButton;
    private final Button refreshAtakContactsButton;
    private final EditText searchLineToleranceInput;
    private final EditText teamNameInput;
    private final EditText teamIdInput;
    private final EditText memberUidInput;
    private final EditText memberCallsignInput;
    private final View homeTabContent;
    private final View gridTabContent;
    private final View teamTabContent;
    private final View trackTabContent;
    private final View leaderTeamControls;
    private final View otherTeamsSection;
    private final View gridStatusControls;
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
    private final TextView identityValue;
    private final TextView homeGpsValue;
    private final TextView gridProgressValue;
    private final TextView teamSizeValue;
    private final TextView teamNameValue;
    private final LinearLayout teamMemberCardsContainer;
    private final LinearLayout invitesRequestsContainer;
    private final TextView teamMarkerVisibilityValue;
    private final TextView atakContactStatusValue;
    private final TextView teamAlertsValue;
    private final TextView homeAlertsValue;
    private final TextView currentRoleValue;
    private final TextView trackStatusValue;
    private final TextView trackDetailsValue;
    private final Handler uiRefreshHandler = new Handler(Looper.getMainLooper());
    private final Set<String> handledTeamMessages = new HashSet<>();
    private final Set<String> resolvedTeamMessages = new HashSet<>();
    private final Runnable uiRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshGridUi();
            uiRefreshHandler.postDelayed(this, 2000L);
        }
    };
    private boolean leaderView = true;
    private boolean suppressToleranceUpdate;

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
        trackClearButton = templateView.findViewById(R.id.track_clear_button);
        startSearchLineButton = templateView
                .findViewById(R.id.start_search_line_button);
        pauseSearchLineButton = templateView
                .findViewById(R.id.pause_search_line_button);
        toggleCallsignsButton = templateView
                .findViewById(R.id.toggle_callsigns_button);
        searchLineColourButton = templateView
                .findViewById(R.id.search_line_colour_button);
        saveTeamSetupButton = templateView
                .findViewById(R.id.save_team_setup_button);
        removeTeamButton = templateView
                .findViewById(R.id.remove_team_button);
        refreshAtakContactsButton = templateView
                .findViewById(R.id.refresh_atak_contacts_button);
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
        trackTabContent = templateView.findViewById(R.id.track_tab_content);
        leaderTeamControls = templateView.findViewById(R.id.leader_team_controls);
        otherTeamsSection = templateView.findViewById(R.id.other_teams_section);
        gridStatusControls = templateView.findViewById(R.id.grid_status_controls);
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
        identityValue = templateView.findViewById(R.id.identity_value);
        homeGpsValue = templateView.findViewById(R.id.home_gps_value);
        gridProgressValue = templateView.findViewById(R.id.grid_progress_value);
        teamSizeValue = templateView.findViewById(R.id.team_size_value);
        teamNameValue = templateView.findViewById(R.id.team_name_value);
        teamMemberCardsContainer = templateView
                .findViewById(R.id.team_member_cards_container);
        invitesRequestsContainer = templateView
                .findViewById(R.id.invites_requests_container);
        teamMarkerVisibilityValue = templateView
                .findViewById(R.id.team_marker_visibility_value);
        atakContactStatusValue = templateView
                .findViewById(R.id.atak_contact_status_value);
        teamAlertsValue = templateView.findViewById(R.id.team_alerts_value);
        homeAlertsValue = templateView.findViewById(R.id.home_alerts_value);
        currentRoleValue = templateView.findViewById(R.id.current_role_value);
        trackStatusValue = templateView.findViewById(R.id.track_status_value);
        trackDetailsValue = templateView.findViewById(R.id.track_details_value);

        homeTabButton.setOnClickListener(this);
        gridTabButton.setOnClickListener(this);
        teamTabButton.setOnClickListener(this);
        trackTabButton.setOnClickListener(this);
        toggleSearchAreaButton.setOnClickListener(this);
        markerModeMeButton.setOnClickListener(this);
        markerModeTeamButton.setOnClickListener(this);
        markerModeLeadersButton.setOnClickListener(this);
        markerModeAllButton.setOnClickListener(this);
        toggleCallsignsButton.setOnClickListener(this);
        trackRecordButton.setOnClickListener(this);
        trackVisibilityButton.setOnClickListener(this);
        trackClearButton.setOnClickListener(this);
        startSearchLineButton.setOnClickListener(this);
        pauseSearchLineButton.setOnClickListener(this);
        searchLineColourButton.setOnClickListener(this);
        saveTeamSetupButton.setOnClickListener(this);
        removeTeamButton.setOnClickListener(this);
        refreshAtakContactsButton.setOnClickListener(this);
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
        } else if (id == R.id.toggle_callsigns_button) {
            boolean showing = mapController.toggleTeamCallsigns();
            Toast.makeText(getMapView().getContext(), showing
                    ? "Callsigns shown"
                    : "Callsigns hidden", Toast.LENGTH_SHORT).show();
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
        } else if (id == R.id.track_clear_button) {
            mapController.clearTrackHistory();
            Toast.makeText(getMapView().getContext(),
                    "Track history cleared", Toast.LENGTH_SHORT).show();
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
                showJoinTeamDialog();
            }
        } else if (id == R.id.remove_team_button) {
            showRemoveTeamDialog();
        } else if (id == R.id.refresh_atak_contacts_button) {
            Toast.makeText(getMapView().getContext(),
                    mapController.refreshAtakTeamContacts(),
                    Toast.LENGTH_SHORT).show();
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
        leaderView = mapController.isLeaderRole();
        currentRoleValue.setText(mapController.getRoleLabel());

        leaderTeamControls.setVisibility(View.VISIBLE);
        int leaderVisibility = leaderView && mapController.isTeamCreated()
                ? View.VISIBLE : View.GONE;
        // Cross-team hierarchy is intentionally hidden until it is backed by
        // confirmed SARtak/ATAK data. Showing placeholder teams caused users to
        // trust information that was not actually connected.
        otherTeamsSection.setVisibility(View.GONE);
        gridStatusControls.setVisibility(leaderVisibility);
        searchLineControls.setVisibility(leaderVisibility);
        teamMarkerVisibilitySection.setVisibility(mapController.isTeamCreated()
                ? View.VISIBLE : View.GONE);
    }

    private void refreshGridUi() {
        refreshRoleUi();
        boolean teamCreated = mapController.isTeamCreated();
        if (!leaderView
                && (mapController.getTeamMarkerVisibilityMode()
                        == TeamMarkerVisibilityMode.LEADERS
                        || mapController.getTeamMarkerVisibilityMode()
                                == TeamMarkerVisibilityMode.ALL_VISIBLE)) {
            mapController.setTeamMarkerVisibilityMode(
                    TeamMarkerVisibilityMode.MY_TEAM);
        }
        toggleSearchAreaButton.setText(mapController.isGridOverlayVisible()
                ? R.string.hide_lanes
                : R.string.show_lanes);
        String status = mapController.getSelectedCellStatus();
        String cellStatus = status.length() == 0
                ? mapController.getSelectedCellId()
                : mapController.getSelectedCellId() + " - " + status;
        String assignmentSummary = mapController.getAssignmentSummary();
        String alertSummary = mapController.getSearchLineWarningSummary();
        String searchLineSummary = mapController.getSearchLineSummary();
        String searchLineMembers = mapController.getSearchLineMemberSummary();

        currentCellValue.setText(cellStatus);
        homeCellValue.setText(cellStatus);
        assignmentValue.setText(assignmentSummary);
        homeAssignmentValue.setText(assignmentSummary);
        homeSearchLineValue.setText(searchLineSummary);
        searchLineStatusValue.setText(searchLineSummary);
        searchLineMembersValue.setText(searchLineMembers);
        pluginHealthValue.setText(mapController.getPluginHealthSummary());
        identityValue.setText(mapController.getIdentitySummary());
        homeGpsValue.setText(mapController.getGpsSummary());
        homeGpsValue.setTextColor(mapController.isGpsActive()
                ? Color.rgb(66, 195, 106)
                : Color.rgb(216, 84, 76));
        gridProgressValue.setText(mapController.getGridProgressSummary());
        teamSizeValue.setText(teamCreated
                ? String.valueOf(mapController.getTeamSize()) : "0");
        teamNameValue.setText(teamCreated
                ? mapController.getTeamName() + " | " + mapController
                        .getTeamId()
                : (leaderView ? "No team created" : "Not in a team"));
        saveTeamSetupButton.setText(leaderView
                ? (teamCreated ? "Edit Team Setup"
                        : "Create Team")
                : "Join Team");
        refreshAtakContactsButton.setVisibility(leaderView
                && teamCreated ? View.VISIBLE : View.GONE);
        removeTeamButton.setVisibility(leaderView && teamCreated
                ? View.VISIBLE : View.GONE);
        templateView.findViewById(R.id.team_size_plus_button).setVisibility(
                leaderView && teamCreated
                        ? View.VISIBLE : View.GONE);
        boolean hasSelectedCell = !"No cell selected".equals(mapController
                .getSelectedCellId());
        gridStatusControls.setVisibility(leaderView && teamCreated
                && hasSelectedCell ? View.VISIBLE : View.GONE);
        setInputTextIfIdle(teamNameInput, mapController.getTeamName());
        setInputTextIfIdle(teamIdInput, mapController.getTeamId());
        teamMarkerVisibilityValue.setText("Showing: "
                + mapController.getTeamMarkerVisibilityLabel());
        updateMarkerVisibilityButtons();
        atakContactStatusValue.setText(mapController.getAtakContactSummary());
        renderTeamMemberCards();
        renderInvitesAndRequests();
        teamAlertsValue.setText(alertSummary);
        homeAlertsValue.setText(alertSummary);
        trackStatusValue.setText(mapController.getTrackStatusSummary());
        trackDetailsValue.setText(mapController.getTrackDetailsSummary());
        pollTeamCotMessages();
        trackRecordButton.setText(mapController.isTrackRecording()
                ? R.string.track_pause
                : R.string.track_resume);
        trackVisibilityButton.setText(mapController.isTrackVisible()
                ? R.string.track_hide
                : R.string.track_show);
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
        toggleCallsignsButton.setText(mapController.isShowingTeamCallsigns()
                ? R.string.hide_callsigns
                : R.string.show_callsigns);
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

    private void pollTeamCotMessages() {
        mapController.advertiseTeamIfDue();

        if (leaderView && mapController.isTeamCreated()) {
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

        if (!leaderView) {
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
    }

    private void updateMarkerVisibilityButtons() {
        TeamMarkerVisibilityMode mode = mapController
                .getTeamMarkerVisibilityMode();
        markerModeMeButton.setEnabled(mode != TeamMarkerVisibilityMode.ME_ONLY);
        markerModeTeamButton.setEnabled(mode
                != TeamMarkerVisibilityMode.MY_TEAM);
        markerModeLeadersButton.setVisibility(leaderView
                ? View.VISIBLE : View.GONE);
        markerModeAllButton.setVisibility(leaderView
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
        } else if (!leaderView) {
            for (SearchTeamCotMessage invite : mapController.getInvitesForMe()) {
                if (resolvedTeamMessages.contains(invite.getUid()))
                    continue;
                invitesRequestsContainer.addView(createIncomingInviteCard(invite));
                count++;
            }
            for (SearchTeamCotMessage request
                    : mapController.getOutgoingJoinRequests()) {
                if (resolvedTeamMessages.contains(request.getUid()))
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

    private boolean hasInviteResponse(SearchTeamCotMessage invite,
            java.util.List<SearchTeamCotMessage> responses) {
        for (SearchTeamCotMessage response : responses) {
            if (invite.getTargetUid().equals(response.getSenderUid())
                    || invite.getTargetCallsign().equalsIgnoreCase(
                            response.getSenderCallsign()))
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
                + " | Colour: " + member.getColorName()
                + " | " + member.getLaneLabel(), 13, false));
        card.addView(createCardText("ATAK group: "
                + member.getAtakGroupName(), 13, false));
        card.addView(createCardText("GPS: " + member.getGpsCoordinates()
                + " | Alt: " + member.getAltitude(), 13, false));
        card.addView(createCardText("Grid: " + member.getCurrentGridCell()
                + " | Last ping: " + member.getLastPing(), 13, false));
        card.addView(createCardText("From you: "
                + member.getDistanceFromYou() + " | From line: "
                + mapController.getMemberSearchLineSummary(
                        member.getUniqueId()), 13, false));
        card.addView(createCardText("Membership: "
                + member.getMembershipStatus().name(), 12, false));
        return card;
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
            startUiRefresh();
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
