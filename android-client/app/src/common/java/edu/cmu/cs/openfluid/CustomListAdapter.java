package edu.cmu.cs.openfluid;

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
    private int resource;

    public CustomListAdapter(Context context, int resource, List<Pair<Integer, String>> items) {
        super(context, resource, items);
        this.context = context;
        this.items = items;
        this.resource = resource;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listItem = convertView;
        if (listItem == null)
            listItem = LayoutInflater.from(context).inflate(resource, parent, false);

        Pair<Integer, String> currentItem = items.get(position);

        ImageView image = listItem.findViewById(R.id.dialog_imageview);
        image.setImageResource(currentItem.first);

        TextView description = listItem.findViewById(R.id.item_description2);
        description.setText(currentItem.second);

        return listItem;
    }
}