/*
 * CardKeys.java
 *
 * Copyright (C) 2012 Eric Butler
 *
 * Authors:
 * Eric Butler <eric@codebutler.com>
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

package au.id.micolous.metrodroid.key

import org.jetbrains.annotations.NonNls
import org.json.JSONException
import org.json.JSONObject

interface CardKeys {

    val type: String

    val description: String?

    val fileType: String

    val uid: String?

    val sourceDataLength: Int

    @Throws(JSONException::class)
    fun toJSON(): JSONObject

    companion object {
        const val JSON_KEY_TYPE_KEY = "KeyType"
        const val TYPE_MFC = "MifareClassic"
        @NonNls
        const val TYPE_MFC_STATIC = "MifareClassicStatic"
        const val JSON_TAG_ID_KEY = "TagId"
        const val CLASSIC_STATIC_TAG_ID = "staticclassic"

        /**
         * Reads ClassicCardKeys from the internal (JSON) format.
         *
         * See https://github.com/micolous/metrodroid/wiki/Importing-MIFARE-Classic-keys#json
         */
        @Throws(JSONException::class)
        fun fromJSON(keyJSON: JSONObject, cardType: String, defaultBundle: String): CardKeys? = when (cardType) {
            CardKeys.TYPE_MFC -> ClassicCardKeys.fromJSON(keyJSON, defaultBundle)
            CardKeys.TYPE_MFC_STATIC -> ClassicStaticKeys.fromJSON(keyJSON, defaultBundle)
            else -> throw IllegalArgumentException("Unknown card type for key: $cardType")
        }

        @Throws(JSONException::class)
        fun fromJSON(keyJSON: JSONObject, defaultBundle: String): CardKeys? = fromJSON(keyJSON,
                keyJSON.getString(CardKeys.JSON_KEY_TYPE_KEY), defaultBundle)
    }
}
