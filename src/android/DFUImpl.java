package com.vensi.plugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.WindowManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import no.nordicsemi.android.dfu.DfuImplementation;

public class DFUImpl extends CordovaPlugin {
    private Context ctx;
    private Activity activity;
    private ProgressUpdateReceiver progressUpdateReceiver;
    private ResponseReceiver responseReceiver;
    private StartServerTask serverTask;
    private StartLocalTask localTask;
    private CallbackContext callbackCtx;
    private JSONObject request;
    private int version = 1;
    private int totalBytesTranser;
    private int firmwareSize;

    DFUView dfuView;

    public DFUImpl(){
    }

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "startDFU":
                callbackCtx = callbackContext;
                ctx = this.cordova.getActivity().getApplicationContext();
                activity = this.cordova.getActivity();
                dfuView = new DFUView(webView, activity, ctx);
                firmwareSize = 0;
                totalBytesTranser = 0;
                request = new JSONObject(data.getString(0));

                progressUpdateReceiver = new ProgressUpdateReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction("Action");
                intentFilter.addAction("zipFileInfo");
                ctx.registerReceiver(progressUpdateReceiver, intentFilter);

                responseReceiver = new ResponseReceiver();
                IntentFilter intentFilter1 = new IntentFilter();
                intentFilter1.addAction("Failed");
                intentFilter1.addAction("Success");
                ctx.registerReceiver(responseReceiver, intentFilter1);

                dfuView.rotated();

                if(!preferences.getString("fileurl","url").equalsIgnoreCase("url")) {
                    String urlString = preferences.getString("fileurl","");
                    if(urlString.startsWith("http")) {
                        try {
                            // Async task to start web socket server
                            serverTask = new StartServerTask();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                serverTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
                            else
                                serverTask.execute("");
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        try {
                            // Async task to start web socket server
                            localTask = new StartLocalTask();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                localTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
                            else
                                localTask.execute("");
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                return true;
            default:
                return true;
        }
    }

    private class StartLocalTask extends AsyncTask<String, Object, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DFU");
                if (!dir.exists()) {
                    dir.mkdirs();
                    return "fail";
                }
                /**  For Development we are taking the zip file from DFU folder
                 * */
                File versionFile = new File(dir, "version.json");


                if (versionFile.exists()) {
                    BufferedReader bufferedReader = new BufferedReader(new FileReader(versionFile));
                    StringBuilder stringBuilder = new StringBuilder();
                    String newLine;
                    while ((newLine = bufferedReader.readLine()) != null) {
                        stringBuilder.append(newLine);
                        stringBuilder.append('\n');
                    }
                    JSONObject jsonObject = new JSONObject(stringBuilder.toString());

                    if (jsonObject.has("date")) {
                        version = Integer.parseInt(jsonObject.getString("date"));
                    }
                }
                int lastBuildVersion = 1;
                if(request.has("swVersion"))
                    lastBuildVersion  = Integer.parseInt(request.getString("swVersion"));
                if(lastBuildVersion == 1) {
                    wakeUpScreen();
                    showOverlay();
                    startDFUProcess();
                    return  "fail";
                } else if(version > lastBuildVersion) {
                    return  "success";
                }
            } catch (JSONException je) {
                je.printStackTrace();
            } catch (IOException ie) {
                ie.printStackTrace();
            }
            return "fail";
        }

        @Override
        protected void onPostExecute(String result) {
            if(result.equalsIgnoreCase("success")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                builder.setMessage("New update available. Would you like to update your device?");
                builder.setCancelable(false);
                builder.setPositiveButton(
                        "Yes",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                wakeUpScreen();
                                showOverlay();
                                startDFUProcess();
                                dialog.cancel();
                            }
                        });

