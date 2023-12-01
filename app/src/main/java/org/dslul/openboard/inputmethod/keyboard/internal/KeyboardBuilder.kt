/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.widget.Toast
import org.dslul.openboard.inputmethod.annotations.UsedForTesting
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.Keyboard
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.KeyboardParser
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.XmlKeyboardParser
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import org.dslul.openboard.inputmethod.latin.BuildConfig
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.define.DebugFlags
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.utils.sumOf
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

// TODO: Write unit tests for this class.
open class KeyboardBuilder<KP : KeyboardParams>(protected val mContext: Context, @JvmField val mParams: KP) {
    @JvmField
    protected val mResources: Resources
    private var mCurrentY = 0

    private var mLeftEdge = false
    private var mTopEdge = false
    private var mRightEdgeKey: Key? = null
    private lateinit var keysInRows: ArrayList<ArrayList<KeyParams>>

    init {
        val res = mContext.resources
        mResources = res
        mParams.GRID_WIDTH = res.getInteger(R.integer.config_keyboard_grid_width)
        mParams.GRID_HEIGHT = res.getInteger(R.integer.config_keyboard_grid_height)
    }

    fun setAllowRedundantMoreKeys(enabled: Boolean) {
        mParams.mAllowRedundantMoreKeys = enabled
    }

