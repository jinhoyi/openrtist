package edu.cmu.cs.openrtist;

import android.app.Activity;
import android.view.View;

import java.util.List;
import java.util.Map;

public class MainMode extends AbstractModeManager {

    public MainMode(Activity currentActivity, List<View> views, Map<View, Boolean> viewUsage) {
        super(currentActivity, views, viewUsage);
    }

    @Override
    public void init() {
        // For each view
        for (View view : views) {
            // Check if the view is used in this mode
            Boolean isUsed = viewUsage.get(view);

            // Define visibility for each view
            setViewVisibility(view, isUsed);

            // Define OnTouchListener and OnClickListener for each view
            setViewListeners(view, isUsed, /* Insert OnTouchListener */, /* Insert OnClickListener */);
        }
    }
}
