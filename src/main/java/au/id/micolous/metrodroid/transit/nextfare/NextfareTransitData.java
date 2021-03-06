/*
 * NextfareTransitData.java
 *
 * Copyright 2015-2018 Michael Farrell <micolous+git@gmail.com>
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
package au.id.micolous.metrodroid.transit.nextfare;

import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.text.SpannableString;
import android.util.Log;

import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import au.id.micolous.farebot.R;
import au.id.micolous.metrodroid.card.classic.ClassicBlock;
import au.id.micolous.metrodroid.card.classic.ClassicCard;
import au.id.micolous.metrodroid.card.classic.ClassicCardTransitFactory;
import au.id.micolous.metrodroid.card.classic.ClassicSector;
import au.id.micolous.metrodroid.transit.CardInfo;
import au.id.micolous.metrodroid.transit.TransitBalance;
import au.id.micolous.metrodroid.transit.TransitBalanceStored;
import au.id.micolous.metrodroid.transit.TransitCurrency;
import au.id.micolous.metrodroid.transit.TransitData;
import au.id.micolous.metrodroid.transit.TransitIdentity;
import au.id.micolous.metrodroid.transit.Trip;
import au.id.micolous.metrodroid.transit.nextfare.record.NextfareBalanceRecord;
import au.id.micolous.metrodroid.transit.nextfare.record.NextfareConfigRecord;
import au.id.micolous.metrodroid.transit.nextfare.record.NextfareRecord;
import au.id.micolous.metrodroid.transit.nextfare.record.NextfareTopupRecord;
import au.id.micolous.metrodroid.transit.nextfare.record.NextfareTransactionRecord;
import au.id.micolous.metrodroid.transit.nextfare.record.NextfareTravelPassRecord;
import au.id.micolous.metrodroid.ui.HeaderListItem;
import au.id.micolous.metrodroid.ui.ListItem;
import au.id.micolous.metrodroid.util.Utils;
import au.id.micolous.metrodroid.xml.ImmutableByteArray;

/**
 * Generic transit data type for Cubic Nextfare.
 * https://github.com/micolous/metrodroid/wiki/Cubic-Nextfare-MFC
 *
 * @author Michael Farrell
 */
public class NextfareTransitData extends TransitData {

    private static final String NAME = "Nextfare";
    public static final Creator<NextfareTransitData> CREATOR = new Creator<NextfareTransitData>() {
        public NextfareTransitData createFromParcel(Parcel parcel) {
            return new NextfareTransitData(parcel, "USD");
        }

        public NextfareTransitData[] newArray(int size) {
            return new NextfareTransitData[size];
        }
    };
    public static final ClassicCardTransitFactory FALLBACK_FACTORY = new NextFareTransitFactory();
    @VisibleForTesting
    public static final ImmutableByteArray MANUFACTURER = ImmutableByteArray.Companion.fromHex(
           "16181A1B1C1D1E1F"
    );
    private static final String TAG = "NextfareTransitData";
    protected NextfareConfigRecord mConfig = null;
    protected boolean mHasUnknownStations = false;
    private final long mSerialNumber;
    private final ImmutableByteArray mSystemCode;
    private final ImmutableByteArray mBlock2;
    private final int mBalance;
    private final List<NextfareTrip> mTrips;
    private final List<NextfareSubscription> mSubscriptions;
    @NonNull
    private final String mCurrency;

    protected NextfareTransitData(Parcel parcel, @NonNull String currency) {
        mSerialNumber = parcel.readLong();
        mBalance = parcel.readInt();
        mTrips = new ArrayList<>();
        parcel.readTypedList(mTrips, NextfareTrip.CREATOR);
        mSubscriptions = new ArrayList<>();
        parcel.readTypedList(mSubscriptions, NextfareSubscription.CREATOR);
        mSystemCode = ImmutableByteArray.Companion.fromParcel(parcel);
        mBlock2 = ImmutableByteArray.Companion.fromParcel(parcel);
        mCurrency = currency;

        mConfig = new NextfareConfigRecord(parcel);
    }

