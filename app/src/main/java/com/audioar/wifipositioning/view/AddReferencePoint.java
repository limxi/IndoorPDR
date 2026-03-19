package com.audioar.wifipositioning.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.audioar.wifipositioning.R;
import com.audioar.wifipositioning.SharedConstants;
import com.audioar.wifipositioning.Utilities;
import com.audioar.wifipositioning.model.AccessPoint;
import com.audioar.wifipositioning.model.Project;
import com.audioar.wifipositioning.model.ReferencePoint;
import com.audioar.wifipositioning.view.viewfrags.ReferenceReadingsAdapter;
import com.onlylemi.mapview.library.MapView;
import com.onlylemi.mapview.library.entity.MapMark;
import com.onlylemi.mapview.library.layer.MarkLayer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;

public class AddReferencePoint extends AppCompatActivity implements View.OnClickListener {

    private String TAG = "AddReferencePoint";
    private String projectId;

    private RecyclerView rvPoints;
    private LinearLayoutManager layoutManager;
    private EditText etRpName;
    private EditText etRpX, etRpY;
    private Button bnRpSave;

    private ReferenceReadingsAdapter readingsAdapter = new ReferenceReadingsAdapter();
    private List<AccessPoint> apsWithReading = new ArrayList<>();
    private Map<String, List<Integer>> readings = new HashMap<>();
    private Map<String, AccessPoint> aps = new HashMap<>();

    private AvailableAPsReceiver receiverWifi;

    private boolean wifiWasEnabled;
    private WifiManager mainWifi;
    private final Handler handler = new Handler();
    private boolean isCaliberating = false;
    private int readingsCount = 0;
    private boolean isEdit = false;
    private String rpId;
    private ReferencePoint referencePointFromDB;

    private Project project;

