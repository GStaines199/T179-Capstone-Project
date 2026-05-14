package com.atakmap.android.plugintemplate.grid;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class SearchTeamStateStore {

    private static final String PREFS_NAME = "sartak_team_state";
    private static final String KEY_TEAM_NAME = "team_name";
    private static final String KEY_TEAM_ID = "team_id";
    private static final String KEY_MEMBERS = "members";
    private static final String KEY_TEAM_CREATED = "team_created";

    private final SharedPreferences preferences;

    public SearchTeamStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public void load(SearchPartyAssignmentManager assignmentManager) {
        assignmentManager.setTeamDetails(
                preferences.getString(KEY_TEAM_NAME,
                        assignmentManager.getTeamName()),
                preferences.getString(KEY_TEAM_ID,
                        assignmentManager.getTeamId()));
        assignmentManager.setTeamCreated(preferences.getBoolean(
                KEY_TEAM_CREATED, false));

        Set<String> members = preferences.getStringSet(KEY_MEMBERS,
                new HashSet<String>());
        if (members == null)
            return;
        for (String encoded : members) {
            if (encoded == null)
                continue;
            String[] parts = encoded.split("\t", 2);
            if (parts.length == 0 || parts[0].trim().length() == 0)
                continue;
            String callsign = parts.length > 1 ? parts[1] : parts[0];
            assignmentManager.addConfirmedRosterMember(parts[0], callsign,
                    SearchTeamMember.TeamRole.SEARCHER);
        }
    }

    public void save(SearchPartyAssignmentManager assignmentManager) {
        Set<String> members = new HashSet<>();
        for (SearchTeamMember member : assignmentManager.getVisibleMembers()) {
            if (member.isTeamLeader())
                continue;
            members.add(member.getUniqueId() + "\t" + member.getCallsign());
        }

        preferences.edit()
                .putString(KEY_TEAM_NAME, assignmentManager.getTeamName())
                .putString(KEY_TEAM_ID, assignmentManager.getTeamId())
                .putBoolean(KEY_TEAM_CREATED,
                        assignmentManager.isTeamCreated())
                .putStringSet(KEY_MEMBERS, members)
                .apply();
    }

    public void clear() {
        preferences.edit()
                .remove(KEY_TEAM_NAME)
                .remove(KEY_TEAM_ID)
                .remove(KEY_MEMBERS)
                .putBoolean(KEY_TEAM_CREATED, false)
                .apply();
    }
}
