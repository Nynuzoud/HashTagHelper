package com.volokh.danylo.hashtaghelper;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * This is a helper class that should be used with {@link android.widget.EditText} or {@link android.widget.TextView}
 * In order to have hash-tagged words highlighted. It also provides a click listeners for every hashtag
 * <p>
 * Example :
 * #ThisIsHashTagWord
 * #ThisIsFirst#ThisIsSecondHashTag
 * #hashtagendsifitfindsnotletterornotdigitsignlike_thisIsNotHighlithedArea
 */
public final class HashTagHelper implements ClickableForegroundColorSpan.OnHashTagClickListener {

    private static final Character NEW_LINE = '\n';
    private static final Character CARRIAGE_RETURN = '\r';
    private static final Character SPACE = ' ';

    /**
     * If this is not null then  all of the symbols in the List will be considered as valid symbols of hashtag
     * For example :
     * mAdditionalHashTagChars = {'$','_','-'}
     * it means that hashtag: "#this_is_hashtag-with$dollar-sign" will be highlighted.
     * <p>
     * Note: if mAdditionalHashTagChars would be "null" only "#this" would be highlighted
     */
    private final List<Character> mAdditionalHashTagChars;

    /**
     * If this is not null then all of the symbols in the List will be considered as valid start symbols
     * For example:
     * mStartChars = {'@','%'}
     * it means that all words starting with these symbols will be highlighted.
     * <p>
     * Note: if mStartChars is null, words started with '#' symbol will be highlighted
     */
    private final List<Character> mStartChars;
    private TextView mTextView;
    private int mHashTagWordColor;

    /**
     * Character style needs to separate different spans in the text
     */
    private Class<? extends CharacterStyle> mCharacterStyle;

    private OnHashTagClickListener mOnHashTagClickListener;

    private final ArrayList<Character> mForbiddenCharacters = new ArrayList<>();

    public static final class Creator {

        private Creator() {
        }

        public static HashTagHelper create(int color, OnHashTagClickListener listener) {
            return new HashTagHelper(color, listener, null, null, null);
        }

        public static HashTagHelper create(int color, OnHashTagClickListener listener, @NonNull List<Character> additionalHashTagChars) {
            return new HashTagHelper(color, listener, additionalHashTagChars, null, null);
        }

        public static HashTagHelper create(
                int color,
                OnHashTagClickListener listener,
                List<Character> additionalHashTagChars,
                @NonNull List<Character> startChars
        ) {
            return new HashTagHelper(color, listener, additionalHashTagChars, startChars, null);
        }

        public static HashTagHelper create(
                int color,
                OnHashTagClickListener listener,
                List<Character> additionalHashTagChars,
                List<Character> startChars,
                @NonNull Class<? extends ClickableForegroundColorSpan> characterStyle
        ) {
            return new HashTagHelper(color, listener, additionalHashTagChars, startChars, characterStyle);
        }

    }

