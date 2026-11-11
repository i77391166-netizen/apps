package com.example.myapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS = "notes_app";
    private static final String KEY = "items";

    private final List<String> notes = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout list;
    private EditText input;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        load();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), 0);
        root.setBackgroundColor(Color.parseColor("#F6F6F8"));

        TextView title = new TextView(this);
        title.setText("Заметки");
        title.setTextSize(30);
        title.setTextColor(Color.parseColor("#111111"));
        title.setPadding(0, dp(8), 0, dp(16));
        root.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        input = new EditText(this);
        input.setHint("Что записать?");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button add = new Button(this);
        add.setText("+");
        add.setTextSize(20);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNote();
            }
        });
        row.addView(add);

        root.addView(row);

        ScrollView sc = new ScrollView(this);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scp.topMargin = dp(10);
        root.addView(sc, scp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);

        setContentView(root);
        render();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void addNote() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Пусто же", Toast.LENGTH_SHORT).show();
            return;
        }
        notes.add(0, text.replace("\n", " "));
        input.setText("");
        save();
        render();
    }

    private void render() {
        list.removeAllViews();

        if (notes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Пока пусто. Добавь первую заметку.");
            empty.setTextColor(Color.parseColor("#888888"));
            empty.setPadding(0, dp(24), 0, 0);
            list.addView(empty);
            return;
        }

        for (int i = 0; i < notes.size(); i++) {
            final int index = i;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(card());

            TextView text = new TextView(this);
            text.setText(notes.get(i));
            text.setTextSize(16);
            text.setTextColor(Color.parseColor("#222222"));
            text.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.addView(text, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button del = new Button(this);
            del.setText("x");
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    notes.remove(index);
                    save();
                    render();
                }
            });
            card.addView(del);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dp(10);
            list.addView(card, cp);
        }
    }

    private GradientDrawable card() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(14));
        return g;
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(notes.get(i));
        }
        prefs.edit().putString(KEY, sb.toString()).apply();
    }

    private void load() {
        String raw = prefs.getString(KEY, "");
        if (raw.isEmpty()) return;
        for (String line : raw.split("\n")) {
            if (!line.trim().isEmpty()) notes.add(line);
        }
    }
}