    public NextfareTransitData(ClassicCard card) {
        this(card, "USD");
    }

    protected NextfareTransitData(ClassicCard card, @NonNull String currency) {
        mCurrency = currency;

        ImmutableByteArray serialData = card.getSector(0).getBlock(0).getData();
        mSerialNumber = serialData.byteArrayToLongReversed(0, 4);

        ImmutableByteArray magicData = card.getSector(0).getBlock(1).getData();
        mSystemCode = magicData.copyOfRange(9, 15);
        Log.d(TAG, "SystemCode = " + mSystemCode);
        mBlock2 = card.getSector(0).getBlock(2).getData();
        Log.d(TAG, "Block2 = " + mBlock2);

        List<NextfareRecord> records = new ArrayList<>();

        for (ClassicSector sector : card.getSectors()) {
            for (ClassicBlock block : sector.getBlocks()) {
                if (sector.getIndex() == 0 || block.getIndex() == 3) {
                    // Ignore sector 0 (preamble) and block 3 (mifare keys/ACL)
                    continue;
                }

                //noinspection StringConcatenation
                Log.d(TAG, "Sector " + sector.getIndex() + " / Block " + block.getIndex());
                NextfareRecord record = NextfareRecord.recordFromBytes(
                        block.getData(), sector.getIndex(), block.getIndex(), getTimezone());

                if (record != null) {
                    records.add(record);
                }
            }
        }

        // Now do a first pass for metadata and balance information.
        List<NextfareBalanceRecord> balances = new ArrayList<>();
        List<NextfareTrip> trips = new ArrayList<>();
        List<NextfareSubscription> subscriptions = new ArrayList<>();
        List<NextfareTransactionRecord> taps = new ArrayList<>();
        List<NextfareTravelPassRecord> passes = new ArrayList<>();

        for (NextfareRecord record : records) {
            if (record instanceof NextfareBalanceRecord) {
                balances.add((NextfareBalanceRecord) record);
            } else if (record instanceof NextfareTopupRecord) {
                NextfareTopupRecord topupRecord = (NextfareTopupRecord) record;

                trips.add(newRefill(topupRecord));
            } else if (record instanceof NextfareTransactionRecord) {
                taps.add((NextfareTransactionRecord) record);
            } else if (record instanceof NextfareTravelPassRecord) {
                passes.add((NextfareTravelPassRecord) record);
            } else if (record instanceof NextfareConfigRecord) {
                mConfig = (NextfareConfigRecord) record;
            }
        }

        if (balances.size() >= 1) {
            Collections.sort(balances);
            NextfareBalanceRecord balance = balances.get(0);

            if (balances.size() == 2) {
                // If the version number overflowed, we need to swap these around.
                if (balances.get(0).getVersion() >= 240 && balances.get(1).getVersion() <= 10) {
                    balance = balances.get(1);
                }

            }

            mBalance = balance.getBalance();
            if (balance.hasTravelPassAvailable()) {
                subscriptions.add(newSubscription(balance));
            }
        } else
            mBalance = 0;

        if (taps.size() >= 1) {
            Collections.sort(taps);

            // Lets figure out the trips.
            int i = 0;

            while (taps.size() > i) {
                NextfareTransactionRecord tapOn = taps.get(i);

                //Log.d(TAG, "TapOn @" + Utils.isoDateTimeFormat(tapOn.getTimestamp()));
                // Start by creating an empty trip
                NextfareTrip trip = newTrip();

                // Put in the metadatas
                trip.mJourneyId = tapOn.getJourney();
                trip.mStartTime = tapOn.getTimestamp();
                trip.mStartStation = tapOn.getStation();
                trip.mModeInt = tapOn.getMode();
                trip.mContinuation = tapOn.isContinuation();
                trip.mCost = -tapOn.getValue();

                if (!mHasUnknownStations && trip.mStartStation != 0 && trip.getStartStation() != null && trip.getStartStation().isUnknown()) {
                    mHasUnknownStations = true;
                }

                // Peek at the next record and see if it is part of
                // this journey
                if (taps.size() > i + 1 && shouldMergeJourneys(tapOn, taps.get(i + 1))) {
                    // There is a tap off.  Lets put that data in
                    NextfareTransactionRecord tapOff = taps.get(i + 1);
                    //Log.d(TAG, "TapOff @" + Utils.isoDateTimeFormat(tapOff.getTimestamp()));

                    trip.mEndTime = tapOff.getTimestamp();
                    trip.mEndStation = tapOff.getStation();
                    trip.mCost -= tapOff.getValue();

                    if (!mHasUnknownStations && trip.mEndStation != 0 && trip.getEndStation() != null && trip.getEndStation().isUnknown()) {
                        mHasUnknownStations = true;
                    }

                    // Increment to skip the next record
                    i++;
                } else {
                    // There is no tap off. Journey is probably in progress, or the agency doesn't
                    // do tap offs.
                }

                trips.add(trip);

                // Increment to go to the next record
                i++;
            }

            // Now sort the trips array
            Collections.sort(trips, new Trip.Comparator());

            // Trips are normally in reverse order, put them in forward order
            Collections.reverse(trips);

            /*
            // Check if the oldest trip was negative. That indicates that we probably got a tap-off
            // without a matching tap-on, and we should handle differently.
            //
            // Normally we silently drop the extra top-up record contained in the "tap" array, so
            // negative things shouldn't pop up here at all.
            NextfareTrip lastTrip = trips.get(trips.size() - 1);
            if (lastTrip.mCost < 0) {
                // We have a negative cost.  We should clean up...
                lastTrip.mEndTime = lastTrip.mStartTime;
                lastTrip.mEndStation = lastTrip.mStartStation;
                lastTrip.mStartTime = null;
                lastTrip.mStartStation = 0;
            }
            */

        }

        if (passes.size() >= 1) {
            Collections.sort(passes);
            subscriptions.add(newSubscription(passes.get(0)));
        }

        mSubscriptions = subscriptions;
        mTrips = trips;
    }

