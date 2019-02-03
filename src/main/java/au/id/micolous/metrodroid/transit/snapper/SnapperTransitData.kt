/*
 * SnapperTransitData.kt
 *
 * Copyright 2019 Michael Farrell <micolous+git@gmail.com>
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
package au.id.micolous.metrodroid.transit.snapper

import au.id.micolous.farebot.R
import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.iso7816.ISO7816Record
import au.id.micolous.metrodroid.card.ksx6923.KSX6923Application
import au.id.micolous.metrodroid.card.ksx6923.KSX6923CardTransitFactory
import au.id.micolous.metrodroid.transit.*
import au.id.micolous.metrodroid.transit.tmoney.TMoneyTransitData

class SnapperTransitData(tMoneyCard: KSX6923Application) : TMoneyTransitData(tMoneyCard) {

    override fun parseTrips(tMoneyCard: KSX6923Application): List<Trip> {
        val txns = getSnapperTransactionRecords(tMoneyCard).map {
            SnapperTransaction.parseTransaction(it.first.data, it.second.data)
        }

        return TransactionTrip.merge(txns)
    }

    private fun getSnapperTransactionRecords(tMoneyCard: KSX6923Application): List<Pair<ISO7816Record, ISO7816Record>> {
        val trips = tMoneyCard.getSfiFile(3) ?: return emptyList()
        val balances = tMoneyCard.getSfiFile(4) ?: return emptyList()

        return trips.records zip balances.records
    }

    override fun getBalance() = purseInfo.buildTransitBalance(TransitCurrency.NZD(mBalance))

    override fun getCardName() = NAME

    companion object {
        private const val NAME = "Snapper"
        private val TAG = SnapperTransitData::class.java.simpleName

        val CARD_INFO = CardInfo.Builder()
                .setName(NAME)
                .setLocation(R.string.location_wellington_nz)
                .setCardType(CardType.ISO7816)
                .build()

        val FACTORY: KSX6923CardTransitFactory = object : KSX6923CardTransitFactory {
            override fun parseTransitIdentity(app: KSX6923Application) =
                    TransitIdentity(NAME, app.serial)

            override fun parseTransitData(app: KSX6923Application) = SnapperTransitData(app)

            override fun getAllCards() = listOf(CARD_INFO)

            override fun check(card: KSX6923Application) = card.getSfiFile(4)?.records?.all {
                    it.data.sliceArray(26 until 46).isAllFF()
                } ?: false
        }
    }

}