package eyes.blue.humidityrecorder;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
/*
public class ListFileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_file);
    }
}
*/


public class ListFileActivity extends ListActivity {

    private String path;
    ArrayList<HashMap<String,String>> myListData = new ArrayList<HashMap<String,String>>();
    SimpleAdapter adapter = null;
    ArrayList<String> fileList=null;
    int clickedItemPostion=-1;
    final String ID_TITLE = "TITLE", ID_SUBTITLE = "SUBTITLE";
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(android.R.style.Theme_Dialog);
        setContentView(R.layout.activity_list_file);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        // Use the current directory as title
        path = "/";
        if (getIntent().hasExtra("path")) {
            path = getIntent().getStringExtra("path");
        }
        //setTitle(path);

        Log.d(getClass().getName(),"List file of "+path);
        // Read all files sorted into the values-array
        fileList = new ArrayList<String>();
        File dir = new File(path);
        if (!dir.canRead()) {
            setTitle(getTitle() + " (inaccessible)");
        }
        String[] list = dir.list();
        if (list != null) {
            for (String file : list) {
                if (!file.startsWith(".")) {
                    fileList.add(file);
                }
            }
        }

        Collections.sort(fileList, Collections.reverseOrder());

        for( int i=0;i<fileList.size() ; ++i) {
            HashMap<String,String> item = new HashMap<String,String>();
            String[] res =fileList.get(i).split("_");    // 1: Recoder device name, 2: Date, 3: time.
            item.put(ID_TITLE, res[0]+" - "+(i+1));
            item.put(ID_SUBTITLE,getSubtitle(res[1], res[2]));
            myListData.add(item);
        }

        adapter=new SimpleAdapter(
                this,
                myListData,
                android.R.layout.simple_list_item_2,
                new String[] { ID_TITLE, ID_SUBTITLE },
                new int[] { android.R.id.text1, android.R.id.text2 } );

        // Put the data into the list
        //adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, fileList);
        setListAdapter(adapter);
        registerForContextMenu(findViewById(android.R.id.list));

        if(fileList.size() == 0)
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ListFileActivity.this, getString(R.string.noHistoryFile), Toast.LENGTH_LONG);
                }
            },100);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId()==android.R.id.list) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.list_file_opt_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        //AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        String fileName;
        switch(item.getItemId()) {
            case R.id.open:
                Intent resultIntent = new Intent();
                //fileName=(String) getListAdapter().getItem(clickedItemPostion);
                fileName=(String) fileList.get(clickedItemPostion);

                //resultIntent.putExtra("file", fileName);
                //setResult(Activity.RESULT_OK, resultIntent);
                //finish();
                Intent intent = new Intent(ListFileActivity.this, ChartActivity.class);
                intent.putExtra("FILE_NAME", fileName);
                startActivity(intent);
                return true;
            case R.id.delete:
                //fileName=(String) getListAdapter().getItem(clickedItemPostion);
                fileName=(String) fileList.get(clickedItemPostion);

                File f=new File(getFilesDir()+File.separator+fileName);
                boolean isDelete=f.delete();
                Log.d(getClass().getName(),"Delete "+fileName+((isDelete)?" Success.":" Failure."));

                for(int i=0;i<fileList.size();i++){
                    if(fileList.get(i).equals(fileName)){
                        fileList.remove(i);
                        Log.d(getClass().getName(),"Remove "+fileName+" from fileList success.");
                        break;
                    }
                }

                myListData.remove(clickedItemPostion);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(adapter!=null)adapter.notifyDataSetChanged();
                    }
                });
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Log.d(getClass().getName(), "User click position: "+position);
        clickedItemPostion=position;
        registerForContextMenu(findViewById(android.R.id.list));
        openContextMenu(findViewById(android.R.id.list));
    }

    private String getSubtitle(String date, String time){
        GregorianCalendar gc = new GregorianCalendar();
        String[] a=date.split("y");
        gc.set(Calendar.YEAR, Integer.parseInt(a[0]));
        a=a[1].split("m");
        gc.set(Calendar.MONTH, Integer.parseInt(a[0])-1);
        gc.set(Calendar.DAY_OF_MONTH, Integer.parseInt(a[1]));

        a=time.split("-");
        //gc.set(Calendar.HOUR_OF_DAY, Integer.parseInt(a[0]));
        //gc.set(Calendar.MINUTE, Integer.parseInt(a[1]));
        //gc.set(Calendar.SECOND, Integer.parseInt(a[2]));

        String dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(gc.getTime());
        String timeStr=String.format(Locale.getDefault(), "%02d", Integer.parseInt(a[0]))+":"+String.format(Locale.getDefault(), "%02d", Integer.parseInt(a[1]))+":"+String.format(Locale.getDefault(), "%02d", Integer.parseInt(a[2]));
        return dateStr+" "+timeStr;
    }
}