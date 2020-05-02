package eyes.blue.humidityrecorder;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class ChartActivity extends AppCompatActivity {
    LineChart chart;
    ValueFormatter formatter;
    int dataLen=0;
    long viewPoing=0;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        /*
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        */

        chart = (LineChart) findViewById(R.id.chart);
        LineData lineData=wrapData();
        if(lineData == null)return;
        chart.setData(lineData);
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(formatter);
        //chart.zoomIn();

        //chart.invalidate();
        //chart.setVisibleXRangeMaximum(dataLen/9);
        //chart.moveViewToX(dataLen/9);
        //wrapData();
    }

    private LineData wrapData(){
        Intent intent = getIntent();
        int lastRecAddr=-1, lastRecTime=-1, interval=-1;
        boolean isIncludeTemp=false;

        String fileName=intent.getStringExtra("FILE_NAME");

        File file=new File(ChartActivity.this.getFilesDir(), fileName);
        dataLen=(int)file.length()-8;
        byte[] data=new byte[dataLen];
        int len=0;
        try {
            FileInputStream fis = new FileInputStream(file);
            interval=fis.read();
            isIncludeTemp=(fis.read()>0);
            lastRecAddr=fis.read()<<8;
            lastRecAddr=lastRecAddr^fis.read();
            lastRecTime=fis.read()<<24;
            lastRecTime=lastRecTime^fis.read()<<16;
            lastRecTime=lastRecTime^fis.read()<<8;
            lastRecTime=lastRecTime^fis.read();

            len=fis.read(data, 0, data.length);
        }catch(Exception e){
            e.printStackTrace();
            Toast.makeText(ChartActivity.this,"Error happen while read data file: "+fileName,Toast.LENGTH_LONG).show();
            return null;
        }

        TimeZone reference = TimeZone.getTimeZone("GMT");
        //GregorianCalendar gc=new GregorianCalendar();
        Calendar gc=Calendar.getInstance(reference);
        gc.setTimeInMillis(946684800000L+((long)lastRecTime*1000));
        //gc.setTimeInMillis(946684800000L);
        Date date = gc.getTime();
        String lastRecTimeStr=android.text.format.DateFormat.getDateFormat(ChartActivity.this).format(date)+" "+fillZero(gc.get(Calendar.HOUR_OF_DAY))+":"+fillZero(gc.get(Calendar.MINUTE));

        //String lastRecTimeStr=gc.get(Calendar.YEAR)+"/"+(gc.get(Calendar.MONTH)+1)+"/"+gc.get(Calendar.DAY_OF_MONTH)+" "+fillZero(gc.get(Calendar.HOUR_OF_DAY))+":"+fillZero(gc.get(Calendar.MINUTE));
        Log.d(getClass().getName(),"Record interval: "+interval);
        Log.d(getClass().getName(),"Is include temperature record: "+interval);
        Log.d(getClass().getName(),"Last record time: "+lastRecTimeStr+"("+gc.getTimeInMillis()+", "+lastRecTime+")");
        Log.d(getClass().getName(),"Newest data address: "+lastRecAddr+", data length: "+lastRecAddr);
        Log.d(getClass().getName(), "Read "+len+" byte from file "+fileName);

        List<Entry> entries = new ArrayList<Entry>();
        int startAddr=lastRecAddr+1;
        int startTime=lastRecTime-((data.length-1)*interval*60);
        //int startTime=-data.length+1;

        if(startAddr>=data.length) // The last record index at last one record.
            startAddr=0;

        if(startAddr!=0) {
            for (int i = startAddr; i < data.length; i++) {
                //gc.setTimeInMillis(946684800000L+(long)(startTime*1000));
                entries.add(new Entry(startTime, data[i]));
                startTime +=60;
            }
            startAddr = 0;
        }

        for(int i=startAddr;i<=lastRecAddr;i++){
            entries.add(new Entry(startTime, data[i]));
            startTime+=60;
        }

        Log.d(getClass().getName(), "There are "+entries.size()+" data in line set.");

        formatter=new ValueFormatter() {
            TimeZone reference = TimeZone.getTimeZone("GMT");
            //Calendar gc=new GregorianCalendar(2000,0,1,0,0,0);
            //int y2kSec=946684800;
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                Calendar gc=Calendar.getInstance(reference);
                gc.setTimeInMillis(946684800000L+((long)value*1000));
                //gc.setTimeInMillis(((long)value*60000));

                //String ret= gc.get(Calendar.YEAR)+"/"+(gc.get(Calendar.MONTH)+1)+"/"+gc.get(Calendar.DAY_OF_MONTH)+"_"+gc.get(Calendar.HOUR_OF_DAY)+":"+gc.get(Calendar.MINUTE)+":"+gc.get(GregorianCalendar.SECOND);
                int hour=gc.get(Calendar.HOUR);
                if(hour == 0)hour =12;
                String ret= hour+":"+gc.get(Calendar.MINUTE);
                //Log.d(getClass().getName(), "Format value to: "+ret+"("+(long)(value*1000)+")");

                return ret;
            }

        };

        LineDataSet dataSet = new LineDataSet(entries, getString(R.string.humidity)); // add entries to dataset
        dataSet.setColor(Color.BLUE);
        dataSet.setDrawValues(false);
        //dataSet.setValueTextColor(...);

        LineData lineData = new LineData(dataSet);

        String descStr=String.format(getString(R.string.chartDescFmt), entries.size(), interval, lastRecTimeStr);
        //chart.getDescription().setText(entries.size()+"筆紀錄, 間格"+interval+"分鐘, 最後紀錄時間"+lastRecTimeStr+", 左舊右新");
        chart.getDescription().setText(descStr);
        dumpData(data);
        return lineData;
    }

    private String fillZero(int num){
        String str=""+num;
        return ((str.length()>1)?str:"0"+str);
    }

    private void dumpData(byte[] data){
        int i=0;
        String line="";

        while(i<data.length){
            line+=data[i]+" ";
            if(i%16==0){
                Log.d(getClass().getName(),line);
                line="";
            }
            i++;
        }
    }

}
