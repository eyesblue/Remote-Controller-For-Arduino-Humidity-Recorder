package eyes.blue.humidityrecorder;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    // GUI Components
    private TextView mBluetoothStatus, upTimeText, batteryStateText, recIntervalText, lastRecAddrText, TemperatureText, humidityText;
    private Button syncTime, getData, refreshState;
    private Set<BluetoothDevice> mPairedDevices;
    //private BluetoothDevice bluetoothDevice = null;
    ProgressBar progressBar=null;
    //private ArrayAdapter<String> mBTArrayAdapter;
    //private CheckBox mLED1;
    //private EditText inputdata;
    private SeekBar brighBar;
    Spinner intervalSpinner;

    private Handler mHandler;
    // Our main handler that will receive callback notifications
    //private ConnectedThread mConnectedThread;
    // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null;
    // bi-directional client-to-client data path
    boolean userIsInteracting = false;

    // used in bluetooth handler to identify message status
    private String btDevName = null;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        mBluetoothStatus = (TextView) findViewById(R.id.bluetoothStatus);
        upTimeText=findViewById(R.id.upTimeText);
        batteryStateText = findViewById(R.id.batteryStateText);
        recIntervalText = findViewById(R.id.recIntervalText);
        lastRecAddrText = findViewById(R.id.lastRecAddrText);
        TemperatureText = findViewById(R.id.TemperatureText);
        humidityText = findViewById(R.id.humidityText);

        syncTime = (Button) findViewById(R.id.synTimeBtn);
        getData = (Button) findViewById(R.id.getData);
        refreshState=findViewById(R.id.refreshState);
        brighBar = findViewById(R.id.brighBar);
        intervalSpinner = findViewById(R.id.intervalSpinner);

        progressBar=findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        mBTSocket=PassObjBetweenActivity.getApplication().getBtConn();

        syncTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isDeviceReady())return;
                enableAllWidget(false);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            setStatusBarMsg(getString(R.string.sync_time));
                            //GregorianCalendar gc=new GregorianCalendar();
                            Calendar gc = Calendar.getInstance();
                            gc.add(GregorianCalendar.SECOND, 1);
                            byte year = (byte) (gc.get(GregorianCalendar.YEAR) - 2000);
                            byte month = (byte) (gc.get(GregorianCalendar.MONTH) + 1);
                            byte day = (byte) gc.get(GregorianCalendar.DAY_OF_MONTH);
                            byte hour = (byte) gc.get(GregorianCalendar.HOUR_OF_DAY);
                            byte min = (byte) gc.get(GregorianCalendar.MINUTE);
                            byte sec = (byte) gc.get(GregorianCalendar.SECOND);

                            byte[] data = {(byte) 'T', year, month, day, hour, min, sec};
                            int millis = (int)( System.currentTimeMillis() % 1000);

                            try {
                                Thread.sleep(millis);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            mBTSocket.getOutputStream().write(data);
                            Log.d(getClass().getName(), "Sync "+millis+"ms for synchronize second time.");

                            setStatusBarMsg(getString(R.string.commSuccess));
                        } catch (IOException ioe) {
                            postDelayToastMsg(getString(R.string.ioeStr));
                            setStatusBarMsg(getString(R.string.ioeStr));
                        }finally {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    enableAllWidget(true);
                                }
                            });
                        }
                    }
                }).start();



            }
        });

        getData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isDeviceReady())return;

                enableAllWidget(false);
                progressBar.setProgress(0);
                progressBar.setVisibility(View.VISIBLE);
                setStatusBarMsg(getString(R.string.humidityTransferring));

                Thread t=new Thread(new Runnable() {
                    @Override
                    public void run() {
                        GregorianCalendar gc = new GregorianCalendar();
                        final String filename = btDevName + "_"+ gc.get(GregorianCalendar.YEAR) + "y" + (gc.get(GregorianCalendar.MONTH) + 1) + "m" + gc.get(GregorianCalendar.DAY_OF_MONTH) + "_" + gc.get(GregorianCalendar.HOUR_OF_DAY) + "-" + gc.get(GregorianCalendar.MINUTE) + "-" + gc.get(GregorianCalendar.SECOND);
                        FileOutputStream fos=null;

                        try {
                            // Data format: dataLen(2byte), recInterval(byte), isIncludeTemp(byte), lastRecAddr(2byte), lastRecTime(4byte), data ...
                            dropUnwantedData();
                            mBTSocket.getOutputStream().write('D');
                            InputStream is = mBTSocket.getInputStream();
                            long startTime = System.currentTimeMillis();
                            byte[] data=readDataNonBlock(is , 2); // Read first element [dataLen] for sure data we want read.

                            ByteArrayInputStream bais=new ByteArrayInputStream(data);
                            int dataLen = bais.read() << 8;
                            dataLen = dataLen ^ bais.read();

                            Log.d(getClass().getName(), "Read data part "+dataLen+" bytes.");

                            progressBar.setMax(dataLen);
                            data = new byte[dataLen];
                            for (int i = 0; i < dataLen; i++) {
                                data[i] = (byte) is.read();
                                progressBar.setProgress(i);
                            }

                            long spendTime = System.currentTimeMillis() - startTime;
                            final String statusBarMsg=String.format(getString(R.string.humidityTransferSuccess), dataLen, spendTime);

                            Log.d(getClass().getName(), "Data save to file: " + filename);
                            File file = new File(MainActivity.this.getFilesDir(), filename);
                            fos = new FileOutputStream(file);
                            //byte[] meta={(byte)interval,(byte)isIncludeTemp,(byte)(lastRecAddr>>8),(byte)(lastRecAddr),(byte)(lastRecTime>>24),(byte)(lastRecTime>>16),(byte)(lastRecTime>>8),(byte)(lastRecTime),(byte)(dataLen>>8),(byte)(dataLen)};
                            //fos.write(meta);
                            fos.write(data);
                            fos.flush();
                            fos.close();

                            postDelayToastMsg(String.format(getString(R.string.dataSaveToFile), filename));
                            setStatusBarMsg(statusBarMsg);
                            Log.d(getClass().getName(), statusBarMsg);

                            Intent intent = new Intent(MainActivity.this, ChartActivity.class);
                            intent.putExtra("FILE_NAME", filename);
                            startActivity(intent);

                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                            postDelayToastMsg(getString(R.string.ioeStr));
                        }finally {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                    enableAllWidget(true);
                                }
                            });
                        }
                    }
                });
                t.start();
            }
        });

        refreshState.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getDeviceState();
            }
        });

        brighBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            //int lastData=-1;
            @Override
            public void onProgressChanged(SeekBar seekBar, final int progress, boolean fromUser) {
                setStatusBarMsg(String.format(getString(R.string.setBrightMsg),progress));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        sendData(progress);
                    }
                }).start();

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendData(127);
                setStatusBarMsg(getString(R.string.commSuccess));
            }

            private void sendData(int data) {
                Log.d(getClass().getName(), "Send Brightness command: " + data);
                if(!isDeviceReady())return;

                try {
                    //setStatusBarMsg(String.format(getString(R.string.setBrightMsg),data));
                    mBTSocket.getOutputStream().write((byte) 'B');
                    mBTSocket.getOutputStream().write((byte) data);
                    //setStatusBarMsg(getString(R.string.commSuccess));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    postDelayToastMsg(getString(R.string.ioeStr));
                }
            }
        });

        final String[] intervalStr = new String[120];
        for (int i = 0; i < 120; i++)
            intervalStr[i] = "" + (i + 1);

        ArrayAdapter<String> opts = new ArrayAdapter<>(MainActivity.this,
                android.R.layout.simple_spinner_dropdown_item,
                intervalStr);
        intervalSpinner.setAdapter(opts);
        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(!userIsInteracting)return;

                int interval = position + 1;
                Log.d(getClass().getName(), "Send Interval command: " + interval);
                if(!isDeviceReady())return;


                try {
                    setStatusBarMsg(String.format(getString(R.string.setRecIntervalMsg),interval));
                    mBTSocket.getOutputStream().write((byte) 'I');
                    mBTSocket.getOutputStream().write((byte) interval);
                    setStatusBarMsg(getString(R.string.commSuccess));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    Toast.makeText(MainActivity.this, "IOException happen while send command !!!", Toast.LENGTH_LONG).show();
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        btDevName = getIntent().getStringExtra("btDevName");
        Toolbar mActionBarToolbar = (Toolbar) findViewById(R.id.toolbar);
        mActionBarToolbar.setTitle(btDevName);
        setSupportActionBar(mActionBarToolbar);

        // Read data from the socket and drop, if there is data last communication.
        new Thread(new Runnable() {
            @Override
            public void run() {
                dropUnwantedData();
                getDeviceState();
            }
        }).start();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_history) {
            Intent listActivity=new Intent(MainActivity.this, ListFileActivity.class);
            listActivity.putExtra("path", MainActivity.this.getFilesDir().getAbsolutePath());
            startActivity(listActivity);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

/*
    int PICKFILE_RESULT_CODE = 1;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICKFILE_RESULT_CODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                String filePath = data.getStringExtra("file");
                Log.d(getClass().getName(), "User select path: " + filePath);
            }
        }
    }
*/

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        userIsInteracting = true;
    }

    @Override
    public void onBackPressed() {
        try {
            if (mBTSocket !=null && mBTSocket.isConnected())
                mBTSocket.close();
        }catch (IOException ioe){
            Log.d(getClass().getName(),"IOException happen while disconnect bluetooth connection.");
            ioe.printStackTrace();
        }
        super.onBackPressed();
    }

    int READ_TIME_OUT=4000;
    private byte[] readDataNonBlock(InputStream is, int len)throws IOException{
        byte[] b=new byte[len];
        long startTime=System.currentTimeMillis();

            for (int i = 0; i < len; i++) {
                while(is.available() == 0) {
                    try {
                        Thread.sleep(5);
                    }catch(InterruptedException ie){
                        ie.printStackTrace();
                    }
                    if(System.currentTimeMillis() - startTime > READ_TIME_OUT)
                        throw new IOException("Read timeout of InputStream in "+ READ_TIME_OUT + " second.");
                }
                    b[i]=(byte)is.read();
            }

            return b;
    }

    private void getDeviceState() {
        Log.d(getClass().getName(), "Send get device state command: S");
        if(!isDeviceReady())return;

        enableAllWidget(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    setStatusBarMsg(getString(R.string.getDevStateMsg));
                    dropUnwantedData();
                    mBTSocket.getOutputStream().write((byte) 'S');
                    //final InputStream is = mBTSocket.getInputStream();
                    byte[] b=readDataNonBlock(mBTSocket.getInputStream(),10);

                    ByteArrayInputStream bis=new ByteArrayInputStream(b);

                    long upTime=bis.read()<<24;
                    upTime^=bis.read()<<16;
                    upTime^=bis.read()<<8;
                    upTime^=bis.read();
                    int batteryState = bis.read();
                    final int recInterval = bis.read();
                    int lastRecAddr = bis.read() << 8;
                    lastRecAddr ^= bis.read();
                    final int temp = bis.read();
                    final int hmd = bis.read();

                    final int lastRecAddress=lastRecAddr;
                    final String upTimeStr=secToDHMS(upTime);
                    final boolean isBatteryFine = (batteryState > 0);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            upTimeText.setText(upTimeStr);
                            if (!isBatteryFine) batteryStateText.setTextColor(Color.RED);
                            else batteryStateText.setTextColor(upTimeText.getTextColors());
                            batteryStateText.setText(isBatteryFine ? getString(R.string.fine) : getString(R.string.lowPower));
                            recIntervalText.setText("" + recInterval);
                            lastRecAddrText.setText("" + lastRecAddress);
                            TemperatureText.setText("" + temp);
                            humidityText.setText("" + hmd);
                            setStatusBarMsg(getString(R.string.commSuccess));
                        }
                    });

                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    postDelayToastMsg(getString(R.string.ioeStr));
                }finally {
                    enableAllWidget(true);
                }
            }
        }).start();

    }

    private void setContentVisiable(final boolean isVisiable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LinearLayout ll = findViewById(R.id.contentLinearLayout);
                if(isVisiable)ll.setVisibility(View.VISIBLE);
                else ll.setVisibility(View.GONE);

                final int childCount = ll.getChildCount();
                for(int i = 0;i<childCount;i++){
                    View v = ll.getChildAt(i);
                    if(isVisiable)v.setVisibility(View.VISIBLE);
                    else v.setVisibility(View.GONE);
                }
            }
        });
    }

    /* The function should not packet with Thread, the caller should already execute with thread,
        if the function packet with Thread, it will cause problem.
        此函式不該用Thread包裝，因呼叫者已經用Thread包裝，若此函式用Thread包裝，時序上會出問題！
    */
    private void dropUnwantedData(){
        if(!isDeviceReady())return;

        try {
            int len=0;
            InputStream is = mBTSocket.getInputStream();
            while(is.available()>0) {
                is.read();
                len++;
                try {
                    Thread.sleep(2);
                }catch(InterruptedException ie){ie.printStackTrace();}
            }
            Log.d(getClass().getName(), "========== Drop unwanted data length:"+len+" ==========");
        }catch(IOException ioe){
            Log.e(getClass().getName(), getString(R.string.ioeStr));
            ioe.printStackTrace();
        }
    }

    private boolean isDeviceReady(){
        if(mBTSocket==null || !mBTSocket.isConnected()){
            postDelayToastMsg(getString(R.string.devOffline));
            return false;
        }
        return true;
    }

    private String secToDHMS(long timeSec){
        int day=(int)(timeSec/86400L);
        long left=timeSec%86400;
        int hour=(int)left/3600;
        left%=3600;
        int min=(int)left/60;
        int sec=(int)left%60;
        return ((day!=0)?day+getString(R.string.dayStr):"")+((hour!=0)?hour+getString(R.string.hourStr):"")+((min!=0)?min+getString(R.string.minStr):"")+sec+getString(R.string.secStr);
    }

    public void postDelayToastMsg(int msgId){
        postDelayToastMsg(getString(msgId));
    }

    public void postDelayToastMsg(final String msg){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                },100);
            }
        });
    }

    public void setStatusBarMsg(final String msg){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBluetoothStatus.setText(msg);
            }
        });
    }

    private void enableAllWidget(final boolean bool){
        final View[] views=new View[]{syncTime, getData, refreshState,brighBar, intervalSpinner};
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for(View v:views)
                    v.setEnabled(bool);
            }
        });
    }

    /*
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        SystemClock.sleep(100);
                        //pause and wait for rest of data
                        bytes = mmInStream.available();
                        // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes);
                        // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
    /*
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
    /*
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
    */
}






    /*
    int REQUEST_ENABLE_BT=1;
    boolean isBluetoothDevOn=false;
    Button btConnBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        IntentFilter bluetoothStateFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, bluetoothStateFilter);


        btConnBtn = findViewById(R.id.btConnBtn);
        btConnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                discoverConnBtDev();



            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(bluetoothReceiver);
    }

    public void showSimpleMsgDialog(String title, String msg){
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void discoverConnBtDev(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            showSimpleMsgDialog("No bluetooth hardware.", "Your device not support bluetooth.");
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        /* // 查詢已連接過的裝置
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }


}

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        isBluetoothDevOn = false;
                        Toast.makeText(MainActivity.this, "Bluetooth started.", Toast.LENGTH_LONG).show();
                        ((TextView) findViewById(R.id.msg)).setText("Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        isBluetoothDevOn = true;
                        Toast.makeText(MainActivity.this, "Bluetooth started.", Toast.LENGTH_LONG).show();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }

            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    };

        }
*/