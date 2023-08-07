package edu.cmu.cs.gabriel.network;

import android.app.Application;
import android.os.Handler;

import java.util.function.Consumer;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.openfluid.GabrielClientActivity;
import edu.cmu.cs.gabriel.client.comm.MeasurementServerComm;
import edu.cmu.cs.gabriel.protocol.Protos.ResultWrapper;
import edu.cmu.cs.measurementDb.MeasurementDbConsumer;

public class MeasurementComm {
    private final MeasurementServerComm measurementServerComm;
    private final OpenfluidComm openfluidComm;

    public MeasurementComm(
            String endpoint, int port, GabrielClientActivity gabrielClientActivity,
            Handler returnMsgHandler, String tokenLimit) {
        Consumer<ResultWrapper> consumer = new ResultConsumer(
                returnMsgHandler, gabrielClientActivity);
        ErrorConsumer onDisconnect = new ErrorConsumer(returnMsgHandler, gabrielClientActivity);
        MeasurementDbConsumer measurementDbconsumer = new MeasurementDbConsumer(
                gabrielClientActivity, endpoint);
        Application application = gabrielClientActivity.getApplication();
        if (tokenLimit.equals("None")) {
            this.measurementServerComm = MeasurementServerComm.createMeasurementServerComm(
                    consumer, endpoint, port, application, onDisconnect, measurementDbconsumer);
        } else {
            this.measurementServerComm = MeasurementServerComm.createMeasurementServerComm(
                    consumer, endpoint, port, application, onDisconnect, measurementDbconsumer,
                    Integer.parseInt(tokenLimit));
        }

        this.openfluidComm = new OpenfluidComm(this.measurementServerComm, onDisconnect);
    }

    public OpenfluidComm getOpenfluidComm() {
        return openfluidComm;
    }

    public double computeOverallFps() {
        return this.measurementServerComm.computeOverallFps(Const.SOURCE_NAME);
    }
}
