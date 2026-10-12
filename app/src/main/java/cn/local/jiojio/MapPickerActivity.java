package cn.local.jiojio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** AMap place search and point picker; returns WGS-84 to the mock-location core. */
public final class MapPickerActivity extends Activity {
    private static final String PREFS = "amap_privacy";
    private static final String CONSENT = "consented";
    private static final LatLng DEFAULT_POINT = new LatLng(31.2304, 121.4737);
    private static final int LOCATION_PERMISSION_REQUEST = 41;
    private static final int ACCENT = Color.rgb(20, 112, 97);
    private static final int INK = Color.rgb(31, 42, 54);
    private static final int MUTED = Color.rgb(105, 119, 132);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MapView mapView;
    private AMap map;
    private Marker marker;
    private LocationManager locationManager;
    private LocationListener pendingLocationListener;
    private EditText searchInput;
    private ScrollView resultsScroll;
    private LinearLayout resultsList;
    private LinearLayout resultsCard;
    private TextView selectedNameView, selectedAddressView, coordinates, locationHint;
    private LatLng selected;
    private String selectedName = "当前位置";
    private String selectedAddress = "正在获取当前位置…";
    private boolean initialized;
    private boolean userChangedSelection;
    private boolean forceCurrentCenter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!hasConsent()) {
            showPrivacyNotice();
            return;
        }
        initializeMap(state);
    }

    private boolean hasConsent() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(CONSENT, false);
    }

    private void showPrivacyNotice() {
        String message = "为提供高德地图、地点搜索和当前定位，高德地图 SDK 可能处理设备标识（如 OAID）、应用信息、设备与系统信息、IP/网络/Wi-Fi 信息、GNSS/传感器信息、地图坐标和搜索词。" +
                "工具合集会在你授权后读取当前位置，仅用于地图初始居中和选点，不在后台持续定位。详情请阅读高德开放平台隐私政策。";
        new AlertDialog.Builder(this).setTitle("地图与定位服务说明").setMessage(message)
                .setNeutralButton("查看隐私政策", (dialog, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://lbs.amap.com/pages/privacy/"))); }
                    catch (Exception e) { Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show(); }
                    showPrivacyNotice();
                })
                .setNegativeButton("暂不同意", (dialog, which) -> finish())
                .setPositiveButton("同意并打开地图", (dialog, which) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(CONSENT, true).apply();
                    initializeMap(null);
                }).setOnCancelListener(dialog -> finish()).show();
    }

    private void initializeMap(Bundle state) {
        if (initialized) return;
        initialized = true;
        try {
            String key = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA)
                    .metaData.getString("com.amap.api.v2.apikey", "");
            if (key.trim().isEmpty() || key.equals("CONFIGURE_AMAP_KEY")) {
                new AlertDialog.Builder(this).setTitle("尚未配置高德 Key")
                        .setMessage("请在高德开放平台创建 Android Key，并用 -PAMAP_API_KEY=你的Key 重新构建。")
                        .setPositiveButton("知道了", (dialog, which) -> finish()).setOnCancelListener(dialog -> finish()).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "读取高德 Key 配置失败", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        MapsInitializer.updatePrivacyShow(getApplicationContext(), true, true);
        MapsInitializer.updatePrivacyAgree(getApplicationContext(), true);
        ServiceSettings.updatePrivacyShow(getApplicationContext(), true, true);
        ServiceSettings.updatePrivacyAgree(getApplicationContext(), true);

        FrameLayout mapArea = new FrameLayout(this);
        mapView = new MapView(this);
        mapArea.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        mapArea.addView(buildSearchOverlay(), topOverlayParams());

        Button locate = new Button(this);
        locate.setText("当前位置");
        locate.setAllCaps(false);
        locate.setTextColor(ACCENT);
        locate.setBackground(roundRect(Color.WHITE, 18));
        locate.setElevation(dp(5));
        locate.setContentDescription("定位到设备当前位置");
        locate.setOnClickListener(v -> { forceCurrentCenter = true; requestCurrentLocation(true); });
        FrameLayout.LayoutParams locateParams = new FrameLayout.LayoutParams(-2, dp(48), Gravity.END | Gravity.BOTTOM);
        locateParams.setMargins(0, 0, dp(14), dp(14));
        mapArea.addView(locate, locateParams);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 250));
        root.addView(mapArea, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(buildSelectionCard(), new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        mapView.onCreate(state);
        map = mapView.getMap();
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        selected = initialFallbackPoint();
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(selected, 15f));
        addOrMoveMarker(selected);
        map.setOnMapClickListener(point -> {
            userChangedSelection = true;
            selectedName = "地图选点";
            selectedAddress = "已手动选择地图坐标";
            selected = point;
            addOrMoveMarker(point);
            updateSelectionCard();
        });
        map.setOnMarkerDragListener(new AMap.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker value) { userChangedSelection = true; }
            @Override public void onMarkerDrag(Marker value) { selected = value.getPosition(); updateCoordinates(); }
            @Override public void onMarkerDragEnd(Marker value) {
                selected = value.getPosition();
                selectedName = "地图选点";
                selectedAddress = "已手动调整地图标记";
                updateSelectionCard();
            }
        });
        updateSelectionCard();
        requestCurrentLocation(false);
    }

    private View buildSearchOverlay() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(8));
        card.setBackground(roundRect(Color.WHITE, 18));
        card.setElevation(dp(6));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setTextColor(INK);
        searchInput.setHintTextColor(MUTED);
        searchInput.setHint("搜索地点、地址或商户");
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setPadding(dp(12), 0, dp(8), 0);
        searchInput.setBackground(roundRect(Color.rgb(244, 247, 248), 12));
        row.addView(searchInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button searchButton = new Button(this);
        searchButton.setText("搜索");
        searchButton.setAllCaps(false);
        searchButton.setTextColor(Color.WHITE);
        searchButton.setBackground(roundRect(ACCENT, 12));
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(72), dp(46));
        searchButtonParams.leftMargin = dp(8);
        row.addView(searchButton, searchButtonParams);
        card.addView(row);

        resultsCard = new LinearLayout(this);
        resultsCard.setOrientation(LinearLayout.VERTICAL);
        resultsCard.setVisibility(View.GONE);
        resultsScroll = new ScrollView(this);
        resultsScroll.setFillViewport(true);
        resultsScroll.setVisibility(View.GONE);
        resultsScroll.addView(resultsCard);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(220));
        scrollParams.topMargin = dp(6);
        card.addView(resultsScroll, scrollParams);
        resultsList = resultsCard;

        searchButton.setOnClickListener(v -> searchPlaces());
        searchInput.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                searchPlaces(); return true;
            }
            return false;
        });
        return card;
    }

    private FrameLayout.LayoutParams topOverlayParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        params.setMargins(dp(12), dp(12), dp(12), 0);
        return params;
    }

    private View buildSelectionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setBackground(roundTopCard(Color.WHITE, 20));
        card.setElevation(dp(8));

        selectedNameView = new TextView(this);
        selectedNameView.setTextSize(17);
        selectedNameView.setTextColor(INK);
        selectedNameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(selectedNameView, new LinearLayout.LayoutParams(-1, -2));

        selectedAddressView = new TextView(this);
        selectedAddressView.setTextSize(13);
        selectedAddressView.setTextColor(MUTED);
        selectedAddressView.setMaxLines(2);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, -2);
        addressParams.topMargin = dp(4);
        card.addView(selectedAddressView, addressParams);

        coordinates = new TextView(this);
        coordinates.setTextSize(12);
        coordinates.setTextColor(MUTED);
        LinearLayout.LayoutParams coordinateParams = new LinearLayout.LayoutParams(-1, -2);
        coordinateParams.topMargin = dp(6);
        card.addView(coordinates, coordinateParams);

        locationHint = new TextView(this);
        locationHint.setTextSize(12);
        locationHint.setTextColor(ACCENT);
        locationHint.setVisibility(View.GONE);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(4);
        card.addView(locationHint, hintParams);

        Button confirm = new Button(this);
        confirm.setText("使用此位置");
        confirm.setAllCaps(false);
        confirm.setTextColor(Color.WHITE);
        confirm.setTextSize(16);
        confirm.setBackground(roundRect(ACCENT, 14));
        confirm.setOnClickListener(v -> returnSelection());
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(-1, dp(50));
        confirmParams.topMargin = dp(12);
        card.addView(confirm, confirmParams);
        return card;
    }

    private void searchPlaces() {
        String keyword = searchInput.getText().toString().trim();
        if (keyword.isEmpty()) {
            searchInput.setError("请输入地点名称");
            return;
        }
        hideKeyboard();
        resultsScroll.setVisibility(View.VISIBLE);
        resultsCard.setVisibility(View.VISIBLE);
        resultsCard.removeAllViews();
        addResultMessage("正在搜索地点…", MUTED);
        PoiSearch.Query query = new PoiSearch.Query(keyword, "", "");
        query.setPageSize(15);
        query.setPageNum(0);
        query.setCityLimit(false);
        if (selected != null) {
            query.setLocation(new LatLonPoint(selected.latitude, selected.longitude));
            query.setDistanceSort(true);
        }
        try {
            PoiSearch search = new PoiSearch(this, query);
            search.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
                @Override public void onPoiSearched(PoiResult result, int code) {
                    if (isFinishing()) return;
                    resultsCard.removeAllViews();
                    if (code != 1000 || result == null) {
                        addResultMessage("搜索失败，请检查网络或稍后重试（" + code + "）", MUTED);
                        return;
                    }
                    ArrayList<PoiItem> pois = result.getPois();
                    if (pois == null || pois.isEmpty()) {
                        addResultMessage("没有找到相关地点，试试缩短关键词", MUTED);
                        return;
                    }
                    for (PoiItem poi : pois) addPoiRow(poi);
                }
                @Override public void onPoiItemSearched(PoiItem item, int code) {}
            });
            search.searchPOIAsyn();
        } catch (Exception e) {
            resultsCard.removeAllViews();
            addResultMessage("暂时无法搜索地点，请稍后重试", MUTED);
        }
    }

    private void addPoiRow(PoiItem poi) {
        LatLonPoint point = poi.getLatLonPoint();
        if (point == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(8), dp(6), dp(8));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        TextView title = new TextView(this);
        title.setText(poi.getTitle());
        title.setTextSize(15);
        title.setTextColor(INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title);
        String address = joinAddress(poi);
        TextView subtitle = new TextView(this);
        subtitle.setText(address.isEmpty() ? "高德地图地点" : address);
        subtitle.setTextSize(12);
        subtitle.setTextColor(MUTED);
        subtitle.setMaxLines(1);
        row.addView(subtitle);
        row.setOnClickListener(v -> {
            userChangedSelection = true;
            selected = new LatLng(point.getLatitude(), point.getLongitude());
            selectedName = poi.getTitle();
            selectedAddress = address;
            addOrMoveMarker(selected);
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(selected, 17f));
            resultsScroll.setVisibility(View.GONE);
            resultsCard.setVisibility(View.GONE);
            updateSelectionCard();
        });
        resultsCard.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(232, 236, 239));
        resultsCard.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private String joinAddress(PoiItem poi) {
        StringBuilder text = new StringBuilder();
        if (poi.getCityName() != null) text.append(poi.getCityName());
        if (poi.getAdName() != null && !poi.getAdName().isEmpty() && !poi.getAdName().equals(poi.getCityName())) text.append(poi.getAdName());
        if (poi.getSnippet() != null && !poi.getSnippet().isEmpty()) {
            if (text.length() > 0) text.append(" · ");
            text.append(poi.getSnippet());
        }
        return text.toString();
    }

    private void addResultMessage(String text, int color) {
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextColor(color);
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER_VERTICAL);
        message.setPadding(dp(8), dp(12), dp(8), dp(12));
        resultsCard.addView(message, new LinearLayout.LayoutParams(-1, -2));
    }

    private LatLng initialFallbackPoint() {
        String lat = getIntent().getStringExtra("lat"), lon = getIntent().getStringExtra("lon");
        try {
            if (lat != null && lon != null && !lat.trim().isEmpty() && !lon.trim().isEmpty()) {
                double[] amap = CoordinateTransform.wgs84ToGcj02(Double.parseDouble(lat), Double.parseDouble(lon));
                selectedName = "已填入的坐标";
                selectedAddress = "正在读取设备当前位置…";
                return new LatLng(amap[0], amap[1]);
            }
        } catch (RuntimeException ignored) {}
        return DEFAULT_POINT;
    }

    private void requestCurrentLocation(boolean showStatus) {
        if (map == null) return;
        if (!hasLocationPermission()) {
            if (showStatus && locationHint != null) {
                locationHint.setText("需要定位权限才能跳转到当前位置；也可以直接搜索或拖动地图选点。");
                locationHint.setVisibility(View.VISIBLE);
            }
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        Location cached = newestCachedLocation();
        if (cached != null) applyCurrentLocation(cached, false);

        if (pendingLocationListener != null) removeLocationUpdates();
        pendingLocationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (location != null) applyCurrentLocation(location, true);
                removeLocationUpdates();
            }
        };
        boolean requested = false;
        try {
            if (hasFinePermission() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, pendingLocationListener, Looper.getMainLooper());
                requested = true;
            }
            if (hasCoarsePermission() && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, pendingLocationListener, Looper.getMainLooper());
                requested = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        if (requested) {
            if (locationHint != null) { locationHint.setText("正在获取当前位置…"); locationHint.setVisibility(View.VISIBLE); }
            handler.postDelayed(() -> {
                if (pendingLocationListener != null) {
                    removeLocationUpdates();
                    if (locationHint != null) { locationHint.setText("暂时没有新的定位结果，可以搜索地点或拖动地图选点。"); locationHint.setVisibility(View.VISIBLE); }
                }
            }, 15_000);
        } else if (cached == null && locationHint != null) {
            locationHint.setText("系统定位暂不可用，可以搜索地点或拖动地图选点。");
            locationHint.setVisibility(View.VISIBLE);
        }
    }

    private boolean hasFinePermission() { return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private boolean hasCoarsePermission() { return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private boolean hasLocationPermission() { return hasFinePermission() || hasCoarsePermission(); }

    private Location newestCachedLocation() {
        if (locationManager == null) return null;
        Location newest = null;
        try { if (hasFinePermission()) newest = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
        catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            if (hasCoarsePermission()) {
                Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (network != null && (newest == null || network.getTime() > newest.getTime())) newest = network;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        return newest;
    }

    private void applyCurrentLocation(Location location, boolean fresh) {
        if (location == null || (userChangedSelection && !forceCurrentCenter)) return;
        double[] amap = CoordinateTransform.wgs84ToGcj02(location.getLatitude(), location.getLongitude());
        selected = new LatLng(amap[0], amap[1]);
        selectedName = "当前位置";
        selectedAddress = location.hasAccuracy() ? String.format(Locale.getDefault(), "定位精度约 %.0f 米", location.getAccuracy()) : "设备定位结果";
        addOrMoveMarker(selected);
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(selected, 16f));
        forceCurrentCenter = false;
        updateSelectionCard();
        if (locationHint != null) {
            locationHint.setText(fresh ? "已更新到当前位置" : "已使用最近一次定位");
            locationHint.setVisibility(View.VISIBLE);
        }
    }

    private void removeLocationUpdates() {
        if (pendingLocationListener != null && locationManager != null) {
            try { locationManager.removeUpdates(pendingLocationListener); } catch (SecurityException ignored) {}
            pendingLocationListener = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST) return;
        if (hasLocationPermission()) requestCurrentLocation(false);
        else if (locationHint != null) {
            locationHint.setText("未授予定位权限，可以搜索地点或手动拖动地图选点。");
            locationHint.setVisibility(View.VISIBLE);
        }
    }

    private void addOrMoveMarker(LatLng point) {
        if (map == null) return;
        if (marker == null) marker = map.addMarker(new MarkerOptions().position(point).title("模拟位置").draggable(true));
        else marker.setPosition(point);
    }

    private void updateSelectionCard() {
        if (selectedNameView == null || selectedAddressView == null) return;
        selectedNameView.setText(selectedName);
        selectedAddressView.setText(selectedAddress);
        updateCoordinates();
    }

    private void updateCoordinates() {
        if (coordinates == null || selected == null) return;
        double[] wgs = CoordinateTransform.gcj02ToWgs84(selected.latitude, selected.longitude);
        coordinates.setText(String.format(Locale.US, "WGS-84  %.6f, %.6f", wgs[0], wgs[1]));
    }

    private void returnSelection() {
        if (selected == null) return;
        double[] wgs = CoordinateTransform.gcj02ToWgs84(selected.latitude, selected.longitude);
        Intent result = new Intent().putExtra("lat", String.format(Locale.US, "%.7f", wgs[0]))
                .putExtra("lon", String.format(Locale.US, "%.7f", wgs[1])).putExtra("name", selectedName);
        setResult(RESULT_OK, result);
        finish();
    }

    private void hideKeyboard() {
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    private GradientDrawable roundTopCard(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        float r = dp(radiusDp);
        bg.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});
        return bg;
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { removeLocationUpdates(); if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { removeLocationUpdates(); handler.removeCallbacksAndMessages(null); if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { if (mapView != null) mapView.onSaveInstanceState(out); super.onSaveInstanceState(out); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
}