                builder.setNegativeButton(
                        "No",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }
        }
    }

    private class StartServerTask extends AsyncTask<String, Object, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null) {
                    if ((activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.getState() == NetworkInfo.State.CONNECTED)
                            || (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE && activeNetwork.getState() == NetworkInfo.State.CONNECTED)) {
                        Log.d("network info","available");
                        String urlString = preferences.getString("fileurl","");
                        //get the url from config.xml and download the zip file
                        String[] components = urlString.split("/");

                        String versionUrl = urlString.replace(components[components.length-1], "version.json");
                        URL downloadUrl = new URL(versionUrl);

                        /* Open a connection to that URL. */
                        HttpURLConnection ucon = (HttpURLConnection) downloadUrl.openConnection();
                        if( (ucon.getResponseCode() == HttpURLConnection.HTTP_OK) ) {
                            /* Define InputStreams to read from the URLConnection. */
                            InputStream is = ucon.getInputStream();
                            JSONObject jObj = null;
                            String json = "";
                            try {
                                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line + "n");
                                }
                                is.close();
                                json = sb.toString();

                                jObj = new JSONObject(json);
                                if(jObj.has("date"))
                                    version = Integer.parseInt(jObj.getString("date"));

                                int lastBuildVersion = 1;
                                if(request.has("swVersion"))
                                    lastBuildVersion  = Integer.parseInt(request.getString("swVersion"));
                                if(lastBuildVersion == 1) {
                                    wakeUpScreen();
                                    //download server zip file
                                    showOverlay();
                                    downloadZipFileFromServer();
                                } else if(version > lastBuildVersion) {
                                    return  "success";
                                }
                            } catch (Exception e) {
                                Log.e("Buffer Error", "Error converting result " + e.toString());
                            }
                        }
                    } else {
                        Log.d("network info","no network connected");
                        showToast("There is no network, which is required to update firmware.");
                    }
                } else {
                    // not connected to the internet
                    Log.d("network info","no network");
                    showToast("There is no network, which is required to update firmware.");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return "fail";
        }

        @Override
        protected void onPostExecute(String result) {
            if(result.equalsIgnoreCase("success")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                builder.setMessage("New update available. Would you like to update your device?");
                builder.setCancelable(false);
                builder.setPositiveButton(
                        "Yes",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                wakeUpScreen();
                                //download server zip file
                                showOverlay();
                                downloadZipFileFromServer();
                                dialog.cancel();
                            }
                        });

                builder.setNegativeButton(
                        "No",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }
        }
    }

    private void wakeUpScreen() {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void showOverlay() {
        String prepareMessage = "Preparing device for firmware update \n with version : "+version+" \n \n This can take up to 5 mins. \n  Please wait...";
        dfuView.showOverlay(prepareMessage);
    }

    private void startDFUProcess() {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String zipFileName = preferences.getString("fileurl", "");
                    String dfuService_uuid = request.getString("dfuServiceUUID");
                    if(dfuService_uuid == null) {
                        dfuService_uuid = "00001530-1212-EFDE-1523-785FEABCD123";
                    }
                    DfuImplementation dfuImplementation = new DfuImplementation(ctx, activity, request.getString("uuid"));
                    dfuImplementation.startDFU(zipFileName, dfuService_uuid);
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }
        });
    }

    private void downloadZipFileFromServer() {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    if (activeNetwork != null) { // connected to the internet
                        if ((activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.getState() == NetworkInfo.State.CONNECTED)
                                || (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE && activeNetwork.getState() == NetworkInfo.State.CONNECTED)) {
                            File dir = new File(Environment.getExternalStorageDirectory() + "/DFU");
                            if (dir.exists() == false) {
                                dir.mkdirs();
                            }
                            String dfuService_uuid = request.getString("dfuServiceUUID");
                            if(dfuService_uuid == null) {
                                dfuService_uuid = "00001530-1212-EFDE-1523-785FEABCD123";
                            }
                            String urlString = preferences.getString("fileurl","");
                            //get the url from config.xml and download the zip file
                            String[] components = urlString.split("/");
                            String zipFileName = components[components.length-1];

                                URL downloadUrl = new URL(urlString);

                                File file = new File(dir, zipFileName);
                                /* Open a connection to that URL. */
                                HttpURLConnection ucon = (HttpURLConnection) downloadUrl.openConnection();
                                if(ucon.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                    /*
                                    * Define InputStreams to read from the URLConnection.
                                    */
                                    InputStream is = ucon.getInputStream();
                                    BufferedInputStream bis = new BufferedInputStream(is);

                                    /*
                                    * Read bytes to the Buffer until there is nothing more to read(-1).
                                    */
                                    FileOutputStream fos = new FileOutputStream(file);

                                    int current = 0;
                                    while ((current = bis.read()) != -1) {
                                        fos.write(current);
                                    }

                                    fos.close();
                                    DfuImplementation dfuImplementation = new DfuImplementation(ctx, activity, request.getString("uuid"));
                                    dfuImplementation.startDFU(zipFileName, dfuService_uuid);
                                }
                        } else {
                            Log.d("network info","no network connected");
                            showToast("There is no network, which is required to update firmware.");
                        }
                    } else {
                        // not connected to the internet
                        Log.d("network info","no network");
                        showToast("There is no network, which is required to update firmware.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }
        });
    }

    /*to show progress of file transfer*/
    private class ProgressUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            String action = arg1.getAction();
            switch (action)
            {
                case "Action":
                    if(arg1.hasExtra("bytesSent")) {
                        int bytesSent = arg1.getIntExtra("bytesSent", 0);
                        int totalParts = arg1.getIntExtra("totalParts", 0);
                        int currentPart = arg1.getIntExtra("currentPart",0);
                        int currentProgress = 0;
                        if(currentPart == 1) {
                            totalBytesTranser = bytesSent;
                            currentProgress = (totalBytesTranser * 100)/firmwareSize;
                        } else {
                            int temp = totalBytesTranser + bytesSent;
                            currentProgress = (temp * 100)/firmwareSize;
                        }

                        Log.d("bytes transfer ", totalBytesTranser+" totalBytes "+firmwareSize);

                        if(totalBytesTranser == 0)
                        {
                            dfuView.removeOverlay();
                            dfuView.showProgressView();
                        }

                        if(currentProgress >= 0) {
                            dfuView.updateProgressView(currentProgress);
                        }
                    } else if(arg1.hasExtra("removeOverlay")) {
                        Log.v("on  trnasfer","remoeOverlay");
                        //removeOverlay();
                        dfuView.removeProgressView();
                    } else if(arg1.hasExtra("iSFileTransferred")) {
                        Log.v("file transfered ","successfully");
                        //onFileTransfered();
                    }
                    break;
                case "zipFileInfo":
                    int applicationSize = arg1.getIntExtra("applicationSize", 100);
                    int softdeviceSize = arg1.getIntExtra("softdeviceSize", 100);
                    int bootloaderSize = arg1.getIntExtra("bootloaderSize", 100);
                    firmwareSize = applicationSize+softdeviceSize+bootloaderSize;
                    break;
            }
        }
    }

    private void onFileTransfered() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                builder.setMessage("Device Updated with the latest Firmware. Please reconnect to the device.");
                builder.setCancelable(false);
                builder.setPositiveButton(
                        "Ok",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                /*to send response to javascript*/
                                PluginResult result;
                                result = new PluginResult(PluginResult.Status.OK, "DFUResponse");
                                result.setKeepCallback(true);
                                callbackCtx.sendPluginResult(result);

                                dialog.cancel();
                            }
                        });

                AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }
    /*to send response to javascript*/
    private class ResponseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            String action = arg1.getAction();
            if(progressUpdateReceiver != null) {
                ctx.unregisterReceiver(progressUpdateReceiver);
                progressUpdateReceiver = null;
            }
            if(responseReceiver != null) {
                ctx.unregisterReceiver(responseReceiver);
                responseReceiver = null;
            }
            //removeOverlay();
            dfuView.removeProgressView();
            switch (action) {
                case "Success":
                    onFileTransfered();
                    break;
                case "Failed":
                    PluginResult result;
                    result = new PluginResult(PluginResult.Status.OK, "DFUResponse");
                    result.setKeepCallback(true);
                    callbackCtx.sendPluginResult(result);
                    break;
            }
        }
    }

    private void showToast(final String message) {
        activity.runOnUiThread(new Runnable() {
               @Override
               public void run() {
                   AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                   builder.setMessage(message);
                   builder.setCancelable(false);
                   builder.setPositiveButton(
                           "Ok",
                           new DialogInterface.OnClickListener() {
                               public void onClick(DialogInterface dialog, int id) {
                                   dialog.cancel();
                               }
                           });

                   AlertDialog alert = builder.create();
                   alert.show();
               }
        });
    }
}
