package com.thoughtcrimes.securesms.wallet.node;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.thoughtcrimes.securesms.data.NodeInfo;
import com.thoughtcrimes.securesms.util.Helper;
import com.thoughtcrimes.securesms.util.ThemeHelper;

import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import io.beldex.bchat.R;
import timber.log.Timber;

public class NodeInfoAdapter extends RecyclerView.Adapter<NodeInfoAdapter.ViewHolder> {
    private final SimpleDateFormat TS_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public interface OnInteractionListener {
        void onInteraction(View view, NodeInfo item);

        boolean onLongInteraction(View view, NodeInfo item);
    }

    private final List<NodeInfo> nodeItems = new ArrayList<>();
    private final OnInteractionListener listener;

    private final Context context;

    public NodeInfoAdapter(Context context, OnInteractionListener listener) {
        this.context = context;
        this.listener = listener;
        Calendar cal = Calendar.getInstance();
        TimeZone tz = cal.getTimeZone(); //get the local time zone.
        TS_FORMATTER.setTimeZone(tz);
    }

    private static class NodeDiff extends DiffCallback<NodeInfo> {

        public NodeDiff(List<NodeInfo> oldList, List<NodeInfo> newList) {
            super(oldList, newList);
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return mOldList.get(oldItemPosition).equals(mNewList.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            final NodeInfo oldItem = mOldList.get(oldItemPosition);
            final NodeInfo newItem = mNewList.get(newItemPosition);
            return (oldItem.getTimestamp() == newItem.getTimestamp())
                    && (oldItem.isTested() == newItem.isTested())
                    && (oldItem.isValid() == newItem.isValid())
                    && (oldItem.getResponseTime() == newItem.getResponseTime())
                    && (oldItem.isSelected() == newItem.isSelected())
                    && (oldItem.getName().equals(newItem.getName()));
        }
    }

    @Override
    public @NonNull
    ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_node_list_view, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final @NonNull ViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return nodeItems.size();
    }

    public void addNode(NodeInfo node) {
        List<NodeInfo> newItems = new ArrayList<>(nodeItems);
        if (!nodeItems.contains(node))
            newItems.add(node);
        setNodes(newItems); // in case the nodeinfo has changed
    }

    public void setNodes(Collection<NodeInfo> newItemsCollection) {
        List<NodeInfo> newItems;
        if (newItemsCollection != null) {
            newItems = new ArrayList<>(newItemsCollection);
            Collections.sort(newItems, NodeInfo.BestNodeComparator);
        } else {
            newItems = new ArrayList<>();
        }
        final NodeInfoAdapter.NodeDiff diffCallback = new NodeInfoAdapter.NodeDiff(nodeItems, newItems);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        nodeItems.clear();
        nodeItems.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    public void setNodes() {
        setNodes(nodeItems);
    }

    private boolean itemsClickable = true;

    public void allowClick(boolean clickable) {
        itemsClickable = clickable;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        final ImageButton ibBookmark;
        final View pbBookmark;
        final TextView tvName;
        final TextView tvIp;
        final ImageView ivPing;
        NodeInfo nodeItem;
        RelativeLayout itemNodeRelativeLayout;

        ViewHolder(View itemView) {
            super(itemView);
            ibBookmark = itemView.findViewById(R.id.ibBookmark);
            pbBookmark = itemView.findViewById(R.id.pbBookmark);
            tvName = itemView.findViewById(R.id.nodeNameTextView);
            tvIp = itemView.findViewById(R.id.tvAddress);
            ivPing = itemView.findViewById(R.id.ivPing);
            itemNodeRelativeLayout = itemView.findViewById(R.id.itemNodeRelativeLayout);
            ibBookmark.setOnClickListener(v -> {
                nodeItem.toggleFavourite();
                //showStar();
                if (!nodeItem.isFavourite()) {
                    nodeItem.setSelected(false);
                    setNodes(nodeItems);
                }
            });
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        private void showStar() {
            if (nodeItem.isFavourite()) {
                ibBookmark.setImageResource(R.drawable.ic_circle);
            } else {
                ibBookmark.setImageResource(R.drawable.ic_circle);
            }
        }

        void bind(int position) {
            nodeItem = nodeItems.get(position);
            tvName.setText(nodeItem.getName());
            Timber.d("tvName%s", tvName.getText().toString());
            Timber.d("tvName value of node getname %s", nodeItem.getName());
            ivPing.setImageResource(getPingIcon(nodeItem));
            if (nodeItem.isTested()) {
                if (nodeItem.isValid()) {
                    Helper.showTimeDifference(tvIp, nodeItem.getTimestamp());
                    ibBookmark.setImageDrawable(null);
                    Log.d("Beldex","isValid() if");
                } else {
                    Log.d("Beldex","isValid() else");
                    tvIp.setText(getResponseErrorText(context, nodeItem.getResponseCode()));
                    tvIp.setTextColor(ThemeHelper.getThemedColor(context, R.attr.colorError));
                    ibBookmark.setImageResource(R.drawable.ic_circle);
                }
            } else {
                tvIp.setText(context.getResources().getString(R.string.node_testing, nodeItem.getHostAddress()));
            }
            itemView.setSelected(nodeItem.isSelected());
            itemView.setClickable(itemsClickable);
            itemView.setEnabled(itemsClickable);
            ibBookmark.setClickable(itemsClickable);
            pbBookmark.setVisibility(nodeItem.isSelecting() ? View.VISIBLE : View.INVISIBLE);
            itemNodeRelativeLayout.setBackgroundColor(nodeItem.isSelected() ? context.getResources().getColor(R.color.green):context.getResources().getColor(R.color.card_color));
            //showStar();
        }

        @Override
        public void onClick(View view) {
            if (listener != null) {
                int position = getAdapterPosition(); // gets item position
                if (position != RecyclerView.NO_POSITION) { // Check if an item was deleted, but the user clicked it before the UI removed it
                    final NodeInfo node = nodeItems.get(position);
                    node.setSelecting(true);
                    allowClick(false);
                    listener.onInteraction(view, node);
                }
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (listener != null) {
                int position = getAdapterPosition(); // gets item position
                if (position != RecyclerView.NO_POSITION) { // Check if an item was deleted, but the user clicked it before the UI removed it
                    return listener.onLongInteraction(view, nodeItems.get(position));
                }
            }
            return false;
        }
    }

    static public int getPingIcon(NodeInfo nodeInfo) {
        if (nodeInfo.isUnauthorized()) {
            return R.drawable.ic_camera;
        }
        if (nodeInfo.isValid()) {
            final double ping = nodeInfo.getResponseTime();
            if (ping < NodeInfo.PING_GOOD) {
                return R.drawable.ic_camera;
            } else if (ping < NodeInfo.PING_MEDIUM) {
                return R.drawable.ic_camera;
            } else if (ping < NodeInfo.PING_BAD) {
                return R.drawable.ic_camera;
            } else {
                return R.drawable.ic_camera;
            }
        } else {
            return R.drawable.ic_camera;
        }
    }

    static public String getResponseErrorText(Context ctx, int responseCode) {
        if (responseCode == 0) {
            return ctx.getResources().getString(R.string.node_general_error);
        } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return ctx.getResources().getString(R.string.node_auth_error);
        } else {
            return ctx.getResources().getString(R.string.node_test_error, responseCode);
        }
    }
}