    fun loadFromAssets(id: KeyboardId): KeyboardBuilder<KP>? {
        mParams.mId = id
        addLocaleKeyTextsToParams(mContext, mParams, Settings.getInstance().current.mShowMoreKeys)
        try {
            keysInRows = KeyboardParser.parseFromAssets(mParams, mContext) ?: return null
        } catch (e: Throwable) {
            if (DebugFlags.DEBUG_ENABLED || BuildConfig.DEBUG)
                Toast.makeText(mContext, "error parsing keyboard: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "loading $id from assets failed", e)
            return null
        }
        determineAbsoluteValues()
        return this

        // todo: further plan
        //  migrate other languages/layouts to this style
        //   thai and lao number rows... they should probably have none, can't do it generically
        //    so handle it like korean
        //   languageMoreKeys for bengali and hindi layouts are completely mixed up -> maybe need to use layoutMoreKeys... but that's not nice
        //   bangla (india) has different period & symbols label (should it really be latin?)
        //    maybe need separate key text files for _IN and _BD
        //   test whether the layouts really are the same
        //    comparing params with both parsers looks good, see list of detected differences below
        //    still need to check moreKeys, there will be many more differences that might just be minor
        //    write down a list of differences, ask users to open issues if they don't like them
        //   some keyboard_layout_set have supportedScript that is enum synced with script id in ScriptUtils
        //    that's one more reason for using language tags...
        //    but currently it's still read from xml outside the keyboard parser, so that's fine for now
        //   issues:
        //    armenian bottom row (and some more): functional keys could be narrower
        //    rtl parentheses hint label (urdu and more)
        //    urdu and others: no labels because the moreKeys are languageMoreKeys -> need the moreKeys setting soon (at least setting to show first language moreKey if no symbol)
        //     setting: symbol morekey(s): layout default, take from symbols, layout default but fill from symbols if empty
        //     setting: hint label: from symbol morekey only, symbol but language if none, first choice on long press
        //     (later): setting which moreKeys to prefer (default: symbol or important language, always symbol, always language)
        //     (later): setting whether to show duplicate moreKeys (describe properly what it actually does)
        //     and have some setting to enable configuring this per locale (in language settings -> potentially should not be a dialog any more but a fragment?)
        //    label flags are horrible to "decompile" when viewing a layout
        //  migrate pcqwerty to this style
        //   this will be more complicated...
        //   linked shift keys might be easy
        //   distance below number row with high row, but reduced key height?
        //   consider settings key needs to disappear if device is locked
        //   ignore number row setting?
        //  migrate keypad layouts to this style
        //   will need more configurable layout definition -> another parser, or do it with compatible jsons
        //  make the remove duplicate moreKey thing an option?
        //   why is it on for serbian (latin), but not for german (german)?
        //   only nordic and serbian_qwertz layouts have it disabled, default is enabled
        //   -> add the option, but disable it by default for all layouts
        //  migrate emoji layouts to this style
        //   emojis are defined in that string array, should be simple to handle
        //   parsing could be done into a single row, which is then split as needed
        //    this might help with split layout (no change in key size, but in number of rows)
        //   write another parser, it should already consider split
        //   add a setting to display all emojis (and use emojiv2 or emojicompat or whatever is necessary)
        //    mention in subtitle that they may not be displayed properly, depending on the app you're writing in
        //  migrate moreKeys and moreSuggestions to this style?
        //   at least they should not make use of the KeyTextsSet/Table (and of the XmlKeyboardParser?)
        //  remove the old parser
        //   then finally the spanish/german/swiss/nordic layouts can be removed and replaced by some hasExtraKeys parameter
        //   also the eo check could then be removed
        //   and maybe the language -> layout thing could be moved to assets? and maybe even here the extra keys could be defined...
        //    should be either both in method.xml, or both in assets (actually method might be more suitable)
        //  allow users to define their own layouts (maybe do everything else first?)
        //   need to solve the scaling issue with number row and 5 row keyboards
        //   write up how things work for users, also regarding language more keys
        //    readme, maybe also some "help" button in a dialog
        //   some sort of proper UI, or simply text input?
        //    better text import for the start because of much work
        //    ui follows later (consider that users need to be able to start from existing layouts!)
        //   some warning if more than 2 or 3 characters on a single label
        //    currently can't resize keys, but could set autoXScale (does only decrease size, never increase)
        //   careful about moreKeys: if moreKeys don't fit on screen, parser throws an exception!
        //    need to somehow test for this
        //    is that autoColumnOrder thing a workaround for that?
        //     still would crash for a single huge label
        //   potential keyspec parsing issues:
        //    MoreKeySpec constructor does things like KeySpecParser.getLabel and others
        //     these work with special characters like | and \ doing things depending on their position
        //     if used wrongly, things can crash
        //     -> maybe disable this style of parsing when creating MoreKeySpec of a user-provided layout
        //      or also for the simple layouts, because there is no need to have it in layouts
        //    does the same issue apply to normal key labels?
        //   popup and (single key) long press preview rescale the label on x only, which may deform emojis
        //   does glide typing work with multiple letters on one key? if not, users should be notified
        //   maybe allow users to define their own symbol and shift-symbol layouts
        //   allow users to import layouts, which essentially just fills the text from a file
        //    add setting to use moreKeys from symbol layout (always, never, only if none defined)
        //     should also have sth related to hint, because hint and start morekey maybe should stay
        //  option to add extra keys for all layouts?

        // labelFlags should be set correctly
        //  alignHintLabelToBottom: on lxx and rounded themes, but did not find what it actually does...
        //  alignIconToBottom: space_key_for_number_layout
        //  alignLabelOffCenter: number keys in phone layout
        //  fontNormal: turkish (rows 1 and 2 only), .com, emojis, numModeKeyStyle, a bunch of non-latin languages
        //    -> switches to normal typeface, only relevant for holo which has bold
        //  fontMonoSpace: unused
        //  fontDefault: keyExclamationQuestion, a bunch of "normal" keys in fontNormal layouts like thai
        //    -> switches to default defined typeface, useful e.g. if row has fontNormal
        //  followKeyLargeLetterRatio: number keys in number/phone/numpad layouts
        //  followKeyLetterRatio: mode keys in number layouts, some keys in some non-latin layouts
        //  followKeyLabelRatio: enter key, some keys in phone layout (same as followKeyLetterRatio + followKeyLargeLetterRatio)
        //  followKeyHintLabelRatio: unused directly (but includes some others)
        //  hasPopupHint: basically the long-pressable functional keys
        //  hasShiftedLetterHint: period key and some keys on pcqwerty
        //  hasHintLabel: number keys in number layouts
        //  autoXScale: com key, action keys, some on phone layout, some non-latin languages
        //  autoScale: only one single letter in khmer layout (includes autoXScale)
        //  preserveCase: action key + more keys, com key, shift keys
        //  shiftedLetterActivated: period and some keys on pcqwerty, tablet only
        //  fromCustomActionLabel: action key with customLabelActionKeyStyle -> check parser where to get this info
        //  followFunctionalTextColor: number mode keys, action key
        //  keepBackgroundAspectRatio: lxx and rounded action more keys, lxx no-border action and emoji, moreKeys keyboard view
        //  disableKeyHintLabel: keys in pcqwerty row 1 and number row
        //  disableAdditionalMoreKeys: only keys in pcqwerty row 1 so there is no number row -> not necessary with the new layouts, just remove it completely
        //  maybe remove some of the flags? or keep supporting them?
        //  for pcqwerty: hasShiftedLetterHint -> hasShiftedLetterHint|shiftedLetterActivated when shift is enabled, need to consider if the flag is used
        //   actually period key also has shifted letter hint
    }

    fun loadFromXml(xmlId: Int, id: KeyboardId): KeyboardBuilder<KP> {
        if (Settings.getInstance().current.mUseNewKeyboardParsing
            && this::class == KeyboardBuilder::class // otherwise this will apply to moreKeys and moreSuggestions, and then some parameters are off
        ) {
            if (loadFromAssets(id) != null) {
                if (!DebugFlags.DEBUG_ENABLED)
                    return this
                // comparison of old and new parser below, remove once testing is complete
                val keysInRowsFromXml = XmlKeyboardParser(xmlId, mParams, mContext).use { keyboardParser ->
                    keyboardParser.parseKeyboard()
                }
                if (keysInRowsFromXml.size != keysInRows.size) {
                    Log.w(TAG, "different sizes: ${keysInRows.size} vs ${keysInRowsFromXml.size}")
                    return this
                }
                keysInRowsFromXml.forEachIndexed { index, xmlRow ->
                    val row = keysInRows[index].filter { !it.isSpacer }
                    val xmlRow2 = xmlRow.filter { !it.isSpacer }
                    if (row.size != xmlRow2.size) {
                        Log.w(TAG, "different row sizes in row ${index + 1}")
                        return@forEachIndexed
                    }
                    xmlRow2.forEachIndexed { index1, xmlParams ->
                        // todo: compare moreKeys (and if different, check whether it's just the order)
                        //  also check holo, there might be different default parameters
                        //  and compare tablet layouts (how to best force it for both parsers?)
                        val keyParams = row[index1]
                        if (keyParams.mLabel != xmlParams.mLabel)
                            // currency keys (shift symbol) arranged differently
                            // obviously number row differences with possibly localized variants
                            Log.w(TAG, "label different: ${keyParams.mLabel} vs ${xmlParams.mLabel}")
                        if (keyParams.mCode != xmlParams.mCode)
                            Log.w(TAG, "code different: ${keyParams.mCode} vs ${xmlParams.mCode}")
                        if (keyParams.mIconId != xmlParams.mIconId)
                            Log.w(TAG, "icon different: ${keyParams.mIconId} vs ${xmlParams.mIconId}")
                        if (keyParams.mHintLabel != xmlParams.mHintLabel)
                            // extra and number keys are the difference so far
                            // persian has small difference
                            // khmer has some difference
                            // urdu has a lot of difference
                            Log.w(TAG, "hint label different: ${keyParams.mHintLabel} vs ${xmlParams.mHintLabel}")
                        if (keyParams.mLabelFlags != xmlParams.mLabelFlags && keyParams.mCode != 10)
                            // in symbol layout
                            //  my version has disableHintLabel for all
                            //  original has LABEL_FLAGS_HAS_POPUP_HINT on < > in shift symbol (but there is no popup)
                            // armenian, arabic, bangla,... and many with "symbols" original shift and delete have LABEL_FLAGS_FONT_NORMAL, mine not (but my period has)
                            // malayalam delete also has LABEL_FLAGS_AUTO_X_SCALE, mine not
                            // tamil & telugu my delete has LABEL_FLAGS_AUTO_X_SCALE, original not
                            // hindi / marathi: -5 has 10, mine has 0 -> delete and LABEL_FLAGS_FONT_NORMAL
                            Log.w(TAG, "label flags different for ${keyParams.mLabel} / ${keyParams.mCode}: ${keyParams.mLabelFlags.toString(16)} vs ${xmlParams.mLabelFlags.toString(16)}")
                    }
                }
                return this
            }
        }
        mParams.mId = id
        // loading a keyboard should set default params like mParams.readAttributes(mContext, attrs);
        // attrs may be null, then default values are used (looks good for "normal" keyboards)
        try {
            XmlKeyboardParser(xmlId, mParams, mContext).use { keyboardParser ->
                keysInRows = keyboardParser.parseKeyboard()
            }
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "keyboard XML parse error", e)
            throw IllegalArgumentException(e.message, e)
        } catch (e: IOException) {
            Log.w(TAG, "keyboard XML parse error", e)
            throw RuntimeException(e.message, e)
        }
        return this
    }

