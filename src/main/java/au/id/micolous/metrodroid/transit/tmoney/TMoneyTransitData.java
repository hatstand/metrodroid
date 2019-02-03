/*
 * TMoneyTransitData.java
 *
 * Copyright 2018 Google
 * Copyright 2018-2019 Michael Farrell <micolous+git@gmail.com>
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

package au.id.micolous.metrodroid.transit.tmoney;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.SpannedString;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import au.id.micolous.farebot.R;
import au.id.micolous.metrodroid.card.CardType;
import au.id.micolous.metrodroid.card.iso7816.ISO7816File;
import au.id.micolous.metrodroid.card.iso7816.ISO7816Record;
import au.id.micolous.metrodroid.card.iso7816.ISO7816Selector;
import au.id.micolous.metrodroid.card.ksx6923.KSX6923Application;
import au.id.micolous.metrodroid.card.ksx6923.KSX6923CardTransitFactory;
import au.id.micolous.metrodroid.card.ksx6923.KSX6923PurseInfo;
import au.id.micolous.metrodroid.transit.CardInfo;
import au.id.micolous.metrodroid.transit.TransitBalance;
import au.id.micolous.metrodroid.transit.TransitCurrency;
import au.id.micolous.metrodroid.transit.TransitData;
import au.id.micolous.metrodroid.transit.TransitIdentity;
import au.id.micolous.metrodroid.transit.Trip;
import au.id.micolous.metrodroid.ui.ListItem;
import au.id.micolous.metrodroid.util.Utils;

import static au.id.micolous.metrodroid.card.ksx6923.KSX6923Application.TRANSACTION_FILE;

public class TMoneyTransitData extends TransitData {
    public static final Parcelable.Creator<TMoneyTransitData> CREATOR = new Parcelable.Creator<TMoneyTransitData>() {
        public TMoneyTransitData createFromParcel(Parcel parcel) {
            return new TMoneyTransitData(parcel);
        }

        public TMoneyTransitData[] newArray(int size) {
            return new TMoneyTransitData[size];
        }
    };

    public static final CardInfo CARD_INFO = new CardInfo.Builder()
            .setImageId(R.drawable.tmoney_card)
            .setName(Utils.localizeString(R.string.card_name_tmoney))
            .setLocation(R.string.location_seoul)
            .setCardType(CardType.ISO7816)
            .setPreview()
            .build();

    public static final long INVALID_DATETIME = 0xffffffffffffffL;

    public final static KSX6923CardTransitFactory FACTORY = new KSX6923CardTransitFactory() {
        @Override
        public TransitIdentity parseTransitIdentity(@NotNull KSX6923Application app) {
            return new TransitIdentity(Utils.localizeString(R.string.card_name_tmoney), app.getSerial());
        }

        @Override
        public TransitData parseTransitData(@NotNull KSX6923Application app) {
            return new TMoneyTransitData(app);
        }

        @NotNull
        @Override
        public List<CardInfo> getAllCards() {
            return Collections.singletonList(CARD_INFO);
        }

        @Override
        public boolean check(@NotNull KSX6923Application card) {
            return true;
        }
    };

    protected final int mBalance;
    private final KSX6923PurseInfo mPurseInfo;
    private final List<? extends Trip> mTrips;

    public TMoneyTransitData(@NotNull KSX6923Application tMoneyCard) {
        super();
        mBalance = tMoneyCard.getBalance();
        mPurseInfo = tMoneyCard.getPurseInfo();
        mTrips = parseTrips(tMoneyCard);
    }

    @NotNull
    protected List<? extends Trip> parseTrips(@NotNull KSX6923Application tMoneyCard) {
        List<TMoneyTrip> trips = new ArrayList<>();
        for (ISO7816Record record : getTransactionRecords(tMoneyCard)) {
            TMoneyTrip t = TMoneyTrip.Companion.parseTrip(record.getData());
            if (t == null)
                continue;
            trips.add(t);
        }

        return Collections.unmodifiableList(trips);
    }

    @NotNull
    protected List<ISO7816Record> getTransactionRecords(@NotNull KSX6923Application tMoneyCard) {
        ISO7816File f = tMoneyCard.getSfiFile(TRANSACTION_FILE);
        if (f == null)
            f = tMoneyCard.getFile(ISO7816Selector.makeSelector(KSX6923Application.FILE_NAME, TRANSACTION_FILE));
        if (f == null)
            return Collections.emptyList();
        return f.getRecords();
    }

    @Nullable
    @Override
    public TransitBalance getBalance() {
        return mPurseInfo.buildTransitBalance(TransitCurrency.KRW(mBalance), null);
    }


    @Override
    public String getSerialNumber() {
        return mPurseInfo.getSerial();
    }

    @NotNull
    @Override
    public String getCardName() {
        return Utils.localizeString(R.string.card_name_tmoney);
    }

    @Override
    public List<ListItem> getInfo() {
        List<ListItem> items = new ArrayList<>();
        items.add(new ListItem("Card Identification Code",
                Utils.intToHex(mPurseInfo.getCardType())));
        items.add(new ListItem("Encryption Algorithm",
                Utils.intToHex(mPurseInfo.getAlg())));
        items.add(new ListItem("Keyset Version",
                Utils.intToHex(mPurseInfo.getVk())));
        items.add(new ListItem("Issuer ID",
                Utils.intToHex(mPurseInfo.getIdCenter())));

        items.add(new ListItem("IDTR",
                Utils.longToHex(mPurseInfo.getIdtr())));
        items.add(new ListItem("User code",
                Utils.intToHex(mPurseInfo.getUserCode())));
        items.add(new ListItem("Disrate",
                Utils.intToHex(mPurseInfo.getDisRate())));
        items.add(new ListItem("Max balance",
                Long.toString(mPurseInfo.getBalMax())));
        items.add(new ListItem("branch code",
                Utils.intToHex(mPurseInfo.getBra())));
        items.add(new ListItem("mmax",
                Long.toString(mPurseInfo.getMmax())));
        items.add(new ListItem("tcode",
                Utils.intToHex(mPurseInfo.getTcode())));
        items.add(new ListItem("ccode",
                Utils.intToHex(mPurseInfo.getCcode())));
        items.add(new ListItem("rfu",
                mPurseInfo.getRfu().getHexString()));


        return items;
    }

    @Override
    public List<? extends Trip> getTrips() {
        return mTrips;
    }

    private TMoneyTransitData(Parcel p) {
        mBalance = p.readInt();
        mPurseInfo = p.readParcelable(KSX6923PurseInfo.class.getClassLoader());
        //noinspection unchecked
        mTrips = p.readArrayList(TMoneyTrip.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mBalance);
        dest.writeParcelable(mPurseInfo, 0);
        dest.writeList(mTrips);
    }

    @NotNull
    protected KSX6923PurseInfo getPurseInfo() {
        return mPurseInfo;
    }

    public static TransitIdentity parseTransitIdentity(KSX6923Application card) {
        return new TransitIdentity(Utils.localizeString(R.string.card_name_tmoney), card.getSerial());
    }
}