    public interface OnHashTagClickListener {
        void onHashTagClicked(Character initialChar, String hashTag);
    }

    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence text, int start, int before, int count) {
            if (text.length() > 0) {
                eraseAndColorizeAllText(text);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    private HashTagHelper(
            int color,
            OnHashTagClickListener listener,
            @Nullable List<Character> additionalHashTagChars,
            @Nullable List<Character> startChars,
            @Nullable Class<? extends ClickableForegroundColorSpan> characterStyle
    ) {

        addForbiddenCharactersToList();

        if (characterStyle == null) {
            mCharacterStyle = ClickableForegroundColorSpan.class;
        } else {
            mCharacterStyle = characterStyle;
        }
        mHashTagWordColor = color;
        mOnHashTagClickListener = listener;
        mAdditionalHashTagChars = new ArrayList<>();
        mStartChars = new ArrayList<>();

        if (additionalHashTagChars != null) {
            mAdditionalHashTagChars.addAll(additionalHashTagChars);
        }

        if (startChars != null) {
            mStartChars.addAll(startChars);
        }
    }

    private void addForbiddenCharactersToList() {
        mForbiddenCharacters.add(NEW_LINE);
        mForbiddenCharacters.add(SPACE);
        mForbiddenCharacters.add(CARRIAGE_RETURN);
    }

    public void handle(TextView textView) {
        if (mTextView == null) {
            mTextView = textView;
            mTextView.addTextChangedListener(mTextWatcher);

            // in order to use spannable we have to set buffer type
            mTextView.setText(mTextView.getText(), TextView.BufferType.SPANNABLE);

            if (mOnHashTagClickListener != null) {
                // we need to set this in order to get onClick event
                mTextView.setMovementMethod(LinkMovementMethod.getInstance());

                // after onClick clicked text become highlighted
                mTextView.setHighlightColor(Color.TRANSPARENT);
            } else {
                // hash tags are not clickable, no need to change these parameters
            }

            setColorsToAllHashTags(mTextView.getText());
        } else {
            throw new RuntimeException("TextView is not null. You need to create a unique HashTagHelper for every TextView");
        }

    }

    private void eraseAndColorizeAllText(CharSequence text) {

        Spannable spannable = ((Spannable) mTextView.getText());

        CharacterStyle[] spans = spannable.getSpans(0, text.length(), mCharacterStyle);
        for (CharacterStyle span : spans) {
            spannable.removeSpan(span);
        }

        setColorsToAllHashTags(text);
    }

    private void setColorsToAllHashTags(CharSequence text) {
        String trimmedText = text.toString().trim();
        int startIndexOfNextHashSign;

        int index = 0;
        while (index < trimmedText.length() - 1) {
            char sign = trimmedText.charAt(index);
            char nextSign = trimmedText.charAt(index + 1);
            int nextNotLetterDigitCharIndex = index + 1; // we assume it is next. if if was not changed by findNextValidHashTagChar then index will be incremented by 1
            if (mStartChars.contains(sign) && !mStartChars.contains(nextSign) && !mForbiddenCharacters.contains(nextSign)) {
                startIndexOfNextHashSign = index;

                nextNotLetterDigitCharIndex = findNextValidHashTagChar(trimmedText, startIndexOfNextHashSign);

                setColorForHashTagToTheEnd(startIndexOfNextHashSign, nextNotLetterDigitCharIndex);
            }

            index = nextNotLetterDigitCharIndex;
        }
    }

    private int findNextValidHashTagChar(CharSequence text, int start) {

        int nonLetterDigitCharIndex = -1; // skip first sign '#"
        for (int index = start + 1; index < text.length(); index++) {

            char sign = text.charAt(index);

            boolean isValidSign = (Character.isLetterOrDigit(sign) || mAdditionalHashTagChars.contains(sign))
                    && !mStartChars.contains(sign);
            if (!isValidSign) {
                nonLetterDigitCharIndex = index;
                break;
            }
        }
        if (nonLetterDigitCharIndex == -1) {
            // we didn't find non-letter. We are at the end of text
            nonLetterDigitCharIndex = text.length();
        }

        return nonLetterDigitCharIndex;
    }

    private void setColorForHashTagToTheEnd(int startIndex, int nextNotLetterDigitCharIndex) {
        Spannable s = (Spannable) mTextView.getText();

        CharacterStyle span;

        if (mOnHashTagClickListener != null) {
            span = new ClickableForegroundColorSpan(mHashTagWordColor, this);
        } else {
            // no need for clickable span because it is messing with selection when click
            span = new ForegroundColorSpan(mHashTagWordColor);
        }

        s.setSpan(span, startIndex, nextNotLetterDigitCharIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public List<String> getAllHashTags(boolean withHashes) {

        String text = mTextView.getText().toString();
        Spannable spannable = (Spannable) mTextView.getText();

        // use set to exclude duplicates
        Set<String> hashTags = new LinkedHashSet<>();

        for (CharacterStyle span : spannable.getSpans(0, text.length(), mCharacterStyle)) {
            hashTags.add(
                    text.substring(!withHashes ? spannable.getSpanStart(span) + 1/*skip "#" sign*/
                                    : spannable.getSpanStart(span),
                            spannable.getSpanEnd(span)));
        }

        return new ArrayList<>(hashTags);
    }

    public List<String> getAllHashTags() {
        return getAllHashTags(false);
    }

    @Override
    public void onHashTagClicked(Character initialChar, String hashTag) {
        mOnHashTagClickListener.onHashTagClicked(initialChar, hashTag);
    }
}