    protected static class NextFareTransitFactory implements ClassicCardTransitFactory {
        @Override
        public boolean earlyCheck(@NonNull List<ClassicSector> sectors) {
            ImmutableByteArray blockData = sectors.get(0).getBlock(1).getData();
            return blockData.copyOfRange(1, 9).contentEquals(MANUFACTURER);
        }

        @Override
        public TransitIdentity parseTransitIdentity(@NonNull ClassicCard card) {
            return parseTransitIdentity(card, NAME);
        }

        protected TransitIdentity parseTransitIdentity(ClassicCard card, String name) {
            ImmutableByteArray serialData = card.getSector(0).getBlock(0).getData();
            long serialNumber = serialData.byteArrayToLongReversed(0, 4);
            return new TransitIdentity(name, formatSerialNumber(serialNumber));
        }

        @Override
        public TransitData parseTransitData(@NonNull ClassicCard classicCard) {
            return new NextfareTransitData(classicCard);
        }

        @Override
        public int earlySectors() {
            return 1;
        }
    }

    @NonNls
    protected static String formatSerialNumber(long serialNumber) {
        String s = "0160 " + Utils.formatNumber(serialNumber, " ", 4, 4, 3);
        s += Utils.calculateLuhn(s.replaceAll(" ", ""));
        return s;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(mSerialNumber);
        parcel.writeInt(mBalance);
        parcel.writeTypedList(mTrips);
        parcel.writeTypedList(mSubscriptions);
        mSystemCode.parcelize(parcel, i);
        mBlock2.parcelize(parcel, i);
        mConfig.writeToParcel(parcel, i);
    }

