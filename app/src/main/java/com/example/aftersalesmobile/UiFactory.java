package com.example.aftersalesmobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class UiFactory {
    private UiFactory() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(context, color));
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static Space space(Context context, int height) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, height)));
        return space;
    }

    public static MaterialCardView card(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(context, 8));
        card.setCardElevation(dp(context, 1));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(ContextCompat.getColor(context, R.color.divider));
        card.setStrokeWidth(dp(context, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 12);
        card.setLayoutParams(params);
        return card;
    }

    public static TextInputLayout input(Context context, String hint, int inputType) {
        TextInputLayout box = new TextInputLayout(context);
        box.setHint(hint);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText edit = new TextInputEditText(context);
        edit.setInputType(inputType);
        edit.setSingleLine(inputType != android.text.InputType.TYPE_CLASS_TEXT
                || (inputType & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        box.addView(edit, editParams);
        return box;
    }

    public static String value(TextInputLayout input) {
        return input.getEditText() == null ? "" : input.getEditText().getText().toString().trim();
    }

    public static MaterialButton button(Context context, String label, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setOnClickListener(listener);
        return button;
    }
}
