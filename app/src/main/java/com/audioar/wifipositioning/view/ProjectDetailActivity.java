package com.audioar.wifipositioning.view;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.audioar.wifipositioning.R;
import com.audioar.wifipositioning.model.AccessPoint;
import com.audioar.wifipositioning.model.Project;
import com.audioar.wifipositioning.model.ReferencePoint;
import com.audioar.wifipositioning.view.viewfrags.APSection;
import com.audioar.wifipositioning.view.viewfrags.RPSection;
import com.audioar.wifipositioning.view.viewfrags.RecyclerItemClickListener;
import com.onlylemi.mapview.library.MapView;
import com.onlylemi.mapview.library.layer.MarkLayer;
import com.onlylemi.mapview.library.layer.MapBaseLayer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters;
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter;
import io.realm.Realm;

public class ProjectDetailActivity extends AppCompatActivity implements View.OnClickListener, RecyclerItemClickListener.OnItemClickListener {

    static private String projectId = "AudioARProject";
    static private String desc = "Description : AudioARProject";

    private RecyclerView pointRV;
    private Button btnAddAp, btnAddRp, btnWIFI;
    private Project project;
    private SectionedRecyclerViewAdapter sectionAdapter = new SectionedRecyclerViewAdapter();
    private RPSection rpSec;
    private APSection apSec;
    private LinearLayoutManager layoutManager;