    /**
     * Called when it needs to be determined if two TapRecords are part of the same journey.
     * <p>
     * Normally this should never need to be overwritten, except in the case that the Journey ID and
     * travel mode is not enough to break up the two journeys.
     * <p>
     * If the agency NEVER records tap-off events, this should always return false.
     *
     * @param tap1 The first tap to compare.
     * @param tap2 The second tap to compare.
     * @return true if the journeys should be merged.
     */
    protected boolean shouldMergeJourneys(NextfareTransactionRecord tap1, NextfareTransactionRecord tap2) {
        return tap1.getJourney() == tap2.getJourney() && tap1.getMode() == tap2.getMode();
    }

    /**
     * Allows you to override the constructor for new trips, to hook in your own station ID code.
     *
     * @return Subclass of NextfareTrip.
     */
    protected NextfareTrip newTrip() {
        return new NextfareTrip(mCurrency, null);
    }

    /**
     * Allows you to override the constructor for new refills, to hook in your own code.
     *
     * @param record Record to parse
     * @return Subclass of NextfareTrip
     */
    protected NextfareTrip newRefill(NextfareTopupRecord record) {
        return new NextfareTrip(record, mCurrency, null);
    }

    /**
     * Allows you to override the constructor for new subscriptions, to hook in your own code.
     * <p>
     * This method is used for existing / past travel passes.
     *
     * @param record Record to parse
     * @return Subclass of NextfareSubscription
     */
    protected NextfareSubscription newSubscription(NextfareTravelPassRecord record) {
        return new NextfareSubscription(record);
    }

    /**
     * Allows you to override the constructor for new subscriptions, to hook in your own code.
     * <p>
     * This method is used for new, unused travel passes.
     *
     * @param record Record to parse
     * @return Subclass of NextfareSubscription
     */
    protected NextfareSubscription newSubscription(NextfareBalanceRecord record) {
        return new NextfareSubscription(record);
    }

    /**
     * Allows you to override the timezone used for all dates and times. Default timezone is the
     * current Android OS timezone.
     *
     * @return TimeZone for the card.
     */
    protected TimeZone getTimezone() {
        // If we don't know the timezone, assume it is Android local timezone.
        return TimeZone.getDefault();
    }

    @Override
    public String getSerialNumber() {
        return formatSerialNumber(mSerialNumber);
    }

    @Override
    public List<NextfareTrip> getTrips() {
        return mTrips;
    }

    @Override
    public TransitBalance getBalance() {
        if (mConfig != null) {
            return new TransitBalanceStored(new TransitCurrency(mBalance, mCurrency), getTicketClass(), mConfig.getExpiry());
        } else
            return new TransitBalanceStored(new TransitCurrency(mBalance, mCurrency));
    }

    public String getTicketClass() {
        if (mConfig != null) {
            return Utils.localizeString(R.string.nextfare_ticket_class, mConfig.getTicketType());
        }

        return null;
    }

    @Override
    public List<NextfareSubscription> getSubscriptions() {
        return mSubscriptions;
    }

    @Override
    public String getCardName() {
        return NAME;
    }

    /**
     * If true, then the unknown stations banner should be shown.
     * <p>
     * In the base Nextfare implementation, this is meaningless (all stations are unknown), so this
     * always returns false. But in subclasses, this should return mHasUnknownStations.
     *
     * @return always false - do not show unknown stations UI
     */
    @Override
    public boolean hasUnknownStations() {
        return false;
    }

    @Override
    public List<ListItem> getInfo() {
        List<ListItem> items = new ArrayList<>();

        items.add(new HeaderListItem(R.string.nextfare));
        items.add(new ListItem(R.string.nextfare_system_code, mSystemCode.toHexDump()));

        // The Los Angeles Tap and Minneapolis Go-To cards have the same system code, but different
        // data in Block 2.
        items.add(new ListItem(
                new SpannableString(Utils.localizeString(R.string.block_title_format, 2)),
                mBlock2.toHexDump()));

        return items;
    }

}
