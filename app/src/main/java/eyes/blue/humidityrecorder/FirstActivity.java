package eyes.blue.humidityrecorder;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class FirstActivity extends AppCompatActivity {
    private Button mListPairedDevicesBtn, btnBtSetting;
    private TextView statusBar;
    private ArrayAdapter<String> mBTArrayAdapter;
    private BluetoothAdapter mBTAdapter;
    final ArrayList<BluetoothDevice> devList = new ArrayList<>();
    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1;
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier
    // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2;
    // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3;

    private Handler mHandler;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        TextView desc=findViewById(R.id.textDesc);
        String descText=getString(R.string.appDescriptor);
        descText=descText.replace("$ARDUINO_HUB","<a href=\"https://create.arduino.cc/projecthub/eyesblue/humidity-recorder-control-with-android-app-3ccb1e\">Arduino hub</a>");
        desc.setText(HtmlCompat.fromHtml(descText, HtmlCompat.FROM_HTML_MODE_LEGACY));
        desc.setMovementMethod(LinkMovementMethod.getInstance());

        mListPairedDevicesBtn = (Button) findViewById(R.id.pairedBtn);
        btnBtSetting=findViewById(R.id.btnBtSetting);
        statusBar=findViewById(R.id.statusBar);
        mBTAdapter= BluetoothAdapter.getDefaultAdapter();

        if (mBTAdapter == null) {
            postDelayMsg(getString(R.string.noBThost));
            return;
        }

        mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.fireSelectEvent(mFirebaseAnalytics, getClass().getName(), Util.BUTTON_CLICK, "LIST_PAIRED_DEVICE");
                listPairedDevices(v);
            }
        });
        btnBtSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.fireSelectEvent(mFirebaseAnalytics, getClass().getName(), Util.BUTTON_CLICK, "PAIR_NEW_DEVICE");
                final Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.bluetooth.BluetoothSettings");
                intent.setComponent(cn);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity( intent);
            }
        });

        //定義執行緒 當收到不同的指令做對應的內容
        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {

                if (msg.what == CONNECTING_STATUS) {
                    //收到CONNECTING_STATUS 顯示以下訊息
                    if (msg.arg1 == 1)
                        statusBar.setText("Connected to Device: " + (String) (msg.obj));
                    else
                        statusBar.setText("Connection Failed");
                }
            }
        };

         mBTArrayAdapter=new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);

        // 詢問藍芽裝置權限
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]
                    {Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    //定義當按下跳出是否開啟藍芽視窗後要做的內容
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        super.onActivityResult(requestCode, resultCode, Data);

        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                //mBluetoothStatus.setText("Enabled");
                Toast.makeText(getApplicationContext(), getString(R.string.enableStr), Toast.LENGTH_SHORT).show();
            } else
                //mBluetoothStatus.setText("Disabled");
                Toast.makeText(getApplicationContext(), getString(R.string.disableStr), Toast.LENGTH_SHORT).show();
        }
    }

    private void listPairedDevices(View view) {
        Set<BluetoothDevice> mPairedDevices = mBTAdapter.getBondedDevices();
        if (!mBTAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), getString(R.string.btNotOn), Toast.LENGTH_SHORT).show();
            return;
        }
        mBTArrayAdapter.clear();
        for (BluetoothDevice device : mPairedDevices) {
            mBTArrayAdapter.add(device.getName());
            devList.add(device);
        }

        AlertDialog.Builder builderSingle = new AlertDialog.Builder(FirstActivity.this);
        builderSingle.setTitle(getString(R.string.selectBtDev));

        builderSingle.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builderSingle.setAdapter(mBTArrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!mBTAdapter.isEnabled()) {
                    Toast.makeText(getBaseContext(), getString(R.string.btNotOn), Toast.LENGTH_SHORT).show();
                    return;
                }

                BluetoothDevice bd=devList.get(which);
                if(bd == null) {
                    postDelayMsg(R.string.recorder_dev_not_found);
                    return;
                }
                Log.d(getClass().getName(),"User select "+bd.getName()+", connect to device.");
                connectToDevice(bd);
            }
        });
        builderSingle.show();
    }

    private void connectToDevice(final BluetoothDevice pairedDevice){
        statusBar.setText(pairedDevice.getName()+" "+getString(R.string.connecting));
        // Get the device MAC address, which is the last 17 chars in the View
        final String address = pairedDevice.getAddress();
        final String name = pairedDevice.getName();

        // Spawn a new thread to avoid blocking the GUI one
        new Thread() {
            public void run() {
                //取得裝置MAC找到連接的藍芽裝置
                //BluetoothDevice device = mBTAdapter.getRemoteDevice(address);
                BluetoothSocket bSock;
                try {
                    bSock = createBluetoothSocket(pairedDevice);
                    //建立藍芽socket
                } catch (IOException e) {
                    postDelayMsg(R.string.sock_creation_failed);
                    return;
                }
                // Establish the Bluetooth socket connection.
                try {
                    bSock.connect(); //建立藍芽連線
                } catch (IOException e) {
                    try {
                        bSock.close(); //關閉socket
                        //開啟執行緒 顯示訊息
                        mHandler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
                        e.printStackTrace();
                        return;
                    } catch (IOException e2) {
                        //insert code to deal with this
                        postDelayMsg(R.string.sock_creation_failed);
                        e2.printStackTrace();
                        return;
                    }
                }


                mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name).sendToTarget();
                PassObjBetweenActivity.getApplication().setBtConn(bSock);
                Intent intent = new Intent(FirstActivity.this, MainActivity.class);
                intent.putExtra("btDevName", name);
                startActivity(intent);
            }
        }.start();
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connection with BT device using UUID
    }

    public void postDelayMsg(int msgId){
        postDelayMsg(getString(msgId));
    }

    public void postDelayMsg(final String msg){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FirstActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        },100);
    }

}
