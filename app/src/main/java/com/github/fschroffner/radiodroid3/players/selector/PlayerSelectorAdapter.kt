package com.github.fschroffner.radiodroid3.players.selector

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import dagger.hilt.EntryPoints
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.players.PlayStationTask
import com.github.fschroffner.radiodroid3.service.PauseReason
import com.github.fschroffner.radiodroid3.station.DataRadioStation

class PlayerSelectorAdapter(
    private val context: Context,
    private val stationToPlay: DataRadioStation?
) : RecyclerView.Adapter<PlayerSelectorAdapter.PlayerItemViewHolder>() {

    inner class PlayerItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewDescription: TextView = itemView.findViewById(R.id.textViewDescription)
        val btnPlay: ImageButton = itemView.findViewById(R.id.buttonPlay)
    }

    private val inflater = LayoutInflater.from(context)
    private val showPlayInExternal: Boolean
    private val viewTypes = mutableListOf<Int>()

    init {
        val entryPoint = EntryPoints.get(context.applicationContext, AppEntryPoint::class.java)

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        showPlayInExternal = prefs.getBoolean("play_external", false) && stationToPlay != null

        if (stationToPlay != null) { viewTypes.add(PlayerType.RADIODROID.value) }
        if (showPlayInExternal) { viewTypes.add(PlayerType.EXTERNAL.value) }
        if (entryPoint.castHandler().isCastSessionAvailable) { viewTypes.add(PlayerType.CAST.value) }
    }

    fun notifyRadioDroidPlaybackStateChanged() {
        if (stationToPlay != null) {
            val pos = viewTypes.indexOf(PlayerType.RADIODROID.value)
            if (pos != -1) notifyItemChanged(pos)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerItemViewHolder {
        return PlayerItemViewHolder(inflater.inflate(R.layout.list_item_play_in, parent, false))
    }

    override fun onBindViewHolder(holder: PlayerItemViewHolder, position: Int) {
        when (holder.itemViewType) {
            PlayerType.RADIODROID.value -> {
                holder.textViewDescription.setText(R.string.app_name)
                val entryPoint = EntryPoints.get(context.applicationContext, AppEntryPoint::class.java)
                val repo = entryPoint.mediaControllerRepository()
                if (repo.isPlaying()) {
                    holder.btnPlay.setImageResource(R.drawable.ic_pause_circle)
                    holder.btnPlay.contentDescription = context.resources.getString(R.string.detail_pause)
                } else {
                    holder.btnPlay.setImageResource(R.drawable.ic_play_circle)
                    holder.btnPlay.contentDescription = context.getString(R.string.detail_play)
                }
                holder.btnPlay.setOnClickListener {
                    if (repo.isPlaying()) {
                        repo.pause(PauseReason.USER)
                    } else {
                        Utils.playAndWarnIfMetered(context, stationToPlay!!, PlayerType.RADIODROID) {
                            Utils.play(context, stationToPlay)
                        }
                    }
                }
            }
            PlayerType.EXTERNAL.value -> {
                holder.textViewDescription.setText(R.string.action_play_in_external)
                holder.btnPlay.setOnClickListener {
                    Utils.playAndWarnIfMetered(context, stationToPlay!!, PlayerType.EXTERNAL) {
                        PlayStationTask.playExternal(stationToPlay, context).execute()
                    }
                }
            }
            PlayerType.CAST.value -> {
                holder.textViewDescription.setText(R.string.media_route_menu_title)
                holder.btnPlay.setOnClickListener { PlayStationTask.playCAST(stationToPlay!!, context).execute() }
            }
        }
    }

    override fun getItemViewType(position: Int): Int = viewTypes[position]

    override fun getItemCount(): Int = viewTypes.size
}
