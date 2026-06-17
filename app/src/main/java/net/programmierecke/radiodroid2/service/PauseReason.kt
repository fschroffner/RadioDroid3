package net.programmierecke.radiodroid2.service

import android.os.Parcel
import android.os.Parcelable

enum class PauseReason : Parcelable {
    NONE, BECAME_NOISY, FOCUS_LOSS, FOCUS_LOSS_TRANSIENT, METERED_CONNECTION, USER;

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeInt(ordinal)
    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<PauseReason> {
        override fun createFromParcel(parcel: Parcel): PauseReason = values()[parcel.readInt()]
        override fun newArray(size: Int): Array<PauseReason?> = arrayOfNulls(size)
    }
}
