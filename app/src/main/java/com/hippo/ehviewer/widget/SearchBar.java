/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.widget;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.hippo.animation.SimpleAnimatorListener;
import com.hippo.drawable.AddDeleteDrawable;
import com.hippo.drawable.DrawerArrowDrawable;
import com.hippo.effect.ViewTransition;
import com.hippo.ehviewer.Constants;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.util.Config;
import com.hippo.util.MathUtils;
import com.hippo.util.Messenger;
import com.hippo.util.UiUtils;
import com.hippo.util.ViewUtils;
import com.hippo.widget.SimpleImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchBar extends CardView implements View.OnClickListener,
        TextView.OnEditorActionListener, TextWatcher,
        SearchEditText.SearchEditTextListener, Messenger.Receiver {

    private static final String STATE_KEY_SUPER = "super";
    private static final String STATE_KEY_STATE = "state";

    private static final long ANIMATE_TIME = 300l;

    public static final int STATE_NORMAL = 0;
    public static final int STATE_SEARCH = 1;
    public static final int STATE_SEARCH_LIST = 2;

    private int mState = STATE_NORMAL;

    private Path mPath = new Path();
    private int mWidth;
    private int mHeight;
    private int mBaseHeight;
    private float mProgress;

    private SimpleImageView mMenuButton;
    private TextView mTitleTextView;
    private SimpleImageView mActionButton;
    private SearchEditText mEditText;
    private ListView mList;

    private ViewTransition mViewTransition;

    private DrawerArrowDrawable mDrawerArrowDrawable;
    private AddDeleteDrawable mAddDeleteDrawable;

    private SearchDatabase mSearchDatabase;
    private List<String> mSuggestionList;
    private ArrayAdapter mSuggestionAdapter;

    private Helper mHelper;

    private boolean mInAnimation = false;

    private int mSource;

    public SearchBar(Context context) {
        super(context);
        init(context);
    }

    public SearchBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SearchBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mSearchDatabase = SearchDatabase.getInstance(getContext());

        setRadius(UiUtils.dp2pix(context, 2));
        setCardElevation(UiUtils.dp2pix(context, 2));
        setCardBackgroundColor(Color.WHITE);

        LayoutInflater.from(context).inflate(R.layout.widget_search_bar, this);
        mMenuButton = (SimpleImageView) findViewById(R.id.search_menu);
        mTitleTextView = (TextView) findViewById(R.id.search_title);
        mActionButton = (SimpleImageView) findViewById(R.id.search_action);
        mEditText = (SearchEditText) findViewById(R.id.search_edit_text);
        mList = (ListView) findViewById(R.id.search_bar_list);

        mViewTransition = new ViewTransition(mTitleTextView, mEditText);

        mDrawerArrowDrawable = new DrawerArrowDrawable(getContext());
        mAddDeleteDrawable = new AddDeleteDrawable(getContext());

        mTitleTextView.setOnClickListener(this);
        mMenuButton.setDrawable(mDrawerArrowDrawable);
        mMenuButton.setOnClickListener(this);
        mActionButton.setDrawable(mAddDeleteDrawable);
        mActionButton.setOnClickListener(this);
        mEditText.setSearchEditTextListener(this);
        mEditText.setOnEditorActionListener(this);
        mEditText.addTextChangedListener(this);

        // Get base height
        ViewUtils.measureView(this, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mBaseHeight = getMeasuredHeight();

        mSuggestionList = new ArrayList<>();
        // TODO Use custom view
        mSuggestionAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1, mSuggestionList);
        mList.setAdapter(mSuggestionAdapter);
        mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String suggestion = mSuggestionList.get(position);
                mEditText.setText(suggestion);
                mEditText.setSelection(suggestion.length());
            }
        });

        // TODO get source from config
        setSource(Config.getEhSource());
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Messenger.getInstance().register(Constants.MESSENGER_ID_EH_SOURCE, this);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Messenger.getInstance().unregister(Constants.MESSENGER_ID_EH_SOURCE, this);
    }

    private void updateSuggestions() {
        String prefix = mEditText.getText().toString();
        String[] suggestions = mSearchDatabase.getSuggestions(prefix);
        mSuggestionList.clear();
        Collections.addAll(mSuggestionList, suggestions);
        mSuggestionAdapter.notifyDataSetChanged();
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    public void setSource(int source) {
        if (mSource != source) {
            Resources resources = getContext().getResources();
            Drawable searchImage = resources.getDrawable(R.drawable.ic_search);
            SpannableStringBuilder ssb = new SpannableStringBuilder("   ");
            ssb.append(String.format(resources.getString(R.string.search_bar_hint),
                    EhClient.getReadableHost(source)));
            int textSize = (int) (mEditText.getTextSize() * 1.25);
            searchImage.setBounds(0, 0, textSize, textSize);
            ssb.setSpan(new ImageSpan(searchImage), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            mEditText.setHint(ssb);

            mSource = source;
        }
    }

    public void setHelper(Helper helper) {
        mHelper = helper;
    }

    public String getText() {
        return mEditText.getText().toString();
    }

    @Override
    public void onClick(View v) {
        if (v == mTitleTextView) {
            mHelper.onClickTitle();
        } else if (v == mMenuButton) {
            if (mState == STATE_NORMAL) {
                mHelper.onClickMenu();
            } else {
                mHelper.onClickArrow();
            }
        } else if (v == mActionButton) {
            if (mState == STATE_NORMAL) {
                mHelper.onClickAdvanceSearch();
            } else {
                mEditText.setText("");
            }
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (v == mEditText) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                String query = mEditText.getText().toString();
                mHelper.onApplySearch(query);
                return true;
            }
        }
        return false;
    }

    public void setState(int state) {
        setState(state, true);
    }

    public void setState(int state, boolean animation) {
        if (mState != state) {
            int oldState = mState;
            mState = state;

            switch (oldState) {
                default:
                case STATE_NORMAL:
                    mViewTransition.showView(1);
                    mEditText.requestFocus();
                    mDrawerArrowDrawable.setArrow(animation ? ANIMATE_TIME : 0);
                    mAddDeleteDrawable.setDelete(animation ? ANIMATE_TIME : 0);
                    if (state == STATE_SEARCH_LIST) {
                        showImeAndSuggestionsList(animation);
                    }
                    break;
                case STATE_SEARCH:
                    if (state == STATE_NORMAL) {
                        mViewTransition.showView(0);
                        mDrawerArrowDrawable.setMenu(animation ? ANIMATE_TIME : 0);
                        mAddDeleteDrawable.setAdd(animation ? ANIMATE_TIME : 0);
                    } else if (state == STATE_SEARCH_LIST) {
                        showImeAndSuggestionsList(animation);
                    }
                    break;
                case STATE_SEARCH_LIST:
                    hideImeAndSuggestionsList(animation);
                    if (state == STATE_NORMAL) {
                        mViewTransition.showView(0);
                        mDrawerArrowDrawable.setMenu(animation ? ANIMATE_TIME : 0);
                        mAddDeleteDrawable.setAdd(animation ? ANIMATE_TIME : 0);
                    }
                    break;
            }
        }
    }

    private void showImeAndSuggestionsList() {
        showImeAndSuggestionsList(true);
    }

    private void showImeAndSuggestionsList(boolean animation) {
        // Show ime
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mEditText, 0);
        // update suggestion for show suggestions list
        updateSuggestions();
        // Show suggestions list
        if (animation) {
            ObjectAnimator oa = ObjectAnimator.ofFloat(this, "progress", 1f);
            oa.setDuration(ANIMATE_TIME);
            oa.addListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    ViewUtils.setVisibility(mList, View.VISIBLE);
                    mInAnimation = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mInAnimation = false;
                }
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                oa.setAutoCancel(true);
            }
            oa.start();
        } else {
            ViewUtils.setVisibility(mList, View.VISIBLE);
            setProgress(1f);
        }
    }

    private void hideImeAndSuggestionsList() {
        hideImeAndSuggestionsList(true);
    }

    private void hideImeAndSuggestionsList(boolean animation) {
        // Hide ime
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.getWindowToken(), 0);
        // Hide suggestions list
        if (animation) {
            ObjectAnimator oa = ObjectAnimator.ofFloat(this, "progress", 0f);
            oa.setDuration(ANIMATE_TIME);
            oa.addListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mInAnimation = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    ViewUtils.setVisibility(mList, View.GONE);
                    mInAnimation = false;
                }
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                oa.setAutoCancel(true);
            }
            oa.start();
        } else {
            setProgress(0f);
            ViewUtils.setVisibility(mList, View.GONE);
        }
    }

    public void setTitle(String title) {
        mTitleTextView.setText(title);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mList.getVisibility() == View.VISIBLE) {
            mWidth = right - left;
            mHeight = bottom - top;
        }
    }

    @SuppressWarnings("unused")
    public void setProgress(float progress) {
        mProgress = progress;
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getProgress() {
        return mProgress;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mInAnimation) {
            final int state = canvas.save();
            float bottom = MathUtils.lerp(mBaseHeight, mHeight, mProgress);
            mPath.rewind();
            mPath.addRect(0f, 0f, mWidth, bottom, Path.Direction.CW);
            canvas.clipPath(mPath);
            super.draw(canvas);
            canvas.restoreToCount(state);
        } else {
            super.draw(canvas);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Empty
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Empty
    }

    @Override
    public void afterTextChanged(Editable s) {
        updateSuggestions();
    }

    @Override
    public void onClick() {
        mHelper.onSearchEditTextClick();
    }

    @Override
    public void onBackPressed() {
        mHelper.onBackPressed();
    }

    @Override
    public void onReceive(int id, Object obj) {
        if (id == Constants.MESSENGER_ID_EH_SOURCE) {
            if (obj instanceof Integer) {
                int source = (Integer) obj;
                setSource(source);
            }
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState());
        state.putInt(STATE_KEY_STATE, mState);
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle savedState = (Bundle) state;
            super.onRestoreInstanceState(savedState.getParcelable(STATE_KEY_SUPER));
            setState(savedState.getInt(STATE_KEY_STATE), false);
        }
    }

    public interface Helper {
        void onClickTitle();
        void onClickMenu();
        void onClickArrow();
        void onClickAdvanceSearch();
        void onSearchEditTextClick();
        void onApplySearch(String query);
        void onBackPressed();
    }
}