    @UsedForTesting
    fun disableTouchPositionCorrectionDataForTest() {
        mParams.mTouchPositionCorrection.setEnabled(false)
    }

    fun setProximityCharsCorrectionEnabled(enabled: Boolean) {
        mParams.mProximityCharsCorrectionEnabled = enabled
    }

    open fun build(): Keyboard {
        if (Settings.getInstance().current.mIsSplitKeyboardEnabled
                && mParams.mId.mElementId in KeyboardId.ELEMENT_ALPHABET..KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            addSplit()
        }
        addKeysToParams()
        return Keyboard(mParams)
    }

    // determine key size and positions using relative width and height
    // ideally this should not change anything
    //  but it does a little, depending on how float -> int is done (cast or round, and when to sum up gaps and width)
    //  still should not be more than a pixel difference
    private fun determineAbsoluteValues() {
        var currentY = mParams.mTopPadding.toFloat()
        for (row in keysInRows) {
            if (row.isEmpty()) continue
            fillGapsWithSpacers(row)
            var currentX = 0f
            row.forEach {
                it.setDimensionsFromRelativeSize(currentX, currentY)
                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "setting size and position for ${it.mLabel}, ${it.mCode}: x ${currentX.toInt()}, w ${it.mFullWidth.toInt()}")
                currentX += it.mFullWidth
            }
            // need to truncate to int here, otherwise it may end up one pixel lower than original
            // though actually not truncating would be more correct... but that's already an y / height issue somewhere in Key
            // todo (later): round, and do the change together with the some thing in Key(KeyParams keyParams)
            currentY += row.first().mFullHeight.toInt()
        }
    }

    // necessary for adjusting widths and positions properly
    // without adding spacers whose width can then be adjusted, we would have to deal with keyXPos,
    // which is more complicated than expected
    private fun fillGapsWithSpacers(row: MutableList<KeyParams>) {
        if (mParams.mId.mElementId !in KeyboardId.ELEMENT_ALPHABET..KeyboardId.ELEMENT_SYMBOLS_SHIFTED) return
        if (row.isEmpty()) return
        if (row.all { it.xPos == 0f }) return // need existing xPos to determine gaps
        var currentX = 0f + mParams.mLeftPadding
        var i = 0
        while (i < row.size) {
            val currentKeyXPos = row[i].xPos
            if (currentKeyXPos > currentX) {
                // insert spacer
                val spacer = KeyParams.newSpacer(mParams, (currentKeyXPos - currentX) / mParams.mBaseWidth)
                spacer.yPos = row[i].yPos
                row.add(i, spacer)
                i++
                currentX += currentKeyXPos - currentX
            }
            currentX += row[i].mFullWidth
            i++
        }
        if (currentX < mParams.mOccupiedWidth) {
            // insert spacer
            val spacer = KeyParams.newSpacer(mParams, (mParams.mOccupiedWidth - currentX) / mParams.mBaseWidth)
            spacer.yPos = row.last().yPos
            row.add(spacer)
        }
    }

    private fun addSplit() {
        val spacerRelativeWidth = Settings.getInstance().current.mSpacerRelativeWidth
        // adjust gaps for the whole keyboard, so it's the same for all rows
        mParams.mRelativeHorizontalGap *= 1f / (1f + spacerRelativeWidth)
        mParams.mHorizontalGap = (mParams.mRelativeHorizontalGap * mParams.mId.mWidth).toInt()
        var maxWidthBeforeSpacer = 0f
        var maxWidthAfterSpacer = 0f
        for (row in keysInRows) {
            fillGapsWithSpacers(row)
            val y = row.first().yPos // all have the same y, so this is fine
            val relativeWidthSum = row.sumOf { it.mRelativeWidth } // sum up relative widths
            val spacer = KeyParams.newSpacer(mParams, spacerRelativeWidth)
            // insert spacer before first key that starts right of the center (also consider gap)
            var insertIndex = row.indexOfFirst { it.xPos + it.mFullWidth / 3 > mParams.mOccupiedWidth / 2 }
                .takeIf { it > -1 } ?: (row.size / 2) // fallback should never be needed, but better than having an error
            if (row.any { it.mCode == Constants.CODE_SPACE }) {
                val spaceLeft = row.single { it.mCode == Constants.CODE_SPACE }
                reduceSymbolAndActionKeyWidth(row)
                insertIndex = row.indexOf(spaceLeft) + 1
                val widthBeforeSpace = row.subList(0, insertIndex - 1).sumOf { it.mRelativeWidth }
                val widthAfterSpace = row.subList(insertIndex, row.size).sumOf { it.mRelativeWidth }
                val spaceLeftWidth = (maxWidthBeforeSpacer - widthBeforeSpace).coerceAtLeast(mParams.mDefaultRelativeKeyWidth)
                val spaceRightWidth = (maxWidthAfterSpacer - widthAfterSpace).coerceAtLeast(mParams.mDefaultRelativeKeyWidth)
                val spacerWidth = spaceLeft.mRelativeWidth + spacerRelativeWidth - spaceLeftWidth - spaceRightWidth
                if (spacerWidth > 0.05f) {
                    // only insert if the spacer has a reasonable width
                    val spaceRight = KeyParams(spaceLeft)
                    spaceLeft.mRelativeWidth = spaceLeftWidth
                    spaceRight.mRelativeWidth = spaceRightWidth
                    spacer.mRelativeWidth = spacerWidth
                    row.add(insertIndex, spaceRight)
                    row.add(insertIndex, spacer)
                } else {
                    // otherwise increase space width, so other keys are resized properly
                    spaceLeft.mRelativeWidth += spacerWidth
                }
            } else {
                val widthBeforeSpacer = row.subList(0, insertIndex).sumOf { it.mRelativeWidth }
                val widthAfterSpacer = row.subList(insertIndex, row.size).sumOf { it.mRelativeWidth }
                maxWidthBeforeSpacer = maxWidthBeforeSpacer.coerceAtLeast(widthBeforeSpacer)
                maxWidthAfterSpacer = maxWidthAfterSpacer.coerceAtLeast(widthAfterSpacer)
                row.add(insertIndex, spacer)
            }
            // re-calculate relative widths
            val relativeWidthSumNew = row.sumOf { it.mRelativeWidth }
            val widthFactor = relativeWidthSum / relativeWidthSumNew
            // re-calculate absolute sizes and positions
            var currentX = 0f
            row.forEach {
                it.mRelativeWidth *= widthFactor
                it.setDimensionsFromRelativeSize(currentX, y)
                currentX += it.mFullWidth
            }
        }
    }

    // reduce width of symbol and action key if in the row, and add this width to space to keep other key size constant
    private fun reduceSymbolAndActionKeyWidth(row: ArrayList<KeyParams>) {
        val spaceKey = row.first { it.mCode == Constants.CODE_SPACE }
        val symbolKey = row.firstOrNull { it.mCode == Constants.CODE_SWITCH_ALPHA_SYMBOL }
        val symbolKeyWidth = symbolKey?.mRelativeWidth ?: 0f
        if (symbolKeyWidth > mParams.mDefaultRelativeKeyWidth) {
            val widthToChange = symbolKey!!.mRelativeWidth - mParams.mDefaultRelativeKeyWidth
            symbolKey.mRelativeWidth -= widthToChange
            spaceKey.mRelativeWidth += widthToChange
        }
        val actionKey = row.firstOrNull { it.mBackgroundType == Key.BACKGROUND_TYPE_ACTION }
        val actionKeyWidth = actionKey?.mRelativeWidth ?: 0f
        if (actionKeyWidth > mParams.mDefaultRelativeKeyWidth * 1.1f) { // allow it to stay a little wider
            val widthToChange = actionKey!!.mRelativeWidth - mParams.mDefaultRelativeKeyWidth * 1.1f
            actionKey.mRelativeWidth -= widthToChange
            spaceKey.mRelativeWidth += widthToChange
        }
    }

    private fun startKeyboard() {
        mCurrentY += mParams.mTopPadding
        mTopEdge = true
    }

    private fun startRow() {
        mLeftEdge = true
        mRightEdgeKey = null
    }

    private fun endRow() {
        val rightEdgeKey = mRightEdgeKey
        if (rightEdgeKey != null) {
            rightEdgeKey.markAsRightEdge(mParams)
            mCurrentY += rightEdgeKey.height + rightEdgeKey.verticalGap
            mRightEdgeKey = null
        }
        mLeftEdge = false
        mTopEdge = false
    }

    private fun endKey(key: Key) {
        mParams.onAddKey(key)
        if (mLeftEdge) {
            key.markAsLeftEdge(mParams)
            mLeftEdge = false
        }
        if (mTopEdge) {
            key.markAsTopEdge(mParams)
        }
        mRightEdgeKey = key
    }

    private fun endKeyboard() {
        mParams.removeRedundantMoreKeys()
        // {@link #parseGridRows(XmlPullParser,boolean)} may populate keyboard rows higher than
        // previously expected.
        val actualHeight = mCurrentY - mParams.mVerticalGap + mParams.mBottomPadding
        mParams.mOccupiedHeight = Math.max(mParams.mOccupiedHeight, actualHeight)
    }

    private fun addKeysToParams() {
        // need to reset it, we need to sum it up to get the height nicely
        // (though in the end we could just not touch it at all, final used value is the same as the one before resetting)
        mCurrentY = 0
        startKeyboard()
        for (row in keysInRows) {
            startRow()
            for (keyParams in row) {
                endKey(keyParams.createKey())
            }
            endRow()
        }
        endKeyboard()
    }

    companion object {
        private const val TAG = "Keyboard.Builder"
    }
}
