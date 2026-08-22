package com.autojump.app.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.autojump.app.R;
import com.autojump.app.bean.Rule;
import java.util.List;

public class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.ViewHolder> {
    private Context mContext;
    private List<Rule> mList;
    private OnRuleOperateListener mListener;

    public interface OnRuleOperateListener {
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
        holder.tvPriority.setText("优先级: " + rule.getPriority()
                + (rule.isRequestUnlockBeforeRun() ? " · 锁屏时请求系统解锁" : ""));

        // 显示条件摘要
        StringBuilder conditionSummary = new StringBuilder();
        if (rule.getConditions() != null && !rule.getConditions().isEmpty()) {
            for (int i = 0; i < rule.getConditions().size(); i++) {
                if (i > 0) conditionSummary.append(" AND ");
                conditionSummary.append(rule.getConditions().get(i).getDescription());
            }
        } else {
            conditionSummary.append("无条件（始终执行）");
        }
        holder.tvConditions.setText(conditionSummary.toString());

        // 显示动作数量
        int actionCount = rule.getActions() != null ? rule.getActions().size() : 0;
        holder.tvActions.setText("动作: " + actionCount + " 个（按顺序执行）");

        // 启用/禁用状态
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(rule.isEnabled());
        holder.switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
            if (mListener != null) mListener.onToggleEnabled(rule, checked);
        });

        // 禁用状态显示灰色
        if (!rule.isEnabled()) {
            holder.itemView.setAlpha(0.5f);
            holder.tvName.setTextColor(Color.GRAY);
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.tvName.setTextColor(Color.BLACK);
        }

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
        TextView tvName, tvPriority, tvConditions, tvActions;
        Switch switchEnabled;
        Button btnEdit, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_rule_name);
            tvPriority = itemView.findViewById(R.id.tv_priority);
            tvConditions = itemView.findViewById(R.id.tv_conditions);
            tvActions = itemView.findViewById(R.id.tv_actions);
            switchEnabled = itemView.findViewById(R.id.switch_enabled);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
