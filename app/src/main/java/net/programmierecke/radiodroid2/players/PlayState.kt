package net.programmierecke.radiodroid2.players

import android.os.Parcel
import android.os.Parcelable

enum class PlayState : Parcelable {
    Idle, PrePlaying, Playing, Paused;

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeInt(ordinal)
    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<PlayState> {
        override fun createFromParcel(parcel: Parcel): PlayState = values()[parcel.readInt()]
        override fun newArray(size: Int): Array<PlayState?> = arrayOfNulls(size)
    }
}
