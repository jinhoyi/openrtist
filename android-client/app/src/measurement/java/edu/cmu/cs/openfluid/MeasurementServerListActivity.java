package edu.cmu.cs.openfluid;

public class MeasurementServerListActivity extends ServerListActivity {
    ServerListAdapter createServerListAdapter() {
        return new MeasurementServerListAdapter(getApplicationContext(), ItemModelList);
    }
}
