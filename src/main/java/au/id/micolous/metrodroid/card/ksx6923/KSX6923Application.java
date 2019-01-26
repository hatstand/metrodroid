/*
 * TmoneyCard.java
 *
 * Copyright 2018 Google
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

package au.id.micolous.metrodroid.card.ksx6923;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.VisibleForTesting;
import android.text.SpannableString;
import android.util.Log;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;

import au.id.micolous.farebot.R;
import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface;
import au.id.micolous.metrodroid.card.iso7816.ISO7816Application;
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationFactory;
import au.id.micolous.metrodroid.card.iso7816.ISO7816File;
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol;
import au.id.micolous.metrodroid.card.iso7816.ISO7816Record;
import au.id.micolous.metrodroid.card.iso7816.ISO7816Selector;
import au.id.micolous.metrodroid.card.iso7816.ISO7816TLV;
import au.id.micolous.metrodroid.transit.TransitData;
import au.id.micolous.metrodroid.transit.TransitIdentity;
import au.id.micolous.metrodroid.transit.snapper.SnapperTransitData;
import au.id.micolous.metrodroid.transit.tmoney.TMoneyTransitData;
import au.id.micolous.metrodroid.ui.ListItem;
import au.id.micolous.metrodroid.ui.ListItemRecursive;
import au.id.micolous.metrodroid.util.Utils;
import au.id.micolous.metrodroid.xml.Base64String;
import au.id.micolous.metrodroid.xml.ImmutableByteArray;

/**
 * Implements the T-Money ISO 7816 application.  This is used by T-Money in South Korea, and
 * Snapper Plus cards in Wellington, New Zealand.
 */
public class KSX6923Application extends ISO7816Application {
    private static final String TAG = KSX6923Application.class.getSimpleName();

    public static final List<ImmutableByteArray> APP_NAME = Collections.singletonList(
            ImmutableByteArray.Companion.fromHex("d4100000030001")
    );

    public static final ImmutableByteArray FILE_NAME = ImmutableByteArray.Companion.fromHex("d4100000030001");

    private static final KSX6923CardTransitFactory[] FACTORIES = {
            SnapperTransitData.Companion.getFACTORY(),
            TMoneyTransitData.FACTORY
    };

    private static final byte INS_GET_BALANCE = 0x4c;
    private static final byte INS_GET_RECORD = 0x78;
    private static final byte BALANCE_RESP_LEN = 4;
    public static final byte TRANSACTION_FILE = 4;
    private static final String TYPE = "tmoney";

    @Element(name = "balance")
    private Integer mBalance;

    @SuppressWarnings("NullableProblems") // Handled by XML serialiser
    @NonNull
    @VisibleForTesting
    @ElementList(name = "snapper-records", required = false, empty = false)
    private List<Base64String> mSnapperData;

    public static List<KSX6923CardTransitFactory> getAllFactories() {
        return Arrays.asList(FACTORIES);
    }

    @Override
    public TransitIdentity parseTransitIdentity() {
        for (KSX6923CardTransitFactory factory : FACTORIES) {
            if (factory.check(this)) {
                return factory.parseTransitIdentity(this);
            }
        }

        // Falback
        return TMoneyTransitData.FACTORY.parseTransitIdentity(this);
    }

    @NonNull
    @Override
    public TransitData parseTransitData() {
        for (KSX6923CardTransitFactory factory : FACTORIES) {
            if (factory.check(this)) {
                TransitData d = factory.parseTransitData(this);
                if (d != null) {
                    return d;
                }
            }
        }

        // Fallback
        return TMoneyTransitData.FACTORY.parseTransitData(this);
    }

    private KSX6923Application() { /* For XML Serializer */ }

    private KSX6923Application(ISO7816Application.ISO7816Info appData, int balance) {
        super(appData);
        mBalance = balance;
    }

    public List<Base64String> getSnapperData() {
        return Collections.unmodifiableList(mSnapperData);
    }

    public List<ListItem> getRawData() {
        final List<Base64String> snapperData = getSnapperData();
        final ArrayList<ListItem> sli = new ArrayList<>();
        sli.add(ListItemRecursive.collapsedValue("T-Money Balance",
                new SpannableString(Utils.intToHex(mBalance))));

        for (int i=0; i<snapperData.size(); i++) {
            final Base64String d = snapperData.get(i);
            @StringRes final int r = d.isAllZero() || d.isAllFF() ?
                    R.string.page_title_format_empty : R.string.page_title_format;

            sli.add(ListItemRecursive.collapsedValue(r, d.toHexDump()));
        }

        return Collections.unmodifiableList(sli);
    }


