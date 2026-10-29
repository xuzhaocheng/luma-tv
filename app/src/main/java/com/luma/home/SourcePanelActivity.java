package com.luma.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

/** 全部信源:点一下切源,长按编辑。 */
public class SourcePanelActivity extends Activity {

    private final List<SourceItem> data = new ArrayList<>();
    private SourceAdapter adapter;
    private int currentId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_source_panel);

        GridView grid = findViewById(R.id.grid_sources);
        adapter = new SourceAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SourceItem it = data.get(position);
                TvApi.switchTo(SourcePanelActivity.this, it.id);
                finish();
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(SourcePanelActivity.this, SourceEditActivity.class);
                i.putExtra(SourceEditActivity.EXTRA_SOURCE_ID, data.get(position).id);
                startActivity(i);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        data.clear();
        data.addAll(SourceStore.all(this));
        currentId = TvApi.getCurrent(this);
        adapter.notifyDataSetChanged();
    }

    private class SourceAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return data.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_source, parent, false);
            SourceItem it = data.get(position);

            ImageView icon = v.findViewById(R.id.img_icon);
            TextView name = v.findViewById(R.id.tv_name);
            TextView star = v.findViewById(R.id.tv_star);

            icon.setImageResource(IconLib.res(it.iconKey));
            name.setText(it.name);
            name.setTextColor(getResources().getColor(
                    it.id == currentId ? R.color.accent : R.color.text_primary));
            star.setVisibility(it.pinned ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
