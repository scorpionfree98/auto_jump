package cn.local.jiojio;
public final class ToolkitApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        com.autojump.app.automation.QueueCoordinator.recover(this);
    }
}
