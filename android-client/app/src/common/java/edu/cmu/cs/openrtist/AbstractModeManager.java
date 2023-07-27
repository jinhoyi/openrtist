package edu.cmu.cs.openrtist;

import android.app.Activity;
import android.view.View;

import java.util.List;
import java.util.Map;

public abstract class AbstractModeManager {
    protected List<View> views;
    protected Map<View, Boolean> viewUsage;
    protected Activity currentActivity;

    public AbstractModeManager(Activity currentActivity, List<View> views, Map<View, Boolean> viewUsage) {
        this.currentActivity = currentActivity;
        this.views = views;
        this.viewUsage = viewUsage;
    }

    public abstract void init();

    protected void setViewVisibility(View view, boolean isVisible) {
        if (isVisible) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.INVISIBLE);
        }
    }

    protected void setViewListeners(View view, boolean isUsed, View.OnTouchListener onTouchListener, View.OnClickListener onClickListener) {
        if (isUsed) {
            view.setOnTouchListener(onTouchListener);
            view.setOnClickListener(onClickListener);
        } else {
            view.setOnTouchListener(null);
            view.setOnClickListener(null);
        }
    }
}
}
