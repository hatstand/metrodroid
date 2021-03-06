/*
 * KMTTrip.java
 *
 * Copyright 2018 Bondan Sumbodo <sybond@gmail.com>
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

package au.id.micolous.metrodroid.transit.kmt;

import android.os.Parcel;
import android.support.annotation.Nullable;

import java.util.Calendar;
import java.util.GregorianCalendar;

import au.id.micolous.farebot.R;
import au.id.micolous.metrodroid.card.felica.FelicaBlock;
import au.id.micolous.metrodroid.transit.Station;
import au.id.micolous.metrodroid.transit.TransitCurrency;
import au.id.micolous.metrodroid.transit.Trip;
import au.id.micolous.metrodroid.util.StationTableReader;
import au.id.micolous.metrodroid.util.Utils;
import au.id.micolous.metrodroid.xml.ImmutableByteArray;

public class KMTTrip extends Trip {
    public static final Creator<KMTTrip> CREATOR = new Creator<KMTTrip>() {
        public KMTTrip createFromParcel(Parcel parcel) {
            return new KMTTrip(parcel);
        }

        public KMTTrip[] newArray(int size) {
            return new KMTTrip[size];
        }
    };
    private final int mProcessType;
    private final int mSequenceNumber;
    private final Calendar mTimestamp;
    private final int mTransactionAmount;
    private final int mEndGateCode;
    private static final String KMT_STR = "kmt";

    private static Calendar calcDate(ImmutableByteArray data) {
        int fulloffset = data.byteArrayToInt(0, 4);
        if (fulloffset == 0) {
            return null;
        }
        Calendar c = new GregorianCalendar(KMTTransitData.TIME_ZONE);
        c.setTimeInMillis(KMTTransitData.KMT_EPOCH);
        c.add(Calendar.SECOND, fulloffset);
        return c;
    }

    private static Station getStation(int code) {
        return StationTableReader.getStation(KMT_STR, code);
    }

    public KMTTrip(FelicaBlock block) {
        ImmutableByteArray data = block.getData();
        mProcessType = data.get(12) & 0xff;
        mSequenceNumber = data.byteArrayToInt(13, 3);
        mTimestamp = calcDate(data);
        mTransactionAmount = data.byteArrayToInt(4, 4);
        mEndGateCode = data.byteArrayToInt(8, 2);
    }

    @Nullable
    @Override
    public Station getStartStation() {
        // Normally, only the end station is recorded.  But top-ups only have a "starting" station.
        if (mProcessType == 0 || mProcessType == 2) {
            return getStation(mEndGateCode);
        }
        return null;
    }

    @Nullable
    @Override
    public Station getEndStation() {
        if (mProcessType == 0 || mProcessType == 2) {
            // "Ending station" doesn't make sense for Ticket Machines or Point-of-sale
            return null;
        }
        return getStation(mEndGateCode);
    }

    private KMTTrip(Parcel parcel) {
        mProcessType = parcel.readInt();
        mSequenceNumber = parcel.readInt();
        mTimestamp = Utils.unparcelCalendar(parcel);
        mTransactionAmount = parcel.readInt();
        mEndGateCode = parcel.readInt();
    }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mProcessType);
        parcel.writeInt(mSequenceNumber);
        Utils.parcelCalendar(parcel, mTimestamp);
        parcel.writeInt(mTransactionAmount);
        parcel.writeInt(mEndGateCode);
    }

    public Mode getMode() {
        switch (mProcessType) {
            case 0:
                return Mode.TICKET_MACHINE;
            case 1:
                return Mode.TRAIN;
            case 2:
                return Mode.POS;
            default:
                return Mode.OTHER;
        }
    }

    @Override
    public Calendar getStartTimestamp() {
        return mTimestamp;
    }

    @Nullable
    @Override
    public TransitCurrency getFare() {
        if (mProcessType != 1) {
            return TransitCurrency.IDR(mTransactionAmount).negate();
        }
        return TransitCurrency.IDR(mTransactionAmount);
    }

    @Override
    public String getAgencyName(boolean isShort) {
        return Utils.localizeString(R.string.kmt_agency);
    }

    public int describeContents() {
        return 0;
    }

}
