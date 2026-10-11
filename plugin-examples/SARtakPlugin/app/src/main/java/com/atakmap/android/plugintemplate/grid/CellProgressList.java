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
                    return Double.compare(first.getWest(), second.getWest());
                }
            };

    private List<SearchGridCell> partial =
            Collections.<SearchGridCell>emptyList();
    private List<SearchGridCell> complete =
            Collections.<SearchGridCell>emptyList();
    private String version;
    private int rebuilds;

    /**
     * The requested page of {@code status} cells. The page index is clamped,
     * so a page that emptied after cells were cleared shows the last page.
     */
    public Page page(SearchGridManager gridManager, SearchGridStatus status,
            int pageIndex) {
        String currentVersion = gridManager.getAreaVersion();
        if (!currentVersion.equals(version))
            rebuild(gridManager.getMarkedCellsInArea(), currentVersion);
        return page(status, pageIndex);
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
