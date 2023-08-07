package edu.cmu.cs.gabriel;

import edu.cmu.cs.gabriel.network.MeasurementComm;
import edu.cmu.cs.openfluid.GabrielClientActivity;

public class MeasurementClientActivity extends GabrielClientActivity {
    @Override
    void setupComm() {
        int port = getPort();
        MeasurementComm measurementComm = new MeasurementComm(
                this.serverIP, port, this, this.returnMsgHandler, Const.TOKEN_LIMIT);
        this.setOpenfluidComm(measurementComm.getOpenfluidComm());
    }
}
