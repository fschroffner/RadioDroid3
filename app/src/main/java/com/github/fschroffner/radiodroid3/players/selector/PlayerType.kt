package com.github.fschroffner.radiodroid3.players.selector

import android.os.Parcel
import android.os.Parcelable

enum class PlayerType(val value: Int) : Parcelable {
    RADIODROID(0), EXTERNAL(1), CAST(2);

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeInt(ordinal)
    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<PlayerType> {
        override fun createFromParcel(parcel: Parcel): PlayerType = values()[parcel.readInt()]
        override fun newArray(size: Int): Array<PlayerType?> = arrayOfNulls(size)
    }
}
