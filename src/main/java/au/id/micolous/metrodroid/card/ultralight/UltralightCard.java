/*
 * UltralightCard.java
 *
 * Copyright 2016-2018 Michael Farrell <micolous+git@gmail.com>
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
package au.id.micolous.metrodroid.card.ultralight;

import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import au.id.micolous.farebot.R;
import au.id.micolous.metrodroid.card.Card;
import au.id.micolous.metrodroid.card.CardType;
import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface;
import au.id.micolous.metrodroid.card.UnauthorizedException;
import au.id.micolous.metrodroid.card.UnsupportedTagException;
import au.id.micolous.metrodroid.transit.CardTransitFactory;
import au.id.micolous.metrodroid.transit.TransitData;
import au.id.micolous.metrodroid.transit.TransitIdentity;
import au.id.micolous.metrodroid.transit.clipper.ClipperUltralightTransitData;
import au.id.micolous.metrodroid.transit.ovc.OvcUltralightTransitFactory;
import au.id.micolous.metrodroid.transit.nextfare.ultralight.NextfareUnknownUltralightTransitData;
import au.id.micolous.metrodroid.transit.ventra.VentraUltralightTransitData;
import au.id.micolous.metrodroid.transit.yvr_compass.CompassUltralightTransitData;
import au.id.micolous.metrodroid.transit.troika.TroikaUltralightTransitData;
import au.id.micolous.metrodroid.transit.unknown.BlankUltralightTransitData;
import au.id.micolous.metrodroid.transit.unknown.UnauthorizedUltralightTransitData;
import au.id.micolous.metrodroid.ui.ListItem;
import au.id.micolous.metrodroid.ui.ListItemRecursive;
import au.id.micolous.metrodroid.util.Utils;
import au.id.micolous.metrodroid.xml.ImmutableByteArray;

import org.jetbrains.annotations.NonNls;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for reading MIFARE Ultralight / Ultralight C
 */
@Root(name = "card")
public class UltralightCard extends Card {
    private static final String TAG = "UltralightCard";
    private static final UltralightCardTransitFactory[] FACTORIES = {
            TroikaUltralightTransitData.FACTORY,
            CompassUltralightTransitData.FACTORY,
            VentraUltralightTransitData.FACTORY,
            // This must be after the checks for known Nextfare MFU deployments.
            NextfareUnknownUltralightTransitData.FACTORY,
            ClipperUltralightTransitData.FACTORY,
            new OvcUltralightTransitFactory(),
            BlankUltralightTransitData.FACTORY,
            // This check must be LAST.
            //
            // This is to throw up a warning whenever there is a card with all locked sectors
            UnauthorizedUltralightTransitData.FACTORY,
    };

    @ElementList(name = "pages")
    private List<UltralightPage> mPages;

    /**
     * This was previously used for Ultralight type checks in Metrodroid. However, this is based on
     * Android's type checks, which aren't great (see UltralightProtocol.getCardType. This should
     * not be used. This is included so that users with scans in the old XML format won't see an
     * error.
     */
    @SuppressWarnings("unused")
    @Deprecated
    @Keep
    @Attribute(name = "ultralightType", required = false)
    private int mDeprecatedUltralightType;

    @Attribute(name = "cardModel", required = false)
    @NonNls
    private String mCardModel;

    private UltralightCard() { /* For XML Serializer */ }

    public UltralightCard(ImmutableByteArray tagId, Calendar scannedAt, String cardModel, List<UltralightPage> pages) {
        super(CardType.MifareUltralight, tagId, scannedAt);
        mCardModel = cardModel;
        mPages = pages;
    }

    public static UltralightCard dumpTag(ImmutableByteArray tagId, Tag tag, TagReaderFeedbackInterface feedbackInterface) throws Exception {
        MifareUltralight tech = null;

        try {
            tech = MifareUltralight.get(tag);
            tech.connect();
            feedbackInterface.updateProgressBar(0, 1);
            feedbackInterface.updateStatusText(Utils.localizeString(R.string.mfu_detect));

            UltralightProtocol p = new UltralightProtocol(tech);
            UltralightProtocol.UltralightType t = p.getCardType();

            if (t.getPageCount() <= 0) {
                throw new UnsupportedTagException(new String[]{"Ultralight"}, "Unknown Ultralight type");
            }

            feedbackInterface.updateStatusText(Utils.localizeString(R.string.mfu_reading));
            feedbackInterface.showCardType(null);

            // Now iterate through the pages and grab all the datas
            int pageNumber = 0;
            byte[] pageBuffer = new byte[0];
            List<UltralightPage> pages = new ArrayList<>();
            boolean unauthorized = false;
            while (pageNumber < t.getPageCount()) {
                if (pageNumber % 4 == 0) {
                    feedbackInterface.updateProgressBar(pageNumber, t.getPageCount());
                    // Lets make a new buffer of data. (16 bytes = 4 pages * 4 bytes)
                    try {
                        pageBuffer = tech.readPages(pageNumber);
                        unauthorized = false;
                    } catch (IOException e) {
                        // Transceive failure, maybe authentication problem
                        unauthorized = true;
                        Log.d(TAG, String.format(Locale.ENGLISH, "Unable to read page %d", pageNumber), e);
                    }
                }

                // Now lets stuff this into some pages.
                if (!unauthorized) {
                    pages.add(UltralightPage.create(pageNumber, Arrays.copyOfRange(
                            pageBuffer,
                            (pageNumber % 4) * MifareUltralight.PAGE_SIZE,
                            ((pageNumber % 4) + 1) * MifareUltralight.PAGE_SIZE)));
                } else {
                    pages.add(new UnauthorizedUltralightPage(pageNumber));
                }
                pageNumber++;
            }

            // Now we have pages to stuff in the card.
            return new UltralightCard(tagId, GregorianCalendar.getInstance(), t.toString(),
                    pages);

        } finally {
            if (tech != null && tech.isConnected()) {
                tech.close();
            }
        }
    }

