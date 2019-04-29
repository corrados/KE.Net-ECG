package net.kenevans.polar.polarecg;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.polar.polarecg.R;

import org.reactivestreams.Publisher;

import java.util.Date;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarSensorSetting;

public class ECGActivity extends AppCompatActivity implements PlotterListener {
    private String TAG = "Polar_ECGActivity";
    // The total number of points = 26 * total large blocks desired
    private static final int POINTS_TO_PLOT = 520;
    private XYPlot mPlot;
    private Plotter mPlotter;

    TextView mTextViewHR, mTextViewFW;
    private PolarBleApi mApi;
    private Disposable mEcgDisposable;
    private boolean mPlaying;
    private Menu mMenu;
    private String DEVICE_ID;

    // Used in Logging
    private long time0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg);
        DEVICE_ID = getIntent().getStringExtra("id");
        mTextViewHR = findViewById(R.id.info);
        mTextViewFW = findViewById(R.id.fw);

        mPlot = findViewById(R.id.plot);

        mApi = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING |
                        PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);
        mApi.setApiCallback(new PolarBleApiCallbackAdapter() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void polarDeviceConnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                Toast.makeText(ECGActivity.this, R.string.connected,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void polarDeviceDisconnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected " + s);
            }

            @Override
            public void ecgFeatureReady(String s) {
                Log.d(TAG, "ECG Feature ready " + s);
                streamECG();
            }

            @Override
            public void hrFeatureReady(String s) {
                Log.d(TAG, "HR Feature ready " + s);
            }

            @Override
            public void fwInformationReceived(String s, String s1) {
                String msg = "Firmware: " + s1.trim();
                Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                mTextViewFW.append(msg + "\n");
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
                String msg = "ID: " + s + "\nBattery level: " + i;
                Log.d(TAG, "Battery level " + s + " " + i);
                mTextViewFW.append(msg + "\n");
            }

            @Override
            public void hrNotificationReceived(String s,
                                               PolarHrData polarHrData) {
                if (mPlaying) {
                    Log.d(TAG,
                            "HR " + polarHrData.hr + " mPlaying=" + mPlaying);
                    mTextViewHR.setText(String.valueOf(polarHrData.hr));
                }
            }
        });
        mApi.connectToPolarDevice(DEVICE_ID);
        mPlaying = true;
    }

    @Override
    protected void onPause() {
        Log.v(TAG, this.getClass().getSimpleName() + " onPause");
        super.onPause();
    }

    @Override
    public void onResume() {
        Log.v(TAG, this.getClass().getSimpleName() + " onResume");
        super.onResume();
        if (mPlotter == null) {
            mPlot.post(new Runnable() {
                @Override
                public void run() {
                    mPlotter = new Plotter(ECGActivity.this, POINTS_TO_PLOT,
                            "ECG", Color.RED, false);
                    mPlotter.setListener(ECGActivity.this);
                    setupPLot();
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.mMenu = menu;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.pause:
                if (mPlaying) {
                    // Turn if off
                    mPlaying = false;
                    mMenu.getItem(0).setIcon(ResourcesCompat.
                            getDrawable(getResources(),
                                    R.drawable.ic_play_arrow_white_36dp, null));
                    mMenu.getItem(0).setTitle("Start");
                } else {
                    // Turn it on
                    mPlaying = true;
                    // Clear the plot
                    mPlotter.clear();
                    if (mEcgDisposable == null) {
                        // Turns it on
                        streamECG();
                    }
                    mMenu.getItem(0).setIcon(ResourcesCompat.
                            getDrawable(getResources(),
                                    R.drawable.ic_pause_white_36dp, null));
                    mMenu.getItem(0).setTitle("Pause");
                }
                return true;
            case R.id.stop:
                mPlaying = false;
                if (mEcgDisposable != null) {
                    // Turns it off
                    streamECG();
                }
                mMenu.getItem(0).setIcon(ResourcesCompat.
                        getDrawable(getResources(),
                                R.drawable.ic_play_arrow_white_36dp, null));
                mMenu.getItem(0).setTitle("Start");
                return true;
        }
        return false;
    }

    /**
     * This is necessary to handle orientation changes and keep the plot. It
     * needs<br><br>
     * android:configChanges="orientation|keyboardHidden|screenSize"<br><br>
     * in AndroidManifest for the Activity.  With this set onPause and
     * onResume are not called, only this.  Otherwise orientation changes
     * cause it to start over with onCreate.  <br><br>
     * The screen orientation changes have not been made yet, so anything
     * relying on them must be done later.
     *
     * @param newConfig The new configuration.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.v(TAG, this.getClass().getSimpleName() +
                    " onConfigurationChanged: Landscape");
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.v(TAG, this.getClass().getSimpleName() +
                    " onConfigurationChanged: Portrait");
        }

        // Cannot do this now as the screen changes have only been dispatched
        mPlot.post(new Runnable() {
            @Override
            public void run() {
                setupPLot();
                mPlotter.updatePlot(mPlot);
            }
        });
    }


    @Override
    public void onDestroy() {
        Log.v(TAG, this.getClass().getSimpleName() + " onDestroy");
        super.onDestroy();
        mApi.shutDown();
    }


    /**
     * Sets the plot parameters, calculating the range boundaries to have the
     * same grid as the domain.  Calls update when done.
     */
    public void setupPLot() {
//        DisplayMetrics displayMetrics = this.getResources()
//        .getDisplayMetrics();
//        float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
//        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
//        Log.d(TAG, "dpWidth=" + dpWidth + " dpHeight=" + dpHeight);
//        Log.d(TAG, "widthPixels=" + displayMetrics.widthPixels +
//                " heightPixels=" + displayMetrics.heightPixels);
//        Log.d(TAG, "density=" + displayMetrics.density);
//        Log.d(TAG, "10dp=" + 10 / displayMetrics.density + " pixels");
//
//        Log.d(TAG, "plotWidth=" + mPlot.getWidth() +
//                " plotHeight=" + mPlot.getHeight());
//
//        RectF widgetRect = mPlot.getGraph().getWidgetDimensions().canvasRect;
//        Log.d(TAG,
//                "widgetRect LRTB=" + widgetRect.left + "," + widgetRect
//                .right +
//                        "," + widgetRect.top + "," + widgetRect.bottom);
//        Log.d(TAG, "widgetRect width=" + (widgetRect.right - widgetRect
//        .left) +
//                " height=" + (widgetRect.bottom - widgetRect.top));
//
//        RectF gridRect = mPlot.getGraph().getGridRect();
//        Log.d(TAG, "gridRect LRTB=" + gridRect.left + "," + gridRect.right +
//                "," + gridRect.top + "," + gridRect.bottom);
//        Log.d(TAG, "gridRect width=" + (gridRect.right - gridRect.left) +
//                " height=" + (gridRect.bottom - gridRect.top));

        // Calculate the range limits to make the blocks be square
        // Using .5 mV and POINTS_TO_PLOT / 130 Hz for total grid size
        // rMax is half the total, rMax at top and -rMax at bottom
        RectF gridRect = mPlot.getGraph().getGridRect();
        double rMax =
                .25 * (gridRect.bottom - gridRect.top) * POINTS_TO_PLOT /
                        26 / (gridRect.right - gridRect.left);
        // Round it to one decimal point
        rMax = Math.round(rMax * 10) / 10.;

        mPlot.addSeries(mPlotter.getSeries(), mPlotter.getFormatter());
        mPlot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED);
        // Set the range block to be .1 so a large block will be .5
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, .1);
        mPlot.setLinesPerRangeLabel(5);
        mPlot.setDomainBoundaries(0, POINTS_TO_PLOT, BoundaryMode.FIXED);
        // Set the domain block to be .2 * 26 so large block will be 26 samples
        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL,
                .2 * 26);
        mPlot.setLinesPerDomainLabel(5);

        mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.NONE);

        // These don't work
