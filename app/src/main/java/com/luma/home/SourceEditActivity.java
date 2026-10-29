package com.luma.home;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import android.app.Activity;

/** 编辑信源:改名字、换图标、设为首页常用。 */
public class SourceEditActivity extends Activity {

    static final String EXTRA_SOURCE_ID = "source_id";

    private SourceItem item;
    private EditText etName;
    private CheckBox cbPin;
    private String selectedIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_source_edit);

        int id = getIntent().getIntExtra(EXTRA_SOURCE_ID, -1);
        item = SourceStore.get(this, id);
        selectedIcon = item.iconKey;

        etName = findViewById(R.id.et_name);
        cbPin = findViewById(R.id.cb_pin);

        etName.setText(item.name);
        cbPin.setChecked(item.pinned);
        ((TextView) findViewById(R.id.tv_title)).setText(
                getString(R.string.source_edit_title) + ": " + SourceStore.defaultName(this, id));

        buildPresets();
        buildIcons();

        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
    }

    private void buildPresets() {
        LinearLayout row = findViewById(R.id.row_presets);
        final int[] presets = {
                R.string.preset_tv, R.string.preset_settop, R.string.preset_ps5, R.string.preset_switch,
                R.string.preset_xbox, R.string.preset_pc, R.string.preset_bluray,
                R.string.preset_projector, R.string.preset_speaker, R.string.preset_camera
        };
        for (int i = 0; i < presets.length; i++) {
            final String label = getString(presets[i]);
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextColor(getResources().getColor(R.color.text_primary));
            tv.setTextSize(15f);
            tv.setBackgroundResource(R.drawable.bg_card);
            tv.setFocusable(true);
            tv.setFocusableInTouchMode(true);
            tv.setPadding(dp(18), dp(10), dp(18), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(10), 0);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etName.setText(label);
                    etName.setSelection(etName.getText().length());
                }
            });
            row.addView(tv);
        }
    }

    private void buildIcons() {
        LinearLayout grid = findViewById(R.id.grid_icons);
        grid.removeAllViews();
        grid.setClipChildren(false);
        grid.setClipToPadding(false);

        final int perRow = 8;
        LinearLayout row = null;
        for (int i = 0; i < IconLib.ALL.length; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                grid.addView(row);
            }
            final String key = IconLib.ALL[i];

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setFocusable(true);
            cell.setFocusableInTouchMode(true);
            cell.setBackgroundResource(R.drawable.bg_card);
            cell.setPadding(dp(6), dp(8), dp(6), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(118), dp(118));
            lp.setMargins(0, 0, dp(14), dp(14));
            cell.setLayoutParams(lp);

            ImageView img = new ImageView(this);
            img.setImageResource(IconLib.res(key));
            img.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(64)));
            cell.addView(img);

            TextView label = new TextView(this);
            label.setText(IconLib.label(this, key));
            label.setTextColor(getResources().getColor(R.color.text_secondary));
            label.setTextSize(14f);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpl = new LinearLayout.LayoutParams(
                    dp(108), ViewGroup.LayoutParams.WRAP_CONTENT);
            lpl.topMargin = dp(6);
            label.setLayoutParams(lpl);
            cell.addView(label);

            cell.setTag(key);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedIcon = key;
                    updateIconCells(grid);
                }
            });
            row.addView(cell);
        }
        updateIconCells(grid);
    }

    private void updateIconCells(LinearLayout grid) {
        for (int r = 0; r < grid.getChildCount(); r++) {
            View child = grid.getChildAt(r);
            if (!(child instanceof LinearLayout)) {
                continue;
            }
            LinearLayout line = (LinearLayout) child;
            for (int i = 0; i < line.getChildCount(); i++) {
                View cell = line.getChildAt(i);
                Object tag = cell.getTag();
                boolean selected = tag != null && tag.equals(selectedIcon);
                cell.setBackgroundResource(selected
                        ? R.drawable.bg_card_focus : R.drawable.bg_card);
                if (cell instanceof LinearLayout) {
                    LinearLayout box = (LinearLayout) cell;
                    if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
                        ((TextView) box.getChildAt(1)).setTextColor(getResources().getColor(
                                selected ? R.color.accent : R.color.text_secondary));
                    }
                }
            }
        }
    }

    private void save() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            name = SourceStore.defaultName(this, item.id);
        }
        final boolean wantPin = cbPin.isChecked();
        final String finalName = name;

        if (wantPin && !item.pinned && SourceStore.pinned(this).size() >= SourceStore.MAX_PINNED) {
            SourceItem oldest = SourceStore.oldestPinned(this);
            String oldestName = oldest == null ? "" : oldest.name;
            final int oldestId = oldest == null ? -1 : oldest.id;

            new AlertDialog.Builder(this)
                    .setTitle(R.string.pinned_full_title)
                    .setMessage(getString(R.string.pinned_full_msg, oldestName))
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            if (oldestId >= 0) {
                                SourceStore.unpin(SourceEditActivity.this, oldestId);
                            }
                            applyAndFinish(finalName, wantPin);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        applyAndFinish(finalName, wantPin);
    }

    private void applyAndFinish(String name, boolean pin) {
        item.name = name;
        item.iconKey = selectedIcon;
        if (pin != item.pinned) {
            item.pinned = pin;
            item.pinnedAt = pin ? System.currentTimeMillis() : 0;
        }
        SourceStore.save(this, item);
        finish();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
