package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How much of the route is left and which route cell comes next. Runs on
 * every panel refresh, so it never walks the route: the route's cell index
 * is built once per plan version, and progress is recomputed from the sparse
 * stored statuses only when a status or the plan changes.
 */
public class RouteProgressTracker {

    /** Route progress at one store revision. */
    public static final class Progress {
        public final int totalCells;
        public final int completeCells;
        /** First route cell, in route order, that is not complete. */
        public final String nextCellId;
        /** 1-based position of {@link #nextCellId} in the route, or 0. */
        public final int nextPosition;

        Progress(int totalCells, int completeCells, String nextCellId,
                int nextPosition) {
            this.totalCells = totalCells;
            this.completeCells = completeCells;
            this.nextCellId = nextCellId;
            this.nextPosition = nextPosition;
        }

        public int getRemaining() {
            return Math.max(0, totalCells - completeCells);
        }

        public boolean isComplete() {
            return totalCells > 0 && nextCellId == null;
        }
    }

    private String cachedPlanVersion;
    private List<String> routeIds;
    private Map<String, Integer> routeIndex;
    private int cachedRevision = -1;
    private Progress cachedProgress;

    /** Null when there is no route. */
    public Progress getProgress(SearchRoutePlan plan,
            SearchGridStateStore stateStore) {
        if (plan == null || plan.isEmpty() || stateStore == null) {
            clear();
            return null;
        }
        String planVersion = plan.getPlanId() + "@" + plan.getUpdatedAt()
                + "#" + plan.size();
        if (!planVersion.equals(cachedPlanVersion)) {
            indexRoute(plan.getCellIds());
            cachedPlanVersion = planVersion;
            cachedProgress = null;
        }
        int revision = stateStore.getRevision();
        if (cachedProgress != null && revision == cachedRevision)
            return cachedProgress;
        cachedProgress = compute(stateStore.getKnownStatuses());
        cachedRevision = revision;
        return cachedProgress;
    }

    public void clear() {
        cachedPlanVersion = null;
        routeIds = null;
        routeIndex = null;
        cachedRevision = -1;
        cachedProgress = null;
    }

    private void indexRoute(List<String> cellIds) {
        routeIds = cellIds;
        routeIndex = new HashMap<>(cellIds.size() * 2);
        for (int i = 0; i < cellIds.size(); i++)
            routeIndex.put(cellIds.get(i), i);
    }

    private Progress compute(Map<String, SearchGridStatus> knownStatuses) {
        int[] completeIndexes = new int[Math.min(routeIds.size(),
                knownStatuses.size())];
        int complete = 0;
        for (Map.Entry<String, SearchGridStatus> entry : knownStatuses
                .entrySet()) {
            if (entry.getValue() != SearchGridStatus.COMPLETE)
                continue;
            Integer index = routeIndex.get(entry.getKey());
            if (index != null && complete < completeIndexes.length)
                completeIndexes[complete++] = index;
        }
        // Route ids are unique, so the sorted indexes are distinct and the
        // first gap is the first route cell that is not complete.
        Arrays.sort(completeIndexes, 0, complete);
        int next = 0;
        for (int i = 0; i < complete && completeIndexes[i] == next; i++)
            next++;
        boolean done = next >= routeIds.size();
        return new Progress(routeIds.size(), complete,
                done ? null : routeIds.get(next), done ? 0 : next + 1);
    }
}
