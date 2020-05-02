package eyes.blue.humidityrecorder;

import android.app.Application;
import android.bluetooth.BluetoothSocket;

public class PassObjBetweenActivity extends Application {
    private static PassObjBetweenActivity sInstance;
    BluetoothSocket btSocket = null;

    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    public static PassObjBetweenActivity getApplication() {
        return sInstance;
    }

    public void setBtConn(BluetoothSocket socket)
    {
        btSocket=socket;
    }

    public BluetoothSocket getBtConn()
    {
        return btSocket;
    }
}