    public static UltralightCard dumpTagA(ImmutableByteArray tagId, Tag tag, TagReaderFeedbackInterface feedbackInterface) throws Exception {
        NfcA tech = null;

        try {
            tech = NfcA.get(tag);
            tech.connect();
            if (tech.getSak() != 0 || !Arrays.equals(tech.getAtqa(), new byte[]{0x44, 0x00}))
                return null;
            feedbackInterface.updateProgressBar(0, 1);

            feedbackInterface.updateStatusText(Utils.localizeString(R.string.mfu_reading));
            feedbackInterface.showCardType(null);

            // Now iterate through the pages and grab all the datas
            byte[] pageBuffer = new byte[0];
            while (true) {
                // Find first unread page
                int page = pageBuffer.length / 4;
                if (page >= 0x100)
                    break;
                // read command
                byte []rd = {0x30, (byte) page};
                byte []res;
                try {
                    res = tech.transceive(rd);
                } catch (Exception e) {
                    break;
                }
                pageBuffer = Utils.concatByteArrays(pageBuffer, res);
                feedbackInterface.updateProgressBar(pageBuffer.length, 1024);
            }

            int numPages = (pageBuffer.length / 4);
            int i;
            List<UltralightPage> pages = new ArrayList<>();
            for (i = 0; i < numPages; i++) {
                pages.add(UltralightPage.create(i, Arrays.copyOfRange(
                        pageBuffer, i * 4, (i+1) * 4)));
            }

            // Now we have pages to stuff in the card.
            return new UltralightCard(tagId, GregorianCalendar.getInstance(), "",
                    pages);

        } finally {
            if (tech != null && tech.isConnected()) {
                tech.close();
            }
        }
    }

    public static List<CardTransitFactory<UltralightCard>> getAllFactories() {
        return Arrays.asList(FACTORIES);
    }

    private UltralightCardTransitFactory findTransitFactory() {
        for (UltralightCardTransitFactory factory : FACTORIES) {
            try {
                if (factory.check(this))
                    return factory;
            } catch (IndexOutOfBoundsException | UnauthorizedException e) {
                /* Not the right factory. Just continue  */
            }
        }
        return null;
    }

    @Override
    public TransitIdentity parseTransitIdentity() {
        UltralightCardTransitFactory factory = findTransitFactory();
        if (factory == null)
            return null;
        return factory.parseTransitIdentity(this);
    }

    @Override
    public TransitData parseTransitData() {
        UltralightCardTransitFactory factory = findTransitFactory();
        if (factory == null)
            return null;
        return factory.parseTransitData(this);
    }

    public UltralightPage[] getPages() {
        return mPages.toArray(new UltralightPage[0]);
    }

    public UltralightPage getPage(int index) {
        return mPages.get(index);
    }

    public ImmutableByteArray readPages(int startPage, int pageCount) throws IndexOutOfBoundsException {
        ImmutableByteArray data = ImmutableByteArray.Companion.empty();
        for (int index = startPage; index < (startPage + pageCount); index++) {
            ImmutableByteArray blockData = getPage(index).getData();
            data = data.plus(blockData);
        }
        return data;
    }

    /**
     * Get the model of Ultralight card this is.
     *
     * Note: this is NULL on dumps from old versions of Metrodroid.
     *
     * @return Model of Ultralight card this is.
     */
    @NonNls
    @Nullable
    public String getCardModel() {
        return mCardModel;
    }

    @NonNull
    @Override
    public List<ListItem> getRawData() {
        List<ListItem> li = new ArrayList<>();
        for (UltralightPage sector : mPages) {
            String sectorIndexString = Integer.toHexString(sector.getIndex());

            if (sector instanceof UnauthorizedUltralightPage) {
                li.add(ListItemRecursive.collapsedValue(Utils.localizeString(
                        R.string.unauthorized_page_title_format, sectorIndexString),
                        null, null));
            } else {
                li.add(ListItemRecursive.collapsedValue(Utils.localizeString(
                        R.string.page_title_format, sectorIndexString),
                        null, sector.getData().toHexDump()));
            }
        }
        return li;
    }
}
