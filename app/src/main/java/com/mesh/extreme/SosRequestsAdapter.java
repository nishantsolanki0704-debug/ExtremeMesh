package com.mesh.extreme;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SosRequestsAdapter extends RecyclerView.Adapter<SosRequestsAdapter.SosViewHolder> {

    public interface OnAcceptClickListener {
        void onAccept(MeshPacket packet);
    }

    private List<MeshPacket> packetList = new ArrayList<>();
    private final OnAcceptClickListener acceptListener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public SosRequestsAdapter(OnAcceptClickListener acceptListener) {
        this.acceptListener = acceptListener;
    }

    public void setPackets(List<MeshPacket> packets) {
        this.packetList = packets != null ? packets : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SosViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sos_request, parent, false);
        return new SosViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SosViewHolder holder, int position) {
        MeshPacket packet = packetList.get(position);

        holder.tvNodeId.setText("SOS Node #" + Integer.toHexString(packet.packetHash).toUpperCase());
        holder.tvTime.setText(timeFormat.format(new Date(packet.localReceivedTimeMs)));

        // Decode flags
        boolean aid = (packet.flags & (1 << 0)) != 0;
        boolean food = (packet.flags & (1 << 1)) != 0;
        boolean ackComing = (packet.flags & (1 << 2)) != 0;
        boolean ackDelivered = (packet.flags & (1 << 3)) != 0;

        StringBuilder tags = new StringBuilder();
        if (aid) tags.append("[FIRST AID NEEDED] ");
        if (food) tags.append("[FOOD NEEDED] ");
        if (ackComing) tags.append("[HELP EN ROUTE] ");
        if (ackDelivered) tags.append("[SUPPLIES DELIVERED] ");
        if (tags.length() == 0) tags.append("[GENERAL DISTRESS]");

        holder.tvTriageTags.setText(tags.toString());
        holder.tvDetails.setText(String.format(Locale.getDefault(), "People: %d | Lat: %.4f, Lon: %.4f",
                packet.peopleCount, packet.latitude, packet.longitude));

        if (packet.message != null && !packet.message.trim().isEmpty()) {
            holder.tvMessage.setVisibility(View.VISIBLE);
            holder.tvMessage.setText("Msg: " + packet.message);
        } else {
            holder.tvMessage.setVisibility(View.GONE);
        }

        if (ackComing) {
            holder.btnAcceptSos.setText("Response Sent (En Route)");
            holder.btnAcceptSos.setEnabled(false);
            holder.btnAcceptSos.setBackgroundColor(0xFF2E7D32);
        } else {
            holder.btnAcceptSos.setText("Accept Request (Help En Route)");
            holder.btnAcceptSos.setEnabled(true);
            holder.btnAcceptSos.setBackgroundColor(0xFF4CAF50);
            holder.btnAcceptSos.setOnClickListener(v -> acceptListener.onAccept(packet));
        }
    }

    @Override
    public int getItemCount() {
        return packetList.size();
    }

    static class SosViewHolder extends RecyclerView.ViewHolder {
        TextView tvNodeId, tvTime, tvTriageTags, tvDetails, tvMessage;
        Button btnAcceptSos;

        public SosViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNodeId = itemView.findViewById(R.id.tvNodeId);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTriageTags = itemView.findViewById(R.id.tvTriageTags);
            tvDetails = itemView.findViewById(R.id.tvDetails);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            btnAcceptSos = itemView.findViewById(R.id.btnAcceptSos);
        }
    }
}