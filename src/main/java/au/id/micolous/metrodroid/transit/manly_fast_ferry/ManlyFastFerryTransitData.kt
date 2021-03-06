/*
 * ManlyFastFerryTransitData.kt
 *
 * Copyright 2015-2019 Michael Farrell <micolous+git@gmail.com>
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
package au.id.micolous.metrodroid.transit.manly_fast_ferry

import au.id.micolous.farebot.R
import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.card.classic.ClassicCardTransitFactory
import au.id.micolous.metrodroid.transit.CardInfo
import au.id.micolous.metrodroid.transit.erg.ErgTransitData
import au.id.micolous.metrodroid.transit.erg.ErgTransitFactory
import au.id.micolous.metrodroid.transit.erg.record.ErgPurseRecord
import java.util.*

/**
 * Transit data type for Manly Fast Ferry Smartcard (Sydney, AU).
 *
 * This transit card is a system made by ERG Group (now Videlli Limited / Vix Technology).
 *
 * Note: This is a distinct private company who run their own ferry service to Manly, separate to
 * Transport for NSW's Manly Ferry service.
 *
 * Documentation of format: https://github.com/micolous/metrodroid/wiki/Manly-Fast-Ferry
 */
class ManlyFastFerryTransitData private constructor(card: ClassicCard) :
        ErgTransitData(card, CURRENCY) {

    override fun newTrip(purse: ErgPurseRecord, epoch: Int) =
            ManlyFastFerryTransaction(purse, epoch)

    override fun getCardName() = NAME
    override fun getTimezone(): TimeZone = TIME_ZONE

    companion object {
        private const val NAME = "Manly Fast Ferry"
        private const val AGENCY_ID = 0x0227
        internal val TIME_ZONE = TimeZone.getTimeZone("Australia/Sydney")
        internal const val CURRENCY = "AUD"

        private val CARD_INFO = CardInfo.Builder()
                .setImageId(R.drawable.manly_fast_ferry_card)
                .setName(ManlyFastFerryTransitData.NAME)
                .setLocation(R.string.location_sydney_australia)
                .setCardType(CardType.MifareClassic)
                .setKeysRequired()
                .build()

        val FACTORY: ClassicCardTransitFactory = object : ErgTransitFactory() {
            override val ergAgencyID: Int
                get() = AGENCY_ID

            override fun parseTransitData(classicCard: ClassicCard) =
                    ManlyFastFerryTransitData(classicCard)

            override fun parseTransitIdentity(card: ClassicCard) = parseTransitIdentity(card, NAME)

            override fun getAllCards() = listOf(CARD_INFO)
        }
    }
}
