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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import au.id.micolous.farebot.R;
import au.id.micolous.metrodroid.card.CardType;
import au.id.micolous.metrodroid.card.iso7816.ISO7816File;
import au.id.micolous.metrodroid.card.iso7816.ISO7816Record;
import au.id.micolous.metrodroid.card.iso7816.ISO7816Selector;
import au.id.micolous.metrodroid.card.ksx6923.KSX6923Application;
import au.id.micolous.metrodroid.card.ksx6923.KSX6923CardTransitFactory;
import au.id.micolous.metrodroid.transit.CardInfo;
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
        public TransitIdentity parseTransitIdentity(@NonNull KSX6923Application app) {
            return new TransitIdentity(Utils.localizeString(R.string.card_name_tmoney), app.getSerial());
        }

        @Override
        public TransitData parseTransitData(@NonNull KSX6923Application app) {
            return new TMoneyTransitData(app);
        }

        @NonNull
        @Override
        public List<CardInfo> getAllCards() {
            return Collections.singletonList(CARD_INFO);
        }

        @Override
        public boolean check(@NotNull KSX6923Application card) {
            return true;
        }
    };

    private final String mSerialNumber;
    protected final int mBalance;
    private final Calendar mDate;
    private final List<? extends Trip> mTrips;

    public TMoneyTransitData(@NonNull KSX6923Application tMoneyCard) {
        super();
        mSerialNumber = tMoneyCard.getSerial();
        mBalance = tMoneyCard.getBalance();
        mDate = tMoneyCard.getIssueDate();
        mTrips = parseTrips(tMoneyCard);
    }

    @NonNull
    protected List<? extends Trip> parseTrips(@NonNull KSX6923Application tMoneyCard) {
        List<TMoneyTrip> trips = new ArrayList<>();
        for (ISO7816Record record : getTransactionRecords(tMoneyCard)) {
            TMoneyTrip t = TMoneyTrip.Companion.parseTrip(record.getData());
            if (t == null)
                continue;
            trips.add(t);
        }

        return Collections.unmodifiableList(trips);
    }

    @NonNull
    protected List<ISO7816Record> getTransactionRecords(@NonNull KSX6923Application tMoneyCard) {
        ISO7816File f = tMoneyCard.getSfiFile(TRANSACTION_FILE);
        if (f == null)
            f = tMoneyCard.getFile(ISO7816Selector.makeSelector(KSX6923Application.FILE_NAME, TRANSACTION_FILE));
        if (f == null)
            return Collections.emptyList();
        return f.getRecords();
    }

    @Nullable
    @Override
    public TransitCurrency getBalance() {
        return TransitCurrency.KRW(mBalance);
    }


    @Override
    public String getSerialNumber() {
        return mSerialNumber;
    }

    @NonNull
    @Override
    public String getCardName() {
        return Utils.localizeString(R.string.card_name_tmoney);
    }

    @Override
    public List<ListItem> getInfo() {
        List<ListItem> items = new ArrayList<>();

        items.add(new ListItem(R.string.tmoney_date, Utils.longDateFormat(mDate)));

        return items;
    }

    @Override
    public List<? extends Trip> getTrips() {
        return mTrips;
    }

    private TMoneyTransitData(Parcel p) {
        mSerialNumber = p.readString();
        mBalance = p.readInt();
        mDate = Utils.unparcelCalendar(p);
        //noinspection unchecked
        mTrips = p.readArrayList(TMoneyTrip.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mSerialNumber);
        dest.writeInt(mBalance);
        Utils.parcelCalendar(dest, mDate);
        dest.writeList(mTrips);
    }

    public static TransitIdentity parseTransitIdentity(KSX6923Application card) {
        return new TransitIdentity(Utils.localizeString(R.string.card_name_tmoney), card.getSerial());
    }

    @Nullable
    public static Calendar parseHexDateTime(long val, @NonNull TimeZone tz) {
        if (val == INVALID_DATETIME)
            return null;
        GregorianCalendar g = new GregorianCalendar(tz);
        g.set(Utils.convertBCDtoInteger((int) (val >> 40)),
                Utils.convertBCDtoInteger((int) ((val >> 32) & 0xffL))-1,
                Utils.convertBCDtoInteger((int) ((val >> 24) & 0xffL)),
                Utils.convertBCDtoInteger((int) ((val >> 16) & 0xffL)),
                Utils.convertBCDtoInteger((int) ((val >> 8) & 0xffL)),
                Utils.convertBCDtoInteger((int) ((val) & 0xffL)));
        return g;
    }
}
