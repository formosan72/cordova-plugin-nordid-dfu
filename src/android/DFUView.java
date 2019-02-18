package com.vensi.plugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.apache.cordova.CordovaWebView;

public class DFUView {
    private FrameLayout mParentLayout;
    private Activity mParentActivity;
    private Context mContext;

    ProgressBar progressbar;
    TextView progressLabel;
    RelativeLayout overlayView;
    AlertDialog progressAlertView;
    TextView statusLabel;
    CordovaWebView mWebView;

    public DFUView(CordovaWebView webView, Activity parentActivity, Context context) {
        mParentLayout = (FrameLayout) webView.getView().getParent();
        mParentActivity = parentActivity;
        mContext = context;
        mWebView = webView;
    }

    public  void showOverlay(final String message)
    {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
                int width = displayMetrics.widthPixels;
                int height = displayMetrics.heightPixels;
                FrameLayout.LayoutParams layoutparams;

                overlayView = new RelativeLayout(mContext);
                overlayView.setBackgroundColor(Color.argb(200,50,50,50));
                layoutparams = new FrameLayout.LayoutParams(width,height);
                layoutparams.setMargins(0,0,0,0);
                overlayView.setLayoutParams(layoutparams);
                overlayView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d("onclicklistener", "listener");
                    }
                });
                overlayView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        return true;
                    }
                });

                statusLabel = new TextView(mContext);
                statusLabel.setText(message);
                statusLabel.setTextColor(Color.WHITE);
                statusLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                RelativeLayout.LayoutParams relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.WRAP_CONTENT);
                relativeLayoutparams.setMargins(0,height/2-40,0,0);
                statusLabel.setLayoutParams(relativeLayoutparams);

                overlayView.addView(statusLabel);

                mParentLayout.addView(overlayView);
            }
        });
    }

    public void showProgressView()
    {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RelativeLayout popup = new RelativeLayout(mContext);
                RelativeLayout.LayoutParams relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                relativeLayoutparams.setMargins(0,0,0,0);
                popup.setLayoutParams(relativeLayoutparams);
                popup.setClickable(false);
                GradientDrawable border = new GradientDrawable();
                border.setStroke(5, new Integer(Color.WHITE));
                border.setColor(new Integer(Color.WHITE));
                popup.setBackground(border);

                progressLabel = new TextView(mContext);
                progressLabel.setText("");
                progressLabel.setTextColor(new Integer(Color.BLACK));
                progressLabel.setGravity(Gravity.CENTER_HORIZONTAL);
                progressLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

                relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,60);
                relativeLayoutparams.setMargins(0,160,0,0);
                progressLabel.setLayoutParams(relativeLayoutparams);

                TextView statusLabel = new TextView(mContext);
                statusLabel.setText("Updating");
                statusLabel.setTextColor(new Integer(Color.BLACK));
                statusLabel.setGravity(Gravity.CENTER_HORIZONTAL);
                statusLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

                relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,60);
                relativeLayoutparams.setMargins(0,30,0,0);
                statusLabel.setLayoutParams(relativeLayoutparams);

                progressbar = new ProgressBar(mContext, null, android.R.attr.progressBarStyleHorizontal);
                relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.WRAP_CONTENT);
                relativeLayoutparams.setMargins(30,100,30,0);
                progressbar.setLayoutParams(relativeLayoutparams);
                progressbar.getIndeterminateDrawable().setColorFilter(Color.DKGRAY, PorterDuff.Mode.MULTIPLY);
                progressbar.getProgressDrawable().setColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY);

                popup.addView(statusLabel);
                popup.addView(progressbar);
                popup.addView(progressLabel);

                AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                builder.setCancelable(false);
                builder.setView(popup);
                if(!mParentActivity.isFinishing()) {
                    progressAlertView = builder.create();
                    progressAlertView.show();
                }
            }
        });
    }

    public void updateProgressView(final int value) {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(value >= 0) {
                    //Log.d("DFU  ","dfu started");
                    progressbar.setProgress(value);
                    progressLabel.setText(""+value+" %");
                }
            }
        });
    }

    public void removeOverlay()
    {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(overlayView != null)
                    overlayView.removeView(statusLabel);
            }
        });
    }

    public void removeProgressView()
    {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(progressAlertView != null)
                    progressAlertView.dismiss();
                if(overlayView != null)
                    mParentLayout.removeView(overlayView);
            }
        });
    }

    public void rotated() {
        mWebView.getView().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                                       int bottom, int oldLeft, int oldTop, int oldRight,
                                       int oldBottom) {
                new Thread() {
                    @Override
                    public void run() {
                        mParentActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
                                int width = displayMetrics.widthPixels;
                                int height = displayMetrics.heightPixels;
                                FrameLayout.LayoutParams layoutparams;
                                if(statusLabel != null) {
                                    RelativeLayout.LayoutParams relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.WRAP_CONTENT);
                                    relativeLayoutparams.setMargins(0,height/2-40,0,0);
                                    statusLabel.setLayoutParams(relativeLayoutparams);
                                }
                                if(overlayView != null) {
                                    overlayView.invalidate();
                                    overlayView.requestLayout();
                                    layoutparams = new FrameLayout.LayoutParams(width,height);
                                    layoutparams.setMargins(0,0,0,0);
                                    overlayView.setLayoutParams(layoutparams);
                                }
                            }
                        });
                    }
                }.start();
            }
        });
    }

}
