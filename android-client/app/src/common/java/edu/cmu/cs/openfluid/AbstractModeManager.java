package edu.cmu.cs.openrtist;

import android.util.Log;
import android.view.View;

import java.util.Map;

public abstract class AbstractModeManager {

    protected Map<ViewID, View> views;
    protected Map<ViewID, Integer> visibility;
    protected static GabrielClientActivity clientActivity;

    public AbstractModeManager(GabrielClientActivity gabrielClientActivity, Map<ViewID, View> views, Map<ViewID, Integer> visibility) {
        this.clientActivity = gabrielClientActivity;
        this.views = views;
        this.visibility = visibility;

        Log.v("ModeManager", "Visibility Size = " + this.visibility.size());
        Log.v("ModeManager", "ViewID Size = " + ViewID.SIZE.getValue());

        assert this.visibility.size() == ViewID.SIZE.getValue();
    }

    public void init() {
        for (ViewID key : visibility.keySet()) {
            Integer view_visibility = visibility.get(key);
            View view = views.get(key);
            view.setVisibility(view_visibility);

            if (view_visibility == View.VISIBLE) {
                view.setOnTouchListener(getOnTouchListener(key));
                view.setOnClickListener(getOnClickListener(key));
            } else {
                view.setOnTouchListener(null);
                view.setOnClickListener(null);
            }
        }
    }

    protected abstract View.OnTouchListener getOnTouchListener(ViewID key);

    protected abstract View.OnClickListener getOnClickListener(ViewID key);
}

