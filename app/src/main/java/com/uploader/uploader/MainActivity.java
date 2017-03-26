package com.uploader.uploader;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.ihhira.android.filechooser.FileChooser;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MainActivity extends BaseActivity {

    public Context context1;
    private final static String DEFAULT_INITIAL_DIRECTORY = "/";
    public final static Integer FILE_SELECT_CODE = 001;
    public final String getUrl = "http://192.168.1.6:8080/KMC/Services/DBInterface/txt";
    public final String postUrl = "http://192.168.1.6:8080/KMC/Services/DBInterface/Testing";

    private DownloadManager downloadManager;
    private long downloadReference;
    private long enqueue;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        registerReceiver(downloadReceiver, filter);
    }
    public void getUploader(View view) {
        FileChooser fileChooser = new FileChooser(MainActivity.this, "", FileChooser.DialogType.SELECT_FILE, null);
//        fileChooser.setFilelistFilter("txt, doc,docx", false);
        fileList();
        FileChooser.FileSelectionCallback callback = new FileChooser.FileSelectionCallback() {

            @Override
            public void onSelect(File file) {
                //Do something with the selected file

                new Retrievedata().execute("POST",postUrl,file.getAbsolutePath(),file.getName(),"DOC");
            }

        };
        fileChooser.show(callback);
    }

    public void doDownload(View view) {
        new Retrievedata().execute("GET",getUrl);
    }

    class Retrievedata extends AsyncTask<String, Void, String > {
        @Override
        protected String doInBackground(String... params) {
            String method = params[0];
            String serverUrl = params[1];
            if(method.equals("POST")) {
                try {
                    String path = params[2];
                    String fileName = params[3];
                    final String desc = params[4];
                    File file = new File(path);


                    byte[] value = Base64.encode(file.toString().getBytes(), Base64.DEFAULT);
                    String val = new String(value);
                    Log.i("Encoded String", new String(value));


                    executeMultiPartRequest(serverUrl,val,fileName,"DOCUMENT");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            getFileFromService(getUrl);
        }
    }
    // Upload encoded file to server
    public void executeMultiPartRequest(String urlString, String file, String fileName, String fileDescription) throws Exception {
        HttpClient client = new DefaultHttpClient();
        HttpPost postRequest = new HttpPost(urlString);
        try {
            //Set various attributes
            MultipartEntity multiPartEntity = new MultipartEntity();
            multiPartEntity.addPart("fileDescription", new StringBody(fileDescription != null ? fileDescription : ""));
            multiPartEntity.addPart("fileName", new StringBody(fileName != null ? fileName : "File"));
//            FileBody fileBody = new FileBody(file,"multipart/form-data");
            //Prepare payload
            multiPartEntity.addPart("file", new StringBody(file));

            //Set to request body
            postRequest.setEntity(multiPartEntity);
            //Send request
            HttpResponse response = client.execute(postRequest);

            //Verify response if any
            if (response != null) {
                Log.d("Response...",response.toString());
                Integer resCode = response.getStatusLine().getStatusCode();
                if(resCode == 200) {
                    Toast.makeText(MainActivity.this,"File Uploaded Successfully",Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getApplicationContext(),ex.getMessage().toString(),Toast.LENGTH_SHORT).show();
        }
    }
    // Download encoded file from server


    public void getFileFromService(String url) {
        try {
            downloadManager = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
            Uri Download_Uri = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(Download_Uri);

            //Restrict the types of networks over which this download may proceed.
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

            //Set whether this download may proceed over a roaming connection.
            request.setAllowedOverRoaming(false);

            //Set the title of this download, to be displayed in notifications (if enabled).
//                        request.setTitle("File Downloading");

            //Set a description of this download, to be displayed in notifications (if enabled)
            request.setDescription(url.toString());

            request.setVisibleInDownloadsUi(true);

            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            //Set the local destination for the downloaded file to a path within the application's external files directory
            request.setDestinationInExternalFilesDir(MainActivity.this, Environment.DIRECTORY_DOWNLOADS,"");
//            request.setMimeType("text/*");
            //Enqueue a new download and same the referenceId
            downloadReference = downloadManager.enqueue(request);


            DownloadManager.Query myDownloadQuery = new DownloadManager.Query();
            //set the query filter to our previously Enqueued download
            myDownloadQuery.setFilterById(downloadReference);

            //Query the download manager about downloads that have been requested.
            Cursor cursor = downloadManager.query(myDownloadQuery);
            if(cursor.moveToFirst()){
                checkStatus(cursor);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void checkStatus(Cursor cursor){

        //column for status
        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
        int status = cursor.getInt(columnIndex);
        //column for reason code if the download failed or paused
        int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
        int reason = cursor.getInt(columnReason);
        //get the download filename
        int filenameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME);
        String filename = cursor.getString(filenameIndex);

        String statusText = "";
        String reasonText = "";

        switch(status){
            case DownloadManager.STATUS_FAILED:
                statusText = "STATUS_FAILED";
                switch(reason){
                    case DownloadManager.ERROR_CANNOT_RESUME:
                        reasonText = "ERROR_CANNOT_RESUME";
                        break;
                    case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                        reasonText = "ERROR_DEVICE_NOT_FOUND";
                        break;
                    case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                        reasonText = "ERROR_FILE_ALREADY_EXISTS";
                        break;
                    case DownloadManager.ERROR_FILE_ERROR:
                        reasonText = "ERROR_FILE_ERROR";
                        break;
                    case DownloadManager.ERROR_HTTP_DATA_ERROR:
                        reasonText = "ERROR_HTTP_DATA_ERROR";
                        break;
                    case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                        reasonText = "ERROR_INSUFFICIENT_SPACE";
                        break;
                    case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                        reasonText = "ERROR_TOO_MANY_REDIRECTS";
                        break;
                    case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                        reasonText = "ERROR_UNHANDLED_HTTP_CODE";
                        break;
                    case DownloadManager.ERROR_UNKNOWN:
                        reasonText = "ERROR_UNKNOWN";
                        break;
                }
                break;
            case DownloadManager.STATUS_PAUSED:
                statusText = "STATUS_PAUSED";
                switch(reason){
                    case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                        reasonText = "PAUSED_QUEUED_FOR_WIFI";
                        break;
                    case DownloadManager.PAUSED_UNKNOWN:
                        reasonText = "PAUSED_UNKNOWN";
                        break;
                    case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                        reasonText = "PAUSED_WAITING_FOR_NETWORK";
                        break;
                    case DownloadManager.PAUSED_WAITING_TO_RETRY:
                        reasonText = "PAUSED_WAITING_TO_RETRY";
                        break;
                }
                break;
            case DownloadManager.STATUS_PENDING:
                statusText = "STATUS_PENDING";
                break;
            case DownloadManager.STATUS_RUNNING:
                statusText = "STATUS_RUNNING";
                break;
            case DownloadManager.STATUS_SUCCESSFUL:
                statusText = "STATUS_SUCCESSFUL";
                reasonText = "Filename:\n" + filename;
                break;
        }


        Toast toast = Toast.makeText(MainActivity.this,
                statusText + "\n" +
                        reasonText,
                Toast.LENGTH_LONG);
        toast.show();

    }

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            //check if the broadcast message is for our Enqueued download
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if(downloadReference == referenceId){


                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadReference);
                Cursor cursor = downloadManager.query(query);

                Uri mostRecentDownload =
                        downloadManager.getUriForDownloadedFile(downloadReference);
                // DownloadManager stores the Mime Type. Makes it really easy for us.
                String mimeType =
                        downloadManager.getMimeTypeForDownloadedFile(downloadReference);

                int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                String downloadedPackageUriString = cursor.getString(uriIndex);
                Intent open = new Intent(Intent.ACTION_VIEW);
                open.setDataAndType(Uri.parse(downloadedPackageUriString), mimeType);
                open.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
//                startActivity(open);

                //parse the JSON data and display on the screen
                // Sets up the prevention of an unintentional call. I found it necessary. Maybe not for others.
                try {
                    ParcelFileDescriptor file = downloadManager.openDownloadedFile(downloadReference);


                    Toast toast = Toast.makeText(MainActivity.this,
                            "Downloading of data just finished", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.TOP, 25, 400);
                    toast.show();

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    };
}

