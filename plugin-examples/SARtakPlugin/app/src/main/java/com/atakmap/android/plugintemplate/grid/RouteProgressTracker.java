package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How much of the route is left and which route cell comes next. Runs on
 * every panel refresh, so it never walks the route: the route's cell index
 * is built once per plan version, and progress is kept as a set of complete
 * route positions. A status change only updates the cells the store reports
 * as changed; all stored statuses are only read again when the plan or the
 * operation changes, or too much changed at once.
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
    /** Route positions whose cell is complete. */
    private BitSet complete;
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
        List<String> changed = cachedProgress == null ? null
                : stateStore.changedSince(cachedRevision);
        Map<String, SearchGridStatus> known = stateStore.getKnownStatuses();
        if (changed == null)
            markAll(known);
        else
            markChanged(changed, known);
        cachedProgress = progress();
        cachedRevision = revision;
        return cachedProgress;
    }

    public void clear() {
        cachedPlanVersion = null;
        routeIds = null;
        routeIndex = null;
        complete = null;
        cachedRevision = -1;
        cachedProgress = null;
    }

    private void indexRoute(List<String> cellIds) {
        routeIds = cellIds;
        routeIndex = new HashMap<>(cellIds.size() * 2);
        for (int i = 0; i < cellIds.size(); i++)
            routeIndex.put(cellIds.get(i), i);
    }

    private void markAll(Map<String, SearchGridStatus> knownStatuses) {
        complete = new BitSet(routeIds.size());
        for (Map.Entry<String, SearchGridStatus> entry : knownStatuses
                .entrySet()) {
            if (entry.getValue() != SearchGridStatus.COMPLETE)
                continue;
            Integer index = routeIndex.get(entry.getKey());
            if (index != null)
                complete.set(index);
        }
    }

    private void markChanged(List<String> cellIds,
            Map<String, SearchGridStatus> knownStatuses) {
        for (String cellId : cellIds) {
            Integer index = routeIndex.get(cellId);
            if (index != null)
                complete.set(index, knownStatuses.get(cellId)
                        == SearchGridStatus.COMPLETE);
        }
    }

    private Progress progress() {
        // The first clear position is the first route cell not complete.
        int next = complete.nextClearBit(0);
        boolean done = next >= routeIds.size();
        return new Progress(routeIds.size(), complete.cardinality(),
                done ? null : routeIds.get(next), done ? 0 : next + 1);
    }
}
