package com.github.fschroffner.radiodroid3.players.selector

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.RadioDroidApp
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.players.PlayStationTask
import com.github.fschroffner.radiodroid3.players.mpd.MPDClient
import com.github.fschroffner.radiodroid3.players.mpd.MPDServerData
import com.github.fschroffner.radiodroid3.players.mpd.tasks.MPDChangeVolumeTask
import com.github.fschroffner.radiodroid3.players.mpd.tasks.MPDPauseTask
import com.github.fschroffner.radiodroid3.players.mpd.tasks.MPDResumeTask
import com.github.fschroffner.radiodroid3.players.mpd.tasks.MPDStopTask
import com.github.fschroffner.radiodroid3.service.PauseReason
import com.github.fschroffner.radiodroid3.service.PlayerService
import com.github.fschroffner.radiodroid3.service.PlayerServiceUtil
import com.github.fschroffner.radiodroid3.station.DataRadioStation

class PlayerSelectorAdapter(
    private val context: Context,
    private val stationToPlay: DataRadioStation?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface ActionListener {
        fun editServer(mpdServerData: MPDServerData)
        fun removeServer(mpdServerData: MPDServerData)
    }

    private inner class MPDServerItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgConnectionStatus: ImageView = itemView.findViewById(R.id.imgConnectionStatus)
        val textViewServerName: TextView = itemView.findViewById(R.id.textViewMPDName)
        val btnPlay: ImageButton = itemView.findViewById(R.id.buttonPlay)
        val btnStop: ImageButton = itemView.findViewById(R.id.buttonStop)
        val btnMore: ImageButton = itemView.findViewById(R.id.buttonMore)
        val textViewNoConnection: TextView = itemView.findViewById(R.id.textViewNoConnection)
        val btnDecreaseVolume: AppCompatImageButton = itemView.findViewById(R.id.buttonMPDDecreaseVolume)
        val textViewCurrentVolume: TextView = itemView.findViewById(R.id.textViewMPDVolume)
        val btnIncreaseVolume: AppCompatImageButton = itemView.findViewById(R.id.buttonMPDIncreaseVolume)
        var mpdServerData: MPDServerData? = null
    }

    private inner class PlayerItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewDescription: TextView = itemView.findViewById(R.id.textViewDescription)
        val btnPlay: ImageButton = itemView.findViewById(R.id.buttonPlay)
    }

    private val inflater = LayoutInflater.from(context)
    private val showPlayInExternal: Boolean
    private val warnOnMeteredConnection: Boolean
    private val fixedViewsCount: Int
    private val viewTypes = mutableListOf<Int>()
    private var actionListener: ActionListener? = null
    private val mpdClient: MPDClient
    private var mpdServers: List<MPDServerData> = emptyList()

    init {
        val radioDroidApp = context.applicationContext as RadioDroidApp
        mpdClient = radioDroidApp.mpdClient

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        showPlayInExternal = prefs.getBoolean("play_external", false) && stationToPlay != null
        warnOnMeteredConnection = prefs.getBoolean(PlayerService.METERED_CONNECTION_WARNING_KEY, false)

        var count = 0
        if (stationToPlay != null) { count++; viewTypes.add(PlayerType.RADIODROID.value) }
        if (showPlayInExternal) { count++; viewTypes.add(PlayerType.EXTERNAL.value) }
        if (radioDroidApp.castHandler.isCastSessionAvailable) { count++; viewTypes.add(PlayerType.CAST.value) }
        fixedViewsCount = count
    }

    fun setActionListener(listener: ActionListener) { actionListener = listener }

    fun notifyRadioDroidPlaybackStateChanged() {
        if (stationToPlay != null) {
            val pos = viewTypes.indexOf(PlayerType.RADIODROID.value)
            if (pos != -1) notifyItemChanged(pos)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType != PlayerType.MPD_SERVER.value) {
            PlayerItemViewHolder(inflater.inflate(R.layout.list_item_play_in, parent, false))
        } else {
            MPDServerItemViewHolder(inflater.inflate(R.layout.list_item_mpd_server, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == PlayerType.MPD_SERVER.value) {
            bindMpdHolder(holder as MPDServerItemViewHolder, position)
        } else {
            bindPlayerHolder(holder as PlayerItemViewHolder)
        }
    }

    private fun bindPlayerHolder(holder: PlayerItemViewHolder) {
        when (holder.itemViewType) {
            PlayerType.RADIODROID.value -> {
                holder.textViewDescription.setText(R.string.app_name)
                if (PlayerServiceUtil.isPlaying()) {
                    holder.btnPlay.setImageResource(R.drawable.ic_pause_circle)
                    holder.btnPlay.contentDescription = context.resources.getString(R.string.detail_pause)
                } else {
                    holder.btnPlay.setImageResource(R.drawable.ic_play_circle)
                    holder.btnPlay.contentDescription = context.getString(R.string.detail_play)
                }
                holder.btnPlay.setOnClickListener {
                    if (PlayerServiceUtil.isPlaying()) {
                        if (PlayerServiceUtil.isRecording()) PlayerServiceUtil.stopRecording()
                        PlayerServiceUtil.pause(PauseReason.USER)
                    } else {
                        Utils.playAndWarnIfMetered(context.applicationContext as RadioDroidApp, stationToPlay!!, PlayerType.RADIODROID) {
                            Utils.play(context.applicationContext as RadioDroidApp, stationToPlay!!)
                        }
                    }
                }
            }
            PlayerType.EXTERNAL.value -> {
                holder.textViewDescription.setText(R.string.action_play_in_external)
                holder.btnPlay.setOnClickListener {
                    Utils.playAndWarnIfMetered(context.applicationContext as RadioDroidApp, stationToPlay!!, PlayerType.EXTERNAL) {
                        PlayStationTask.playExternal(stationToPlay!!, context).execute()
                    }
                }
            }
            PlayerType.CAST.value -> {
                holder.textViewDescription.setText(R.string.media_route_menu_title)
                holder.btnPlay.setOnClickListener { PlayStationTask.playCAST(stationToPlay!!, context).execute() }
            }
        }
    }

    private fun bindMpdHolder(holder: MPDServerItemViewHolder, position: Int) {
        val mpdServerData = mpdServers[position - fixedViewsCount]
        holder.mpdServerData = mpdServerData
        holder.textViewServerName.text = mpdServerData.name

        if (mpdServerData.connected) {
            holder.btnPlay.visibility = View.VISIBLE
            holder.textViewNoConnection.visibility = View.GONE
            holder.textViewCurrentVolume.text = mpdServerData.volume.toString()
            holder.textViewCurrentVolume.visibility = View.VISIBLE
            holder.imgConnectionStatus.setImageResource(R.drawable.ic_mpd_connected_24dp)
        } else {
            holder.btnPlay.visibility = View.GONE
            holder.textViewCurrentVolume.visibility = View.GONE
            holder.textViewNoConnection.visibility = View.VISIBLE
            holder.imgConnectionStatus.setImageResource(R.drawable.ic_mpd_disconnected_24dp)
        }

        if (mpdServerData.connected && stationToPlay == null && mpdServerData.status != MPDServerData.Status.Playing) {
            holder.btnPlay.visibility = View.GONE
        }

        if (mpdServerData.connected && mpdServerData.status != MPDServerData.Status.Idle) {
            holder.btnStop.visibility = View.VISIBLE
            holder.btnStop.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDStopTask(null)) }
        } else {
            holder.btnStop.visibility = View.GONE
        }

        if (mpdServerData.connected && mpdServerData.status != MPDServerData.Status.Idle) {
            holder.btnDecreaseVolume.visibility = View.VISIBLE
            holder.btnIncreaseVolume.visibility = View.VISIBLE
            holder.btnDecreaseVolume.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDChangeVolumeTask(-10, null, mpdServerData)) }
            holder.btnIncreaseVolume.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDChangeVolumeTask(10, null, mpdServerData)) }
        } else {
            holder.btnDecreaseVolume.visibility = View.INVISIBLE
            holder.btnIncreaseVolume.visibility = View.INVISIBLE
        }

        holder.btnMore.setOnClickListener { view ->
            val menu = PopupMenu(context, holder.btnMore)
            menu.menuInflater.inflate(R.menu.menu_mpd_server, menu.menu)
            if (stationToPlay == null) {
                menu.menu.findItem(R.id.action_play).isVisible = false
                menu.menu.findItem(R.id.action_pause).isVisible = false
            } else {
                if (mpdServerData.status != MPDServerData.Status.Playing) menu.menu.findItem(R.id.action_pause).isVisible = false
                else menu.menu.findItem(R.id.action_play).isVisible = false
            }
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> actionListener?.editServer(mpdServerData)
                    R.id.action_remove -> actionListener?.removeServer(mpdServerData)
                    R.id.action_play -> PlayStationTask.playMPD(mpdClient, mpdServerData, stationToPlay!!, context).execute()
                    R.id.action_pause -> mpdClient.enqueueTask(mpdServerData, MPDPauseTask(null))
                }
                true
            }
            menu.show()
        }

        if (mpdServerData.connected) {
            if (stationToPlay != null) {
                holder.btnPlay.contentDescription = context.resources.getString(R.string.detail_play)
                holder.btnPlay.setImageResource(R.drawable.ic_play_circle)
                if (mpdServerData.status != MPDServerData.Status.Playing) {
                    holder.btnPlay.setOnClickListener { PlayStationTask.playMPD(mpdClient, mpdServerData, stationToPlay, context).execute() }
                } else {
                    holder.btnPlay.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDResumeTask(null)) }
                }
            }
            if (mpdServerData.status == MPDServerData.Status.Playing) {
                holder.btnPlay.contentDescription = context.resources.getString(R.string.detail_pause)
                holder.btnPlay.setImageResource(R.drawable.ic_pause_circle)
                holder.btnPlay.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDPauseTask(null)) }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position >= fixedViewsCount) PlayerType.MPD_SERVER.value else viewTypes[position]
    }

    fun setEntries(servers: List<MPDServerData>) {
        mpdServers = servers
        notifyDataSetChanged()
    }

    override fun getItemCount() = mpdServers.size + fixedViewsCount

    companion object {
        @Suppress("unused")
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MPDServerData>() {
            override fun areItemsTheSame(oldEntry: MPDServerData, newEntry: MPDServerData) = oldEntry.id == newEntry.id
            override fun areContentsTheSame(oldEntry: MPDServerData, newEntry: MPDServerData) = oldEntry.contentEquals(newEntry)
        }
    }
}