//        mPlot.getTitle().position(0, HorizontalPositioning
//        .ABSOLUTE_FROM_RIGHT,
//                0,    VerticalPositioning.ABSOLUTE_FROM_TOP, Anchor
//                .RIGHT_TOP);
//        mPlot.getTitle().setAnchor(Anchor.BOTTOM_MIDDLE);
//        mPlot.getTitle().setMarginTop(200);
//        mPlot.getTitle().setPaddingTop(200);

//        mPlot.setRenderMode(Plot.RenderMode.USE_BACKGROUND_THREAD);

        update();
    }

    /**
     * Toggles streaming for ECG.
     */
    public void streamECG() {
        if (mEcgDisposable == null) {
            mEcgDisposable =
                    mApi.requestEcgSettings(DEVICE_ID).toFlowable().flatMap(new Function<PolarSensorSetting, Publisher<PolarEcgData>>() {
                        @Override
                        public Publisher<PolarEcgData> apply(PolarSensorSetting sensorSetting) {
//                            Log.d(TAG, "mEcgDisposable requestEcgSettings " +
//                                    "apply");
//                            Log.d(TAG,
//                                    "sampleRate=" + sensorSetting
//                                    .maxSettings().settings.
//                                            get(PolarSensorSetting
//                                            .SettingType.SAMPLE_RATE) +
//                                            " resolution=" + sensorSetting
//                                            .maxSettings().settings.
//                                            get(PolarSensorSetting
//                                            .SettingType.RESOLUTION) +
//                                            " range=" + sensorSetting
//                                            .maxSettings().settings.
//                                            get(PolarSensorSetting
//                                            .SettingType.RANGE));
                            return mApi.startEcgStreaming(DEVICE_ID,
                                    sensorSetting.maxSettings());
                        }
                    }).observeOn(AndroidSchedulers.mainThread()).subscribe(
                            new Consumer<PolarEcgData>() {
                                @Override
                                public void accept(PolarEcgData polarEcgData) {
//                                    double deltaT =
//                                            .000000001 * (polarEcgData
//                                            .timeStamp - time0);
//                                    time0 = polarEcgData.timeStamp;
//                                    int nSamples = polarEcgData.samples
//                                    .size();
//                                    double samplesPerSec = nSamples / deltaT;
//                                    Log.d(TAG,
//                                            "ecg update:" +
//                                                    " deltaT=" + String
//                                                    .format("%.3f", deltaT) +
//                                                    " nSamples=" + nSamples +
//                                                    " samplesPerSec=" +
//                                                    String.format("%.3f",
//                                                    samplesPerSec));

                                    long now = new Date().getTime();
                                    long ts =
                                            polarEcgData.timeStamp / 1000000;
                                    Log.d(TAG, "timeOffset=" + (now - ts) +
                                            " " + new Date(now - ts));
                                    if (mPlaying) {
                                        mPlotter.addValues(mPlot, polarEcgData);
                                    }
                                }
                            },
                            new Consumer<Throwable>() {
                                @Override
                                public void accept(Throwable throwable) {
                                    Log.e(TAG,
                                            "" + throwable.getLocalizedMessage());
                                    mEcgDisposable = null;
                                }
                            },
                            new Action() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "ECG streaming complete");
                                }
                            }
                    );
        } else {
            // NOTE stops streaming if it is "running"
            mEcgDisposable.dispose();
            mEcgDisposable = null;
        }
    }

    @Override
    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                Log.d(TAG, "update (UI) thread: " + Thread.currentThread()
//                .getName());
                mPlot.redraw();
            }
        });
    }
}
