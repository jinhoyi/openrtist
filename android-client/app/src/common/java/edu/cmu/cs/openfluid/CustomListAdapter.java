package edu.cmu.cs.openrtist;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class CustomListAdapter extends ArrayAdapter<Pair<Integer, String>> {
    private Context context;
    private List<Pair<Integer, String>> items;

    public CustomListAdapter(Context context, List<Pair<Integer, String>> items) {
        super(context, R.layout.list_item, items);
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View mylisthelp = convertView;
        if (mylisthelp == null)
            mylisthelp = LayoutInflater.from(context).inflate(R.layout.myhelplist, parent, false);

        Pair<Integer, String> currentItem = items.get(position);

        ImageView image = mylisthelp.findViewById(R.id.dialog_imageview);
        image.setImageResource(currentItem.first);

        TextView description = mylisthelp.findViewById(R.id.item_description2);
        description.setText(currentItem.second);

        return mylisthelp;
    }
}

//package edu.cmu.cs.openrtist;
//import android.content.Context;
//import android.util.Pair;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ImageView;
//import android.widget.TextView;
//
//import androidx.annotation.NonNull;
//import androidx.recyclerview.widget.RecyclerView;
//
//import java.util.List;
//
//import edu.cmu.cs.openrtist.R;
//
//public class CustomListAdapter extends RecyclerView.Adapter<CustomListAdapter.ViewHolder> {
//    private List<Pair<Integer, String>> items;
//    private Context context;
//
//    public CustomListAdapter(Context context, List<Pair<Integer, String>> items) {
//        this.context = context;
//        this.items = items;
//    }
//
//    @NonNull
//    @Override
//    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mylisthelp, parent, false);
//        return new ViewHolder(view);
//    }
//
//    @Override
//    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
//        Pair<Integer, String> currentItem = items.get(position);
//        holder.itemImage.setImageResource(currentItem.first);
//        holder.itemDescription.setText(currentItem.second);
//    }
//
//    @Override
//    public int getItemCount() {
//        return items.size();
//    }
//
//    static class ViewHolder extends RecyclerView.ViewHolder {
//        ImageView itemImage;
//        TextView itemDescription;
//
//        ViewHolder(View itemView) {
//            super(itemView);
//            itemImage = itemView.findViewById(R.id.dialog_imageview);
//            itemDescription = itemView.findViewById(R.id.item_description2);
//        }
//    }
//}