    // MapView for Android 替代百度地图
    private MapView mapView;
    private MarkLayer markLayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_detail);

        initMapView();

        final Realm realm = Realm.getDefaultInstance();
        if (realm.where(Project.class).equalTo("id", projectId).count() == 0) {
            realm.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(Realm bgRealm) {
                    project = bgRealm.createObject(Project.class, projectId);
                    project.setName(projectId);
                    project.setDesc(desc);
                    project.setCreatedAt(new Date());
                }
            }, new Realm.Transaction.OnSuccess() {
                @Override
                public void onSuccess() {
                    project = realm.where(Project.class).equalTo("id", projectId).findFirst();
                    initUI();
                    loadMapAndMarkers();
                }
            }, new Realm.Transaction.OnError() {
                @Override
                public void onError(Throwable error) {
                    Toast.makeText(ProjectDetailActivity.this, "创建项目失败", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            project = realm.where(Project.class).equalTo("id", projectId).findFirst();
            initUI();
            loadMapAndMarkers();
        }
    }

    private void initMapView() {
        mapView = findViewById(R.id.mapview);

        // 加载地图图片（从assets目录）
        try {
            InputStream is = getAssets().open("floor_plan.png");
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            mapView.loadMap(bitmap);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载地图图片失败", Toast.LENGTH_SHORT).show();
        }

        // 获取标记图层
        markLayer = mapView.getMarkLayer();

        // 启用所有手势
        mapView.setRotateEnabled(true);
        mapView.setScaleEnabled(true);
        mapView.setScrollEnabled(true);
    }

    private void loadMapAndMarkers() {
        if (project == null || project.getRps() == null) return;

        // 清除现有标记
        markLayer.getMarks().clear();

        // 添加参考点标记
        Bitmap markBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pin);
        for (ReferencePoint rp : project.getRps()) {
            com.onlylemi.mapview.library.entity.MapMark mark = new com.onlylemi.mapview.library.entity.MapMark();
            mark.setId(rp.getId());
            mark.setName(rp.getName());
            mark.setX((float) rp.getX());
            mark.setY((float) rp.getY());
            mark.setMarkImg(markBitmap);
            markLayer.addMark(mark);
        }

        mapView.refresh();
    }

    private void initUI() {
        pointRV = findViewById(R.id.rv_points);
        btnAddAp = findViewById(R.id.btn_add_ap);
        btnAddAp.setOnClickListener(this);

        btnAddRp = findViewById(R.id.btn_add_rp);
        btnAddRp.setOnClickListener(this);

        btnWIFI = findViewById(R.id.btn_wifi);
        btnWIFI.setOnClickListener(this);

        setCounts();

        SectionParameters sp = new SectionParameters.Builder(R.layout.item_point_details)
                .headerResourceId(R.layout.item_section_details)
                .build();

        apSec = new APSection(sp);
        rpSec = new RPSection(sp);
        apSec.setAccessPoints(project.getAps());
        rpSec.setReferencePoints(project.getRps());
        sectionAdapter.addSection(apSec);
        sectionAdapter.addSection(rpSec);

        layoutManager = new LinearLayoutManager(this);
        pointRV.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        pointRV.setLayoutManager(layoutManager);
        pointRV.setAdapter(sectionAdapter);
        pointRV.addOnItemTouchListener(new RecyclerItemClickListener(this, pointRV, this));
    }

    private void setCounts() {
        String name = project.getName();
        int apCount = project.getAps().size();
        int rpCount = project.getRps().size();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(name);
        }
        if (apCount > 0) {
            ((TextView)findViewById(R.id.btn_add_ap)).setText("（"+apCount + "）接入点");
        }
        if (rpCount > 0) {
            ((TextView)findViewById(R.id.btn_add_rp)).setText("（"+rpCount + "）参考点");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sectionAdapter.notifyDataSetChanged();
        setCounts();
        loadMapAndMarkers(); // 刷新地图标记
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_add_ap) {
            startAddAPActivity("");
        } else if (id == R.id.btn_add_rp) {
            startAddRPActivity(null);
        } else if (id == R.id.btn_wifi) {
            startPositioningActivity();
        } else if (id == R.id.btn_pref) {
            startPrefActivity();
        } else if (id == R.id.btn_debug) {
            startDebugActivity();
        } else if (id == R.id.btn_remove_all) {
            Realm realm = Realm.getDefaultInstance();
            realm.beginTransaction();
            project.getAps().deleteAllFromRealm();
            project.getRps().deleteAllFromRealm();
            realm.commitTransaction();
            refreshList();
        } else if (id == R.id.btn_pdr) {
            Intent intent = new Intent(this, PDRActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 权限处理逻辑保持不变
    }

    private void startDebugActivity() {
        Intent intent = new Intent(this, AlgorithmDebugActivity.class);
        intent.putExtra("projectId", projectId);
        startActivity(intent);
    }

    private void startPrefActivity() {
        Intent intent = new Intent(this, PrefActivity.class);
        startActivity(intent);
    }

    private void startAddAPActivity(String apId) {
        Intent intent = new Intent(this, SearchWifiAPActivity.class);
        intent.putExtra("projectId", projectId);
        startActivity(intent);
    }

    private void startAddRPActivity(String rpId) {
        Intent intent = new Intent(this, AddReferencePoint.class);
        intent.putExtra("projectId", projectId);
        if (rpId != null) {
            intent.putExtra("rpId", rpId);
        }
        startActivity(intent);
    }

    private void startPositioningActivity() {
        Intent intent = new Intent(this, PositioningActivity.class);
        intent.putExtra("projectId", projectId);
        startActivity(intent);
    }

    @Override
    public void onItemClick(View view, int position) {
        int apsCount = 0;
        if (project.getAps() != null) {
            apsCount = project.getAps().size();
        }
        if (position <= apsCount && position != 0) {
            AccessPoint accessPoint = project.getAps().get(position - 1);
            startAddAPActivity(accessPoint.getId());
        } else if (position > (apsCount + 1)) {
            int rpIndex = position - apsCount - 1 - 1;
            if (rpIndex >= 0 && rpIndex < project.getRps().size()) {
                ReferencePoint referencePoint = project.getRps().get(rpIndex);
                startAddRPActivity(referencePoint.getId());
            }
        }
    }

    @Override
    public void onLongClick(View view, int position) {
        int apsCount = 0;
        if (project.getAps() != null) {
            apsCount = project.getAps().size();
        }
        if (position <= apsCount && position != 0) {
            AccessPoint accessPoint = project.getAps().get(position - 1);
            showDeleteDialog(accessPoint, null);
        } else if (position > (apsCount + 1)) {
            int rpIndex = position - apsCount - 1 - 1;
            if (rpIndex >= 0 && rpIndex < project.getRps().size()) {
                ReferencePoint referencePoint = project.getRps().get(rpIndex);
                showDeleteDialog(null, referencePoint);
            }
        }
    }

    private void showDeleteDialog(final AccessPoint accessPoint, final ReferencePoint referencePoint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_AppCompat_DayNight_Dialog);
        if (accessPoint != null) {
            builder.setTitle("删除该AP");
            builder.setMessage("删除 " + accessPoint.getSsid());
        } else {
            builder.setTitle("删除该RP");
            builder.setMessage("删除 " + referencePoint.getName());
        }

        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Realm realm = Realm.getDefaultInstance();
                realm.executeTransactionAsync(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        if (accessPoint != null) {
                            accessPoint.deleteFromRealm();
                        } else {
                            referencePoint.deleteFromRealm();
                        }
                    }
                }, new Realm.Transaction.OnSuccess() {
                    @Override
                    public void onSuccess() {
                        refreshList();
                    }
                });
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void refreshList() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sectionAdapter.notifyDataSetChanged();
                loadMapAndMarkers();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
}