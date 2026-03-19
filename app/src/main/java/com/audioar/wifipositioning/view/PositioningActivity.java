package com.audioar.wifipositioning.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.audioar.wifipositioning.CoreAlgorithm;
import com.audioar.wifipositioning.R;
import com.audioar.wifipositioning.Utilities;
import com.audioar.wifipositioning.WifiService;
import com.audioar.wifipositioning.model.LocationWithNearbyPlaces;
import com.audioar.wifipositioning.model.Project;
import com.audioar.wifipositioning.model.ReferencePoint;
import com.audioar.wifipositioning.model.WifiData;
import com.audioar.wifipositioning.SharedConstants;
import com.onlylemi.mapview.library.MapView;
import com.onlylemi.mapview.library.entity.MapMark;
import com.onlylemi.mapview.library.layer.LocationLayer;
import com.onlylemi.mapview.library.layer.MarkLayer;

import java.io.IOException;
import java.io.InputStream;

import io.realm.Realm;

public class PositioningActivity extends AppCompatActivity {

    private WifiData mWifiData;
    private String projectId, defaultAlgo;
    private Project project;
    private MainActivityReceiver mReceiver = new MainActivityReceiver();
    private Intent wifiServiceIntent;
    private TextView tvLocation;

    // MapView替代百度地图
    private MapView mapView;
    private LocationLayer locationLayer;
    private MarkLayer markLayer;
    private Bitmap markBitmap;
    private Bitmap locationBitmap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_positioning);

        mWifiData = null;

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver,
                new IntentFilter(SharedConstants.INTENT_FILTER));

        wifiServiceIntent = new Intent(this, WifiService.class);
        startService(wifiServiceIntent);

        mWifiData = (WifiData) getLastNonConfigurationInstance();

        defaultAlgo = Utilities.getDefaultAlgo(this);
        projectId = getIntent().getStringExtra("projectId");

        Realm realm = Realm.getDefaultInstance();
        project = realm.where(Project.class).equalTo("id", projectId).findFirst();

        initMapView();
    }

    private void initMapView() {
        mapView = findViewById(R.id.mapview);

        // 加载地图图片
        try {
            InputStream is = getAssets().open("floor_plan.png");
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            mapView.loadMap(bitmap);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载地图图片失败", Toast.LENGTH_SHORT).show();
            return;
        }

        // 初始化标记
        markBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pin);
        locationBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.blue_dot);

        // 获取图层
        markLayer = mapView.getMarkLayer();
        locationLayer = mapView.getLocationLayer();

        // 设置定位图标
        locationLayer.setLocationMarker(locationBitmap);

        // 绘制参考点
        if (project != null && project.getRps() != null) {
            for (ReferencePoint rp : project.getRps()) {
                MapMark mark = new MapMark();
                mark.setId(rp.getId());
                mark.setName(rp.getName());
                mark.setX((float) rp.getX());
                mark.setY((float) rp.getY());
                mark.setMarkImg(markBitmap);
                markLayer.addMark(mark);
            }
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return mWifiData;
    }

    public class MainActivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mWifiData = intent.getParcelableExtra(SharedConstants.WIFI_DATA);

            if (mWifiData != null) {
                LocationWithNearbyPlaces loc = CoreAlgorithm.processingAlgorithms(
                        mWifiData.getNetworks(), project, Integer.parseInt(defaultAlgo));

                if (loc == null) {
                    Toast.makeText(PositioningActivity.this, "定位失败", Toast.LENGTH_SHORT).show();
                } else {
                    String location = loc.getLocation();
                    String[] split = location.split(" ");
                    try {
                        float x = Float.parseFloat(split[0]);
                        float y = Float.parseFloat(split[1]);

                        // 更新定位点
                        if (locationLayer != null) {
                            locationLayer.updateLocation(x, y, 0); // 0表示楼层
                            mapView.refresh();
                        }

                        tvLocation = findViewById(R.id.tv_location);
                        tvLocation.setText("当前位置: (" + x + ", " + y + ")");
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        stopService(wifiServiceIntent);
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}