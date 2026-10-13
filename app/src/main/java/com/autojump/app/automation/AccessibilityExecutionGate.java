package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Main-thread state machine with injectable Android dependencies. */
public final class AccessibilityExecutionGate {
    public enum Availability { READY, PERMISSION_REQUIRED, NOT_RUNNING }
    public interface Result { void complete(boolean success, String message); }
    public interface Environment {
        boolean connected();
        Availability availability();
        void later(Runnable task, long delayMs);
        void restore(Result result);
        boolean enqueue(Rule rule);
    }
    private final Environment environment;
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private long generation;
    private boolean running;
    public AccessibilityExecutionGate(Environment environment) { this.environment = environment; }

    public void run(Rule rule, boolean needsAccessibility, Result result) {
        if (!needsAccessibility || (!running && environment.connected())) {
            enqueue(rule, result);
            return;
        }
        if (pending.containsKey(rule.getId())) {
            result.complete(false, "指令正在等待自动点击服务，本次触发已合并");
            return;
        }
        if (pending.size() >= 20) {
            result.complete(false, "等待队列已满，请稍后重试");
            return;
        }
        pending.put(rule.getId(), new Pending(rule, result));
        if (running) return;
        running = true;
        poll(++generation, 0);
    }

    private void poll(long token, int attempt) {
        if (!running || token != generation) return;
        if (environment.connected()) {
            finish(token, true, "自动点击服务已连接");
        } else if (attempt < 10) {
            // Allow asynchronous service rebinding and cold-start Shizuku binder delivery.
            environment.later(() -> poll(token, attempt + 1), 300L);
        } else if (environment.availability() == Availability.READY) {
            environment.restore((success, message) -> {
                if (token != generation || !running) return;
                boolean connected = environment.connected();
                finish(token, success && connected, success && !connected
                        ? "恢复后自动点击服务连接已丢失，请重试" : message);
            });
        } else {
            finish(token, false, environment.availability() == Availability.PERMISSION_REQUIRED
                    ? "请在权限页确认一次 Shizuku 授权"
                    : "自动点击服务未连接，Shizuku 当前不可用");
        }
    }

    private void finish(long token, boolean success, String message) {
        if (!running || token != generation) return;
        List<Pending> batch = new ArrayList<>(pending.values());
        pending.clear();
        running = false;
        generation++;
        for (Pending item : batch) {
            if (success) enqueue(item.rule, item.result);
            else item.result.complete(false, message);
        }
    }
    public void cancelAll() {
        if (running) finish(generation, false, "用户停止了等待权限恢复的任务");
    }
    private void enqueue(Rule rule, Result result) {
        boolean queued = environment.enqueue(rule);
        result.complete(queued, queued ? "指令已加入执行队列" : "指令未能加入执行队列");
    }
    private static final class Pending {
        final Rule rule;
        final Result result;
        Pending(Rule rule, Result result) { this.rule = rule; this.result = result; }
    }
}