    public static final ISO7816ApplicationFactory FACTORY = new ISO7816ApplicationFactory() {
        @NonNull
        @Override
        public List<ImmutableByteArray> getApplicationNames() {
            return APP_NAME;
        }

        @NonNull
        @Override
        public String getType() {
            return TYPE;
        }

        /**
         * Dumps a TMoney card in the field.
         * @param appData ISO7816 app info of the tag.
         * @param protocol Tag to dump.
         * @return TMoneyCard of the card contents. Returns null if an unsupported card is in the
         *         field.
         */
        @Nullable
        @Override
        public List<ISO7816Application> dumpTag(@NonNull ISO7816Protocol protocol, @NonNull ISO7816Info appData, @NonNull TagReaderFeedbackInterface feedbackInterface) {
            ImmutableByteArray balanceResponse;


            try {
                feedbackInterface.updateStatusText(Utils.localizeString(R.string.card_reading_type,
                        TMoneyTransitData.CARD_INFO.getName()));
                feedbackInterface.updateProgressBar(0, 37);
                feedbackInterface.showCardType(TMoneyTransitData.CARD_INFO);
                balanceResponse = protocol.sendRequest(ISO7816Protocol.CLASS_90, INS_GET_BALANCE,
                        (byte) 0, (byte) 0, BALANCE_RESP_LEN);
                feedbackInterface.updateProgressBar(1, 37);

                // TODO: Understand this data
                for (int i = 0; i <= 0xf; i++) {
                    Log.d(TAG, "sending snapper get = " + i);
                    ImmutableByteArray ba = protocol.sendRequest(ISO7816Protocol.CLASS_90, INS_GET_RECORD, (byte)i, (byte)0, (byte)0x10);
                }


                for (int i = 1; i < 6; i++) {
                    try {
                        appData.dumpFileSFI(protocol, i, 0);
                        //appData.dumpFile(protocol, ISO7816Selector.makeSelector(FILE_NAME, i), 0);
                    } catch (Exception e) {
                        //noinspection StringConcatenation
                        Log.w(TAG, "Caught exception on file 4200/"  + Integer.toHexString(i) + ": " + e);
                    }
                    feedbackInterface.updateProgressBar(32+i, 37);
                }
                try {
                    appData.dumpFile(protocol, ISO7816Selector.makeSelector(0xdf00), 0);
                } catch (Exception e) {
                    //noinspection StringConcatenation
                    Log.w(TAG, "Caught exception on file df00: " + e);
                }
            } catch (Exception e) {
                //noinspection StringConcatenation
                Log.w(TAG, "Got exception " + e);
                return null;
            }

            return Collections.singletonList(new KSX6923Application(appData,
                    Utils.byteArrayToInt(balanceResponse, 0, BALANCE_RESP_LEN)));
        }

        @Override
        public Class<? extends ISO7816Application> getCardClass(@NonNull String type) {
            return KSX6923Application.class;
        }
    };

    public int getBalance() {
        return mBalance;
    }

    @NonNull
    public List<ISO7816Record> getTransactionRecords() {
        ISO7816File f = getSfiFile(TRANSACTION_FILE);
        if (f == null) // Old T-Money scans
            f = getFile(ISO7816Selector.makeSelector(FILE_NAME, TRANSACTION_FILE));

        if (f == null)
            return Collections.emptyList();
        return f.getRecords();
    }

    private ImmutableByteArray getSerialTag() {
        return ISO7816TLV.INSTANCE.findBERTLV(getAppData(), "b0", false);
    }

    public String getSerial() {
        return Utils.groupString(getSerialTag().getHexString(4, 8), " ", 4, 4, 4);
    }

    public Calendar getIssueDate() {
        final ImmutableByteArray serialTag = getSerialTag();
        final GregorianCalendar c = new GregorianCalendar(Utils.UTC);

        c.set(serialTag.convertBCDtoInteger(17, 2),
                serialTag.convertBCDtoInteger(19, 1) - 1,
                1, 0, 0, 0);
        return c;
    }
}
