package com.autojump.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.autojump.app.R;
import com.autojump.app.bean.ConfigBean;
import com.autojump.app.util.ShortcutUtil;
import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {
    private Context mContext;
    private List<ConfigBean> mList;
    private OnConfigOperateListener mListener;

    public interface OnConfigOperateListener {
        void onEdit(ConfigBean bean);
        void onDelete(ConfigBean bean);
    }

    public ConfigAdapter(Context context, List<ConfigBean> list, OnConfigOperateListener listener) {
        mContext = context;
        mList = list;
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_config, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConfigBean bean = mList.get(position);
        holder.tvName.setText(bean.getName());
        holder.tvLink.setText(bean.getDeepLink());

        // 生成桌面独立快捷
        holder.btnPinDesktop.setOnClickListener(v -> ShortcutUtil.createDesktopPinShortcut(mContext, bean));
        // 编辑配置
        holder.btnEdit.setOnClickListener(v -> {
            if (mListener != null) mListener.onEdit(bean);
        });
        // 删除配置
        holder.btnDelete.setOnClickListener(v -> {
            if (mListener != null) mListener.onDelete(bean);
        });
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvLink;
        Button btnPinDesktop, btnEdit, btnDelete;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvLink = itemView.findViewById(R.id.tv_link);
            btnPinDesktop = itemView.findViewById(R.id.btn_pin_desktop);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
