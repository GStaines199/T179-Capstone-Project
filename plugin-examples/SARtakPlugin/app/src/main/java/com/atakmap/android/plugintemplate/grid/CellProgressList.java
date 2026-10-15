package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The partial and completed cells of the current area as two sorted, paged
 * lists for the Grid tab.
 * <p>
 * Built from {@link SearchGridManager#getMarkedCellsInArea()}, which comes
 * from the sparse stored statuses, never by walking the area. The sorted
 * lists are cached against {@link SearchGridManager#getAreaVersion()}, so a
 * panel refresh with nothing changed costs a string compare and one page.
 * Free of ATAK types so it can be unit tested on a plain JVM.
 */
public final class CellProgressList {

    /** One page of a list, with the totals the Grid tab shows. */
    public static final class Page {
        public final SearchGridStatus status;
        public final List<SearchGridCell> cells;
        public final int pageIndex;
        public final int pageCount;
        public final int total;
        public final int partialTotal;
        public final int completeTotal;

        Page(SearchGridStatus status, List<SearchGridCell> cells,
                int pageIndex, int pageCount, int total, int partialTotal,
                int completeTotal) {
            this.status = status;
            this.cells = cells;
            this.pageIndex = pageIndex;
            this.pageCount = pageCount;
            this.total = total;
            this.partialTotal = partialTotal;
            this.completeTotal = completeTotal;
        }

        public int firstNumber() {
            return total == 0 ? 0 : pageIndex * PAGE_SIZE + 1;
        }

        public int lastNumber() {
            return firstNumber() + cells.size() - (cells.isEmpty() ? 0 : 1);
        }

        public boolean hasPrevious() {
            return pageIndex > 0;
        }

        public boolean hasNext() {
            return pageIndex + 1 < pageCount;
        }
    }

    public static final int PAGE_SIZE = 8;

    /** Map reading order: north row first, then west to east. */
    private static final Comparator<SearchGridCell> READING_ORDER =
            new Comparator<SearchGridCell>() {
                @Override
                public int compare(SearchGridCell first,
                        SearchGridCell second) {
                    int zone = first.getZoneDescriptor().compareTo(
                            second.getZoneDescriptor());
                    if (zone != 0)
                        return zone;
                    int north = Double.compare(second.getSouth(),
                            first.getSouth());
                    if (north != 0)
                        return north;
                    int west = Double.compare(first.getWest(),
                            second.getWest());
                    if (west != 0)
                        return west;
                    return first.getId().compareTo(second.getId());
                }
            };

    private List<SearchGridCell> partial = new ArrayList<>();
    private List<SearchGridCell> complete = new ArrayList<>();
    private String version;
    private int rebuilds;
    private int markedGeneration = -1;
    private int appliedChanges;

    /**
     * The requested page of {@code status} cells. The page index is clamped,
     * so a page that emptied after cells were cleared shows the last page.
     *
     * <p>After a few status changes the sorted lists are updated in place
     * from {@link SearchGridManager#getMarkedChanges(int)}; re-sorting
     * 30,000 marked cells cost 20-70 ms on the emulator for each change.
     */
    public Page page(SearchGridManager gridManager, SearchGridStatus status,
            int pageIndex) {
        String currentVersion = gridManager.getAreaVersion();
        if (!currentVersion.equals(version)) {
            int generation = gridManager.getMarkedGeneration();
            if (version != null && generation == markedGeneration) {
                List<SearchGridCell> changes = gridManager
                        .getMarkedChanges(appliedChanges);
                for (SearchGridCell cell : changes)
                    apply(cell);
                appliedChanges += changes.size();
                version = currentVersion;
            } else {
                rebuild(gridManager.getMarkedCellsInArea(), currentVersion);
                markedGeneration = generation;
                appliedChanges = gridManager.getMarkedChangeCount();
            }
        }
        return page(status, pageIndex);
    }

    /** Moves one cell to the list for its new status, keeping both sorted. */
    void apply(SearchGridCell cell) {
        remove(partial, cell);
        remove(complete, cell);
        if (cell.getStatus() == SearchGridStatus.COMPLETE)
            insert(complete, cell);
        else if (cell.getStatus() == SearchGridStatus.PARTIAL)
            insert(partial, cell);
    }

    private static void remove(List<SearchGridCell> list,
            SearchGridCell cell) {
        int index = Collections.binarySearch(list, cell, READING_ORDER);
        if (index >= 0)
            list.remove(index);
    }

    private static void insert(List<SearchGridCell> list,
            SearchGridCell cell) {
        int index = Collections.binarySearch(list, cell, READING_ORDER);
        if (index >= 0)
            list.set(index, cell);
        else
            list.add(-index - 1, cell);
    }

    void rebuild(List<SearchGridCell> marked, String newVersion) {
        List<SearchGridCell> newPartial = new ArrayList<>();
        List<SearchGridCell> newComplete = new ArrayList<>();
        if (marked != null) {
            for (SearchGridCell cell : marked) {
                if (cell.getStatus() == SearchGridStatus.COMPLETE)
                    newComplete.add(cell);
                else if (cell.getStatus() == SearchGridStatus.PARTIAL)
                    newPartial.add(cell);
            }
        }
        Collections.sort(newPartial, READING_ORDER);
        Collections.sort(newComplete, READING_ORDER);
        partial = newPartial;
        complete = newComplete;
        version = newVersion;
        rebuilds++;
    }

    Page page(SearchGridStatus status, int pageIndex) {
        SearchGridStatus listed = status == SearchGridStatus.COMPLETE
                ? SearchGridStatus.COMPLETE : SearchGridStatus.PARTIAL;
        List<SearchGridCell> source = listed == SearchGridStatus.COMPLETE
                ? complete : partial;
        int pageCount = Math.max(1, (source.size() + PAGE_SIZE - 1)
                / PAGE_SIZE);
        int index = Math.max(0, Math.min(pageIndex, pageCount - 1));
        int from = index * PAGE_SIZE;
        int to = Math.min(source.size(), from + PAGE_SIZE);
        return new Page(listed, new ArrayList<>(source.subList(from, to)),
                index, pageCount, source.size(), partial.size(),
                complete.size());
    }

    /** How many times the lists were rebuilt; for tests. */
    int getRebuildCount() {
        return rebuilds;
    }
}
