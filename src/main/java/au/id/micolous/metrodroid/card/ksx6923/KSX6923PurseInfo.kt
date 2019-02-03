package au.id.micolous.metrodroid.card.ksx6923

import android.os.Parcelable
import au.id.micolous.metrodroid.transit.TransitBalance
import au.id.micolous.metrodroid.transit.TransitBalanceStored
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.util.Utils
import au.id.micolous.metrodroid.xml.ImmutableByteArray
import kotlinx.android.parcel.Parcelize
import java.util.*

/**
 * `EFPURSE_INFO` -- FCI tag b0
 */
@Parcelize
class KSX6923PurseInfo internal constructor(private val purseInfoData: ImmutableByteArray) : Parcelable {
    val cardType : Byte
        get() = purseInfoData[0]

    val alg : Byte
        get() = purseInfoData[1]

    val vk : Byte
        get() = purseInfoData[2]

    val idCenter : Byte
        get() = purseInfoData[3]

    val csn : String
        get() = purseInfoData.getHexString(4, 8)

    val idtr : Long
        get() = purseInfoData.convertBCDtoLong(12, 5)

    val issueDate : Calendar?
        get() = KSX6923Application.parseHexDate(purseInfoData.byteArrayToLong(17, 4))

    val expiryDate : Calendar?
        get() = KSX6923Application.parseHexDate(purseInfoData.byteArrayToLong(21, 4))

    val userCode : Byte
        get() = purseInfoData[26]

    val disRate : Byte
        get() = purseInfoData[27]

    val balMax : Long
        get() = purseInfoData.byteArrayToLong(27, 4)

    val bra : Int
        get() = purseInfoData.convertBCDtoInteger(31, 2)

    val mmax : Long
        get() = purseInfoData.byteArrayToLong(33, 4)

    val tcode : Byte
        get() = purseInfoData[37]

    val ccode : Byte
        get() = purseInfoData[38]

    val rfu : ImmutableByteArray
        get() = purseInfoData.sliceOffLen(39, 8)

    // Convenience functionality
    val serial : String
        get() = Utils.groupString(csn, " ", 4, 4, 4)

    fun buildTransitBalance(balance: TransitCurrency, label: String? = null) : TransitBalance {
        return TransitBalanceStored(balance, label, issueDate, expiryDate)
    }
}