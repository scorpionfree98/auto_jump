package cn.local.jiojio;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.List;
import java.util.Map;

/** Exercises the real app service and stores on an Android device/emulator without UI automation. */
public final class CoreRuntimeInstrumentation extends Instrumentation {
    private static int checksPassed;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        checksPassed = 0;
        Bundle result = new Bundle();
        Context context = getTargetContext();
        if (!"cn.local.toolkit.runtime".equals(context.getPackageName())
                || !(android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.HARDWARE.contains("goldfish"))) {
            result.putString("stream", "FAIL: toolbox regression requires an isolated emulator target\n");
            finish(Activity.RESULT_CANCELED, result);
            return;
        }
        long now = System.currentTimeMillis();
        double offset = (now % 100000) / 1000000.0;
        Position favorite = new Position("Docker VM favorite", -80.0 - offset, 170.0 + offset, now, "Regression place");
        Position target = new Position("Docker VM mock", 70.0 + offset, -170.0 + offset, now);
        PositionStore store = new PositionStore(context);
        android.content.SharedPreferences positions = context.getSharedPreferences("positions", Context.MODE_PRIVATE);
        String priorFavorites = positions.getString("favorites", "[]");
        String priorHistory = positions.getString("history", "[]");
        android.content.SharedPreferences rulesPrefs = context.getSharedPreferences("jump_config_sp", Context.MODE_PRIVATE);
        String priorRules = rulesPrefs.getString("rule_list", null);
        boolean serviceStarted = false;
        int resultCode = Activity.RESULT_OK;
        try {
            check(MockLocationService.authorized(context), "Select this app as mock-location app before running test");

            com.autojump.app.data.RuleRepository repository = new com.autojump.app.data.RuleRepository(context);
            com.autojump.app.bean.Rule rule = new com.autojump.app.bean.Rule("tool-regression", "Tool regression");
            rule.getActions().add(com.autojump.app.domain.RuleAction.deeplink("example://regression"));
            rule.getConditions().add(com.autojump.app.domain.RuleCondition.charging(false));
            String exported = repository.exportRules(java.util.Collections.singletonList(rule));
            List<com.autojump.app.bean.Rule> imported = repository.importRules(exported);
            check(exported.equals(repository.exportRules(imported)), "Rule export/import round trip changed data");
            repository.saveRules(imported);
            check(exported.equals(new com.autojump.app.data.RuleRepository(context).exportRules(
                    new com.autojump.app.data.RuleRepository(context).getRules())), "Saved imported rules did not reload");
            rejects(repository, "{\"schemaVersion\":99,\"rules\":[]}");
            rejects(repository, "not-json");
            check(exported.equals(repository.exportRules(repository.getRules())), "Invalid import mutated saved rules");

            com.autojump.app.bean.Rule extra = new com.autojump.app.bean.Rule("tool-regression-extra", "Extra regression");
            extra.getActions().add(com.autojump.app.domain.RuleAction.deeplink("example://extra"));
            List<com.autojump.app.bean.Rule> appended = repository.getRules();
            appended.addAll(repository.importRules(repository.exportRules(java.util.Collections.singletonList(extra))));
            repository.saveRules(appended);
            check(new com.autojump.app.data.RuleRepository(context).getRules().size() == 2, "Appended import did not reload");
            repository.saveRules(repository.importRules(repository.exportRules(java.util.Collections.singletonList(extra))));
            check(repository.getRules().size() == 1 && "tool-regression-extra".equals(repository.getRules().get(0).getId()),
                    "Replacement import retained old rules");
            repository.saveRules(repository.importRules(exported));
            check(exported.equals(new com.autojump.app.data.RuleRepository(context).exportRules(
                    new com.autojump.app.data.RuleRepository(context).getRules())), "Backup restore did not reload");

            store.add("favorites", favorite);
            List<Position> saved = new PositionStore(context).list("favorites");
            check(contains(saved, favorite.key()), "Favorite did not persist");
            check(saved.stream().anyMatch(p -> p.key().equals(favorite.key()) && p.name.equals(favorite.name)
                    && p.locationName.equals("Regression place")), "Favorite and location names did not persist separately");
            store.add("favorites", new Position("Duplicate coordinate", favorite.latitude, favorite.longitude, favorite.timestamp + 1));
            check(count(new PositionStore(context).list("favorites"), favorite.key()) == 1, "Favorite coordinate was not deduplicated");

            Map<String, String> device = DeviceInspector.snapshot(context);
            check(device.containsKey("品牌 / 型号") && device.containsKey("Android") && device.size() >= 8,
                    "Device information snapshot incomplete");
            SensorProbe sensors = new SensorProbe(context, (name, values, accuracy) -> {});
            try { check(sensors.start() != null, "Sensor discovery failed"); } finally { sensors.close(); }

            Intent start = new Intent(context, MockLocationService.class)
                    .putExtra("name", target.name)
                    .putExtra("lat", Double.toString(target.latitude))
                    .putExtra("lon", Double.toString(target.longitude));
            context.startForegroundService(start);
            serviceStarted = true;
            waitFor(() -> MockLocationService.running, 15000, "Mock-location service did not become active");

            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location actual = null;
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (SystemClock.elapsedRealtime() < deadline) {
                actual = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (actual != null && actual.getLatitude() == target.latitude && actual.getLongitude() == target.longitude) break;
                SystemClock.sleep(250);
            }
            check(actual != null && actual.getLatitude() == target.latitude && actual.getLongitude() == target.longitude,
                    "GPS provider did not return injected coordinates: " + actual);
            check(actual.isFromMockProvider(), "Injected location is not marked as mock");
            waitFor(() -> contains(new PositionStore(context).list("history"), target.key()),
                    5000, "Successful start missing from history");

            context.startService(new Intent(context, MockLocationService.class).setAction("stop"));
            waitFor(() -> !MockLocationService.running, 10000, "Mock-location service did not stop");
            waitFor(() -> MockLocationService.status(context).contains("已停止"), 5000, "Stop status was not reported");

            result.putInt("checks_passed", checksPassed);
            result.putString("stream", "PASS: " + checksPassed + " checks; rule export/import/reload and invalid input protection, favorites persistence/dedup, device snapshot, sensor discovery, GPS mock injection and stop/cleanup.\n");
        } catch (Throwable error) {
            resultCode = Activity.RESULT_CANCELED;
            if (serviceStarted) {
                try { context.startService(new Intent(context, MockLocationService.class).setAction("stop")); }
                catch (Throwable ignored) {}
            }
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
        } finally {
            positions.edit().putString("favorites", priorFavorites).putString("history", priorHistory).commit();
            android.content.SharedPreferences.Editor restore = rulesPrefs.edit();
            if (priorRules == null) restore.remove("rule_list"); else restore.putString("rule_list", priorRules);
            restore.commit();
        }
        finish(resultCode, result);
    }

    private interface Condition { boolean ready(); }
    private static void rejects(com.autojump.app.data.RuleRepository repository, String document) {
        boolean rejected = false;
        try { repository.importRules(document); } catch (RuntimeException expected) { rejected = true; }
        check(rejected, "Invalid import was accepted");
    }
    private static void waitFor(Condition condition, long timeoutMs, String message) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (!condition.ready() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        check(condition.ready(), message);
    }
    private static boolean contains(List<Position> positions, String key) { return count(positions, key) > 0; }
    private static int count(List<Position> positions, String key) {
        int n = 0; for (Position p : positions) if (p.key().equals(key)) n++; return n;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checksPassed++;
    }
}
