package com.autojump.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import cn.local.jiojio.R;
import com.autojump.app.bean.Rule;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.List;

public class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.ViewHolder> {
    private Context mContext;
    private List<Rule> mList;
    private OnRuleOperateListener mListener;

    public interface OnRuleOperateListener {
        void onRun(Rule rule);
        void onEdit(Rule rule);
        void onDelete(Rule rule);
        void onToggleEnabled(Rule rule, boolean enabled);
    }

    public RuleAdapter(Context context, List<Rule> list, OnRuleOperateListener listener) {
        mContext = context;
        mList = list;
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_rule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Rule rule = mList.get(position);
        holder.tvName.setText(rule.getName());
        holder.tvPriority.setText(rule.isRequestUnlockBeforeRun() ? "锁屏亮屏 · 解锁后继续" : "在已解锁时运行");

        // 显示条件摘要
        StringBuilder conditionSummary = new StringBuilder();
        if (rule.getConditions() != null && !rule.getConditions().isEmpty()) {
            java.util.List<String> timeWindows = new java.util.ArrayList<>();
            java.util.List<String> required = new java.util.ArrayList<>();
            for (com.autojump.app.domain.RuleCondition condition : rule.getConditions()) {
                if (condition.getType() == com.autojump.app.domain.ConditionType.TIME) {
                    timeWindows.add(condition.getDescription());
                } else {
                    required.add(condition.getDescription());
                }
            }
            if (!timeWindows.isEmpty()) {
                if (timeWindows.size() > 1) conditionSummary.append("(");
                conditionSummary.append(android.text.TextUtils.join(" 或 ", timeWindows));
                if (timeWindows.size() > 1) conditionSummary.append(")");
            }
            for (String item : required) {
                if (conditionSummary.length() > 0) conditionSummary.append(" 且 ");
                conditionSummary.append(item);
            }
        } else {
            conditionSummary.append("桌面点击 / 手机自动助手启动时执行");
        }
        holder.tvConditions.setText(conditionSummary.toString());

        // 显示动作数量
        int actionCount = rule.getActions() != null ? rule.getActions().size() : 0;
        StringBuilder steps = new StringBuilder();
        for (com.autojump.app.domain.RuleAction action : rule.getActions()) {
            if (steps.length() > 0) steps.append("\n");
            steps.append(com.autojump.app.ui.ActionLabels.describe(action));
        }
        holder.tvActions.setText(steps.length() == 0 ? "还没有操作" : steps.toString());
        holder.tvRecent.setText("尚无运行记录");
        for (com.autojump.app.automation.ExecutionLogStore.Entry entry :
                new com.autojump.app.automation.ExecutionLogStore(mContext).getAll()) {
            if (rule.getId().equals(entry.getRuleId())) {
                String time = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date(entry.getTimestamp()));
                String message = "COMPLETED".equals(entry.getStatus())
                        ? "操作已发送，请在目标应用确认结果" : entry.getMessage();
                holder.tvRecent.setText(time + " · " + message);
                break;
            }
        }
        holder.btnRun.setOnClickListener(v -> { if (mListener != null) mListener.onRun(rule); });

        // 启用/禁用状态
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(rule.isEnabled());
        holder.switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
            if (mListener != null) mListener.onToggleEnabled(rule, checked);
        });

        holder.itemView.setAlpha(rule.isEnabled() ? 1.0f : 0.62f);

        // 编辑
        holder.btnEdit.setOnClickListener(v -> {
            if (mListener != null) mListener.onEdit(rule);
        });

        // 删除
        holder.btnDelete.setOnClickListener(v -> {
            if (mListener != null) mListener.onDelete(rule);
        });
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPriority, tvConditions, tvActions, tvRecent;
        SwitchMaterial switchEnabled;
        Button btnEdit, btnDelete, btnRun;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_rule_name);
            tvPriority = itemView.findViewById(R.id.tv_priority);
            tvConditions = itemView.findViewById(R.id.tv_conditions);
            tvActions = itemView.findViewById(R.id.tv_actions);
            switchEnabled = itemView.findViewById(R.id.switch_enabled);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            btnRun = itemView.findViewById(R.id.btn_run_rule);
            tvRecent = itemView.findViewById(R.id.tv_recent_run);
        }
    }
}
