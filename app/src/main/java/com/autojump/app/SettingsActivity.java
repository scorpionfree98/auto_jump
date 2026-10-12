package com.autojump.app;
import cn.local.jiojio.R;
import cn.local.jiojio.BuildConfig;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.autojump.app.adapter.ConfigAdapter;
import com.autojump.app.bean.ConfigBean;
import com.autojump.app.util.SPUtil;
import com.autojump.app.util.ShortcutUtil;
import java.util.List;
import java.util.UUID;

public class SettingsActivity extends AppCompatActivity implements ConfigAdapter.OnConfigOperateListener {
    private RecyclerView rvConfig;
    private List<ConfigBean> mConfigList;
    private ConfigAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        rvConfig = findViewById(R.id.rv_config);
        findViewById(R.id.btn_add_config).setOnClickListener(v -> showEditDialog(null));
        findViewById(R.id.btn_edit_default).setOnClickListener(v -> showDefaultLinkDialog());

        mConfigList = SPUtil.getConfigList(this);
        mAdapter = new ConfigAdapter(this, mConfigList, this);
        rvConfig.setLayoutManager(new LinearLayoutManager(this));
        rvConfig.setAdapter(mAdapter);
    }

    // 新增 / 编辑跳转配置弹窗。bean 为 null 表示新增，否则为编辑
    private void showEditDialog(ConfigBean editBean) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_config, null);
        EditText etName = dialogView.findViewById(R.id.et_short_name);
        EditText etLink = dialogView.findViewById(R.id.et_deep_link);

        boolean isEdit = editBean != null;
        if (isEdit) {
            etName.setText(editBean.getName());
            etLink.setText(editBean.getDeepLink());
        }

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "编辑跳转指令" : "新增跳转指令")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String link = etLink.getText().toString().trim();
                    if (name.isEmpty() || link.isEmpty()) {
                        Toast.makeText(this, "名称和链接不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (isEdit) {
                        editBean.setName(name);
                        editBean.setDeepLink(link);
                        mAdapter.notifyDataSetChanged();
                    } else {
                        String id = UUID.randomUUID().toString();
                        mConfigList.add(new ConfigBean(id, name, link));
                        mAdapter.notifyItemInserted(mConfigList.size() - 1);
                    }
                    SPUtil.saveConfigList(this, mConfigList);
                    // 同步刷新长按快捷菜单
                    ShortcutUtil.refreshDynamicShortcut(this);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 修改默认（点击图标）跳转链接
    private void showDefaultLinkDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_config, null);
        EditText etName = dialogView.findViewById(R.id.et_short_name);
        EditText etLink = dialogView.findViewById(R.id.et_deep_link);
        etName.setVisibility(View.GONE);
        etLink.setText(SPUtil.getDefaultLink(this));

        new AlertDialog.Builder(this)
                .setTitle("修改默认跳转链接")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    String link = etLink.getText().toString().trim();
                    if (link.isEmpty()) {
                        Toast.makeText(this, "链接不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SPUtil.setDefaultLink(this, link);
                    Toast.makeText(this, "已更新默认跳转链接", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 编辑配置回调
    @Override
    public void onEdit(ConfigBean bean) {
        showEditDialog(bean);
    }

    // 删除配置回调
    @Override
    public void onDelete(ConfigBean bean) {
        new AlertDialog.Builder(this)
                .setTitle("删除确认")
                .setMessage("确定删除「" + bean.getName() + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    mConfigList.remove(bean);
                    SPUtil.saveConfigList(this, mConfigList);
                    mAdapter.notifyDataSetChanged();
                    ShortcutUtil.refreshDynamicShortcut(this);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
