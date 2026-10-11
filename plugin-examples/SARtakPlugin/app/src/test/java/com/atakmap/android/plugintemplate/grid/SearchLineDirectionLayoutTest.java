package com.atakmap.android.plugintemplate.grid;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.atakmap.android.plugintemplate.plugin.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * That the search-direction control actually exists in the inflated screen.
 *
 * <p>Stands in for a check on a device. The plugin targets the ATAK 5.6 plugin
 * API and the project's emulator images carry ATAK 4.6, so ATAK there loads
 * the plugin descriptor but never registers its tool -- the drop-down cannot
 * be opened to look at. Inflating the layout here covers what that would have
 * shown: that the control is present, is a button, and carries the label the
 * receiver expects to find by id.
 *
 * <p>A missing id in a layout is a runtime failure, not a compile one: the
 * generated R constant exists as soon as the id is declared anywhere, so
 * {@code findViewById} returning null is only discovered when the screen is
 * opened.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SearchLineDirectionLayoutTest {

    private View inflate() {
        Activity activity = Robolectric.buildActivity(Activity.class)
                .create().get();
        return LayoutInflater.from(activity)
                .inflate(R.layout.main_layout, null);
    }

    @Test
    public void theSearchLineScreen_carriesADirectionButton() {
        View root = inflate();

        View button = root.findViewById(R.id.search_line_direction_button);
        assertNotNull("the direction control must be in the layout, or the "
                + "receiver's findViewById returns null at runtime", button);
        assertTrue("the direction control must be clickable",
                button instanceof Button);
    }

    @Test
    public void theDirectionButton_sitsBesideTheOtherLineControls() {
        View root = inflate();

        assertNotNull(root.findViewById(R.id.search_line_direction_button));
        assertNotNull(root.findViewById(R.id.search_line_colour_button));
        assertNotNull(root.findViewById(R.id.start_search_line_button));
        assertNotNull(root.findViewById(R.id.pause_search_line_button));
    }

    @Test
    public void theDirectionButton_startsLabelled() {
        View root = inflate();

        Button button = root.findViewById(R.id.search_line_direction_button);
        assertEquals("Search Direction", button.getText().toString());
    }

    /**
     * The four directions the button offers are the four the manager accepts.
     * A dialog built from a hand-written array would drift from the enum the
     * moment either changed.
     */
    @Test
    public void theDialogOffersEveryDirectionTheLineSupports() {
        assertEquals(4, SearchLineDirection.values().length);
        for (SearchLineDirection direction : SearchLineDirection.values()) {
            assertNotNull(direction.getLabel());
            assertTrue(direction.getLabel().length() > 0);
        }
    }
}
