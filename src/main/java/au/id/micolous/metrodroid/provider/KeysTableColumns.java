/*
 * KeysTableColumns.java
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

package au.id.micolous.metrodroid.provider;

import android.provider.BaseColumns;

import org.jetbrains.annotations.NonNls;

public class KeysTableColumns implements BaseColumns {

    public static final String TABLE_NAME = "keys";
    @NonNls
    public static final String CARD_ID = "card_id";
    @NonNls
    public static final String CARD_TYPE = "card_type";
    public static final String KEY_DATA = "key_data";
    @NonNls
    public static final String CREATED_AT = "created_at";

    private KeysTableColumns() {
    }
}