    // MapView替代百度地图
    private MapView mapView;
    private MarkLayer markLayer;
    private Bitmap markBitmap;
    private Bitmap newRpBitmap;
    private float currentX = 500f; // 默认初始X
    private float currentY = 500f; // 默认初始Y

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_reference_point);

        projectId = getIntent().getStringExtra("projectId");
        if (projectId == null) {
            Toast.makeText(this, "没有找到项目", Toast.LENGTH_LONG).show();
            finish();
        }

        if (getIntent().getStringExtra("rpId") != null) {
            isEdit = true;
            rpId = getIntent().getStringExtra("rpId");
        }

        initMapView();
        initUI();

        Realm realm = Realm.getDefaultInstance();
        if (isEdit) {
            referencePointFromDB = realm.where(ReferencePoint.class).equalTo("id", rpId).findFirst();
            if (referencePointFromDB == null) {
                Toast.makeText(this, "没有找到参考点", Toast.LENGTH_LONG).show();
                finish();
            }
            RealmList<AccessPoint> readings = referencePointFromDB.getReadings();
            for (AccessPoint ap : readings) {
                readingsAdapter.addAP(ap);
            }
            readingsAdapter.notifyDataSetChanged();
            etRpName.setText(referencePointFromDB.getName());
            etRpX.setText(String.valueOf(referencePointFromDB.getX()));
            etRpY.setText(String.valueOf(referencePointFromDB.getY()));

            // 更新标记位置
            currentX = (float) referencePointFromDB.getX();
            currentY = (float) referencePointFromDB.getY();
            updateMarkerPosition();

            project = realm.where(Project.class).equalTo("id", projectId).findFirst();

        } else {
            mainWifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            receiverWifi = new AvailableAPsReceiver();
            wifiWasEnabled = mainWifi.isWifiEnabled();
            project = realm.where(Project.class).equalTo("id", projectId).findFirst();
            RealmList<AccessPoint> points = project.getAps();
            for (AccessPoint accessPoint : points) {
                aps.put(accessPoint.getMac_address(), accessPoint);
            }
            if (aps.isEmpty()) {
                Toast.makeText(this, "没有找到接入点", Toast.LENGTH_SHORT).show();
            }
            if (!Utilities.isLocationEnabled(this)) {
                Toast.makeText(this, "请将定位功能打开", Toast.LENGTH_SHORT).show();
            }
        }
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

        // 初始化标记图层
        markLayer = mapView.getMarkLayer();
        markBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pin);
        newRpBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.icon_gcoding);

        // 设置点击监听
        mapView.setOnMapClickListener((x, y) -> {
            currentX = x;
            currentY = y;
            etRpX.setText(String.valueOf(x));
            etRpY.setText(String.valueOf(y));
            updateMarkerPosition();
        });
    }

    private void updateMarkerPosition() {
        if (markLayer == null) return;

        // 清除之前的标记
        markLayer.getMarks().clear();

        // 添加其他参考点
        if (project != null && project.getRps() != null) {
            for (ReferencePoint rp : project.getRps()) {
                if (isEdit && rp.getId().equals(rpId)) continue;
                MapMark mark = new MapMark();
                mark.setId(rp.getId());
                mark.setName(rp.getName());
                mark.setX((float) rp.getX());
                mark.setY((float) rp.getY());
                mark.setMarkImg(markBitmap);
                markLayer.addMark(mark);
            }
        }

        // 添加当前编辑的标记
        MapMark currentMark = new MapMark();
        currentMark.setId("current");
        currentMark.setName("新参考点");
        currentMark.setX(currentX);
        currentMark.setY(currentY);
        currentMark.setMarkImg(newRpBitmap);
        markLayer.addMark(currentMark);

        mapView.refresh();
    }

    private void initUI() {
        layoutManager = new LinearLayoutManager(this);
        rvPoints = findViewById(R.id.rv_points);
        rvPoints.setLayoutManager(layoutManager);
        rvPoints.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvPoints.setAdapter(readingsAdapter);

        bnRpSave = findViewById(R.id.bn_rp_save);
        bnRpSave.setOnClickListener(this);

        if (!isEdit) {
            bnRpSave.setEnabled(false);
            bnRpSave.setText("收集数据中...");
        } else {
            bnRpSave.setEnabled(true);
            bnRpSave.setText("保存参考点");
        }

        etRpName = findViewById(R.id.et_rp_name);
        etRpX = findViewById(R.id.et_rp_x);
        etRpY = findViewById(R.id.et_rp_y);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isEdit) {
            registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            if (!isCaliberating) {
                isCaliberating = true;
                refresh();
            }
        }
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isEdit) {
            unregisterReceiver(receiverWifi);
            isCaliberating = false;
        }
        if (mapView != null) {
            mapView.onPause();
        }
    }

    public void refresh() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mainWifi.startScan();
                if (readingsCount < SharedConstants.READINGS_BATCH) {
                    refresh();
                } else {
                    caliberationCompleted();
                }
            }
        }, SharedConstants.FETCH_INTERVAL);
    }

    private void caliberationCompleted() {
        isCaliberating = false;
        for (Map.Entry<String, List<Integer>> entry : readings.entrySet()) {
            List<Integer> readingsOfAMac = entry.getValue();
            Double mean = calculateMeanValue(readingsOfAMac);
            AccessPoint accessPoint = aps.get(entry.getKey());
            AccessPoint updatedPoint = new AccessPoint(accessPoint);
            updatedPoint.setMeanRss(mean);
            apsWithReading.add(updatedPoint);
        }
        readingsAdapter.setReadings(apsWithReading);
        readingsAdapter.notifyDataSetChanged();
        bnRpSave.setEnabled(true);
        bnRpSave.setText("保存");
    }

    private Double calculateMeanValue(List<Integer> readings) {
        if (readings.isEmpty()) {
            return 0.0d;
        }
        Integer sum = 0;
        for (Integer integer : readings) {
            sum = sum + integer;
        }
        return Double.valueOf(sum) / Double.valueOf(readings.size());
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == bnRpSave.getId()) {
            if (!isEdit) {
                saveNewReferencePoint();
            } else {
                updateReferencePoint();
            }
        }
    }

    private void saveNewReferencePoint() {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();

        ReferencePoint referencePoint = new ReferencePoint();
        referencePoint = setValues(referencePoint);
        referencePoint.setCreatedAt(Calendar.getInstance().getTime());
        referencePoint.setDescription("");

        if (referencePoint.getReadings() == null) {
            RealmList<AccessPoint> readings = new RealmList<>();
            readings.addAll(apsWithReading);
            referencePoint.setReadings(readings);
        } else {
            referencePoint.getReadings().addAll(apsWithReading);
        }

        referencePoint.setId(UUID.randomUUID().toString());

        Project project = realm.where(Project.class).equalTo("id", projectId).findFirst();
        if (project.getRps() == null) {
            RealmList<ReferencePoint> points = new RealmList<>();
            points.add(referencePoint);
            project.setRps(points);
        } else {
            project.getRps().add(referencePoint);
        }

        realm.commitTransaction();
        Toast.makeText(this, "参考点已添加", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateReferencePoint() {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        referencePointFromDB = setValues(referencePointFromDB);
        realm.commitTransaction();
        Toast.makeText(this, "参考点已更新", Toast.LENGTH_SHORT).show();
        finish();
    }

    private ReferencePoint setValues(ReferencePoint referencePoint) {
        String x = etRpX.getText().toString();
        String y = etRpY.getText().toString();

        if (TextUtils.isEmpty(x)) {
            referencePoint.setX(currentX);
        } else {
            referencePoint.setX(Double.valueOf(x));
        }

        if (TextUtils.isEmpty(y)) {
            referencePoint.setY(currentY);
        } else {
            referencePoint.setY(Double.valueOf(y));
        }

        referencePoint.setLocId(referencePoint.getX() + " " + referencePoint.getY());
        referencePoint.setName(etRpName.getText().toString());
        return referencePoint;
    }

    class AvailableAPsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> scanResults = mainWifi.getScanResults();
            ++readingsCount;
            for (Map.Entry<String, AccessPoint> entry : aps.entrySet()) {
                String apMac = entry.getKey();
                boolean found = false;
                for (ScanResult scanResult : scanResults) {
                    if (entry.getKey().equals(scanResult.BSSID)) {
                        checkAndAddApRSS(apMac, scanResult.level);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    checkAndAddApRSS(apMac, SharedConstants.NaN.intValue());
                }
            }
        }
    }

    private void checkAndAddApRSS(String apMac, Integer level) {
        if (readings.containsKey(apMac)) {
            List<Integer> integers = readings.get(apMac);
            integers.add(level);
        } else {
            List<Integer> integers = new ArrayList<>();
            integers.add(level);
            readings.put(apMac, integers);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!wifiWasEnabled && !isEdit) {
            mainWifi.setWifiEnabled(false);
        }
        if (mapView != null) {
            mapView.onDestroy();
        }
    }
}