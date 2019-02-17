/*
 * CardInfo.java
 *
 * Copyright 2011 Eric Butler
 * Copyright 2015-2019 Michael Farrell
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.id.micolous.metrodroid.transit;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.content.res.AppCompatResources;
import android.util.Log;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import au.id.micolous.metrodroid.card.CardType;
import au.id.micolous.metrodroid.card.calypso.CalypsoApplication;
import au.id.micolous.metrodroid.card.china.ChinaCard;
import au.id.micolous.metrodroid.card.classic.ClassicCard;
import au.id.micolous.metrodroid.card.desfire.DesfireCard;
import au.id.micolous.metrodroid.card.felica.FelicaCard;
import au.id.micolous.metrodroid.card.ksx6923.KSX6923Application;
import au.id.micolous.metrodroid.card.ultralight.UltralightCard;
import au.id.micolous.metrodroid.transit.ezlink.EZLinkTransitData;
import au.id.micolous.metrodroid.util.Utils;

/**
 * List of all the cards we know about.
 */

@SuppressWarnings("WeakerAccess")
public final class CardInfo {
    @NonNull
    public static List<CardInfo> getAllCardsAlphabetical() {
        List<CardInfo> ret = new ArrayList<>();
        List<CardTransitFactory<?>> allFactories = new ArrayList<>();
        allFactories.addAll(ClassicCard.Companion.getAllFactories());
        allFactories.addAll(CalypsoApplication.getAllFactories());
        allFactories.addAll(DesfireCard.getAllFactories());
        allFactories.addAll(FelicaCard.getAllFactories());
        allFactories.addAll(UltralightCard.getAllFactories());
        allFactories.addAll(ChinaCard.getAllFactories());
        allFactories.addAll(KSX6923Application.getAllFactories());
        for (CardTransitFactory<?> factory : allFactories) {
            ret.addAll(factory.getAllCards());
        }
        ret.addAll(EZLinkTransitData.ALL_CARD_INFOS);
        Collator collator = Collator.getInstance();
        Collections.sort(ret, (a, b) -> collator.compare(a.getName(), b.getName()));
        return ret;
    }

    @DrawableRes
    private final int mImageId;
    @DrawableRes
    private final int mImageAlphaId;
    private final String mName;
    @StringRes
    private final int mLocationId;
    private final CardType mCardType;
    private final boolean mKeysRequired;
    private final boolean mPreview;
    @StringRes
    private final int mResourceExtraNote;

    private CardInfo(@DrawableRes int imageId, String name, @StringRes int locationId, CardType cardType, boolean keysRequired, boolean preview, @StringRes int resourceExtraNote,  @DrawableRes int imageAlphaId) {
        mImageId = imageId;
        mImageAlphaId = imageAlphaId;
        mName = name;
        mLocationId = locationId;
        mCardType = cardType;
        mKeysRequired = keysRequired;
        mPreview = preview;
        mResourceExtraNote = resourceExtraNote;
    }

    public boolean hasBitmap() {
        return mImageAlphaId != 0 || mImageId != 0;
    }

    public Drawable getDrawable(Context ctxt) {
        if (mImageAlphaId != 0) {
            Log.d("CardInfo", String.format(Locale.ENGLISH, "masked bitmap %x / %x", mImageId, mImageAlphaId));
            Resources res = ctxt.getResources();
            return new BitmapDrawable(res, Utils.getMaskedBitmap(res, mImageId, mImageAlphaId));
        } else {
            return AppCompatResources.getDrawable(ctxt, mImageId);
        }
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @StringRes
    public int getLocationId() {
        return mLocationId;
    }

    public CardType getCardType() {
        return mCardType;
    }

    public boolean getKeysRequired() {
        return mKeysRequired;
    }

    /**
     * Indicates if the card is a "preview" / beta decoder, with possibly
     * incomplete / incorrect data.
     *
     * @return true if this is a beta version of the card decoder.
     */
    public boolean getPreview() {
        return mPreview;
    }

    @StringRes
    public int getResourceExtraNote() {
        return mResourceExtraNote;
    }


    public static final class Builder {
        @DrawableRes
        private int mImageId;
        @DrawableRes
        private int mImageAlphaId;
        private String mName;
        @StringRes
        private int mLocationId;
        private CardType mCardType;
        private boolean mKeysRequired;
        private boolean mPreview;
        @StringRes
        private int mResourceExtraNote;

        public Builder() {
        }

        @NonNull
        public CardInfo build() {
            return new CardInfo(mImageId, mName, mLocationId, mCardType, mKeysRequired, mPreview, mResourceExtraNote, mImageAlphaId);
        }

        public Builder setImageId(@DrawableRes int id) {
            return setImageId(id, 0);
        }

        public Builder setImageId(@DrawableRes int id, @DrawableRes int alpha) {
            mImageId = id;
            mImageAlphaId = alpha;
            return this;
        }

        public Builder setName(String name) {
            mName = name;
            return this;
        }

        public Builder setLocation(@StringRes int id) {
            mLocationId = id;
            return this;
        }

        public Builder setCardType(CardType type) {
            mCardType = type;
            return this;
        }

        public Builder setKeysRequired() {
            mKeysRequired = true;
            return this;
        }

        public Builder setPreview() {
            mPreview = true;
            return this;
        }

        public Builder setExtraNote(@StringRes int id) {
            mResourceExtraNote = id;
            return this;
        }

    }
}
