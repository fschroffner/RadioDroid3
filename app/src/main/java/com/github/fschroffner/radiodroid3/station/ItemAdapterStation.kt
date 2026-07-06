package com.github.fschroffner.radiodroid3.station

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ShortcutManager
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.view.IconicsImageButton
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.CountryFlagsLoader
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.RadioDroidApp
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.interfaces.IAdapterRefreshable
import com.github.fschroffner.radiodroid3.players.PlayStationTask
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import com.github.fschroffner.radiodroid3.service.PlayerService
import com.github.fschroffner.radiodroid3.service.PlayerServiceUtil
import com.github.fschroffner.radiodroid3.utils.RecyclerItemMoveAndSwipeHelper
import com.github.fschroffner.radiodroid3.utils.RecyclerItemSwipeHelper
import com.github.fschroffner.radiodroid3.utils.SwipeableViewHolder
import com.github.fschroffner.radiodroid3.views.TagsView

open class ItemAdapterStation(
    val activity: FragmentActivity,
    val resourceId: Int,
    private val filterType: StationsFilter.FilterType
) : RecyclerView.Adapter<ItemAdapterStation.StationViewHolder>(),
    RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    interface StationActionsListener {
        fun onStationClick(station: DataRadioStation, pos: Int)
        fun onStationMoved(from: Int, to: Int)
        fun onStationSwiped(station: DataRadioStation)
        fun onStationMoveFinished()
    }

    fun interface FilterListener {
        fun onSearchCompleted(searchStatus: StationsFilter.SearchStatus)
    }

    private val TAG = "AdapterStations"

    var stationsList: List<DataRadioStation> = emptyList()
    var filteredStationsList: List<DataRadioStation> = mutableListOf()

    private var stationActionsListener: StationActionsListener? = null
    private var filterListener: FilterListener? = null
    private var supportsStationRemoval = false

    private var shouldLoadIcons = false
    private var refreshable: IAdapterRefreshable? = null

    private val updateUIReceiver: BroadcastReceiver
    private var expandedPosition = -1
    var playingStationPosition = -1

    val stationImagePlaceholder = AppCompatResources.getDrawable(activity, R.drawable.ic_photo_24dp)
    private val favouriteManager: FavouriteManager
    private var filter: StationsFilter? = null

    private val tagSelectionCallback = object : TagsView.TagSelectionCallback {
        override fun onTagSelected(tag: String) {
            val i = Intent(context, ActivityMain::class.java).apply {
                putExtra(ActivityMain.EXTRA_SEARCH_TAG, tag)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        }
    }

    open inner class StationViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView), View.OnClickListener, SwipeableViewHolder {

        open var viewForeground: View = itemView.findViewById(R.id.station_foreground)
        val layoutMain: LinearLayout = itemView.findViewById(R.id.layoutMain)
        open var frameLayout: FrameLayout = itemView.findViewById(R.id.frameLayout)

        open var imageViewIcon: ImageView = itemView.findViewById(R.id.imageViewIcon)
        open var transparentImageView: ImageView = itemView.findViewById(R.id.transparentCircle)
        val starredStatusIcon: ImageView = itemView.findViewById(R.id.starredStatusIcon)
        val textViewTitle: TextView = itemView.findViewById(R.id.textViewTitle)
        val textViewShortDescription: TextView = itemView.findViewById(R.id.textViewShortDescription)
        val textViewTags: TextView = itemView.findViewById(R.id.textViewTags)
        val buttonMore: ImageButton = itemView.findViewById(R.id.buttonMore)
        val imageTrend: ImageView = itemView.findViewById(R.id.trendStatusIcon)

        var stubDetails: ViewStub? = itemView.findViewById(R.id.stubDetails)
        var viewDetails: View? = null
        var buttonVisitWebsite: IconicsImageButton? = null
        var buttonBookmark: ImageButton? = null
        var buttonShare: ImageButton? = null
        var buttonAddAlarm: ImageButton? = null
        var buttonCreateShortcut: ImageButton? = null
        var buttonPlayInternalOrExternal: ImageButton? = null
        var viewTags: TagsView? = null

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            stationActionsListener?.let {
                val pos = adapterPosition
                it.onStationClick(filteredStationsList[pos], pos)
            }
        }

        override fun getForegroundView(): View = viewForeground
    }

    init {
        val radioDroidApp = activity.application as RadioDroidApp
        favouriteManager = radioDroidApp.favouriteManager

        val intentFilter = IntentFilter().apply {
            addAction(PlayerService.PLAYER_SERVICE_META_UPDATE)
            addAction(DataRadioStation.RADIO_STATION_LOCAL_INFO_CHAGED)
        }

        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                intent ?: return
                when (intent.action) {
                    PlayerService.PLAYER_SERVICE_META_UPDATE -> highlightCurrentStation()
                    DataRadioStation.RADIO_STATION_LOCAL_INFO_CHAGED -> {
                        val uuid = intent.getStringExtra(DataRadioStation.RADIO_STATION_UUID)
                        notifyChangedByStationUuid(uuid)
                    }
                }
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(updateUIReceiver, intentFilter)
    }

    fun setStationActionsListener(listener: StationActionsListener) { stationActionsListener = listener }
    fun setFilterListener(listener: FilterListener) { filterListener = listener }

    fun enableItemRemoval(recyclerView: RecyclerView) {
        if (!supportsStationRemoval) {
            supportsStationRemoval = true
            val helper = RecyclerItemSwipeHelper<StationViewHolder>(context, 0, ItemTouchHelper.LEFT + ItemTouchHelper.RIGHT, this)
            ItemTouchHelper(helper).attachToRecyclerView(recyclerView)
        }
    }

    fun enableItemMoveAndRemoval(recyclerView: RecyclerView) {
        if (!supportsStationRemoval) {
            supportsStationRemoval = true
            val helper = RecyclerItemMoveAndSwipeHelper<StationViewHolder>(context, ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, this)
            ItemTouchHelper(helper).attachToRecyclerView(recyclerView)
        }
    }

    fun updateList(refreshableList: IAdapterRefreshable?, stations: List<DataRadioStation>) {
        refreshable = refreshableList
        stationsList = stations
        filteredStationsList = stations
        notifyStationsChanged()
    }

    private fun notifyStationsChanged() {
        expandedPosition = -1
        playingStationPosition = -1
        shouldLoadIcons = Utils.shouldLoadIcons(context)
        highlightCurrentStation()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(resourceId, parent, false)
        return StationViewHolder(v)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = filteredStationsList[position]
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val useCircularIcons = Utils.useCircularIcons(context)

        holder.itemView.setBackgroundColor(when {
            station.DeletedOnServer -> 0xFFFF0000.toInt()
            !station.Working -> 0xFFFFFF00.toInt()
            else -> 0x00000000
        })

        if (!shouldLoadIcons) {
            holder.imageViewIcon.visibility = View.GONE
        } else {
            if (station.hasIcon()) {
                setupIcon(useCircularIcons, holder.imageViewIcon, holder.transparentImageView)
                PlayerServiceUtil.getStationIcon(holder.imageViewIcon, station.IconUrl)
            } else {
                holder.imageViewIcon.setImageDrawable(stationImagePlaceholder)
            }

            if (prefs.getBoolean("compact_style", false)) setupCompactStyle(holder)

            if (prefs.getBoolean("icon_click_toggles_favorite", true)) {
                val isInFavorites = favouriteManager.has(station.StationUuid)
                holder.imageViewIcon.contentDescription = context.applicationContext.getString(if (isInFavorites) R.string.detail_unstar else R.string.detail_star)
                holder.imageViewIcon.setOnClickListener { view ->
                    if (favouriteManager.has(station.StationUuid)) {
                        StationActions.removeFromFavourites(context, view, station)
                    } else {
                        StationActions.markAsFavourite(context, station)
                    }
                    notifyItemChanged(holder.adapterPosition)
                }
            }
        }

        val isExpanded = position == expandedPosition
        holder.textViewTags.visibility = if (isExpanded) View.GONE else View.VISIBLE
        holder.buttonMore.setImageResource(if (isExpanded) R.drawable.ic_expand_less_black_24dp else R.drawable.ic_expand_more_black_24dp)
        holder.buttonMore.contentDescription = context.applicationContext.getString(if (isExpanded) R.string.image_button_less else R.string.image_button_more)
        holder.buttonMore.setOnClickListener {
            if (expandedPosition != -1) notifyItemChanged(expandedPosition)
            val pos = holder.adapterPosition
            expandedPosition = if (isExpanded) -1 else pos
            if (expandedPosition != -1) notifyItemChanged(expandedPosition)
        }

        val tv = TypedValue()
        if (playingStationPosition == position) {
            context.theme.resolveAttribute(R.attr.colorAccentMy, tv, true)
            holder.textViewTitle.setTextColor(tv.data)
            holder.textViewTitle.setTypeface(null, Typeface.BOLD)
        } else {
            context.theme.resolveAttribute(R.attr.boxBackgroundColor, tv, true)
            holder.textViewTitle.typeface = holder.textViewShortDescription.typeface
            context.theme.resolveAttribute(R.attr.iconsInItemBackgroundColor, tv, true)
            holder.textViewTitle.setTextColor(tv.data)
        }

        holder.textViewTitle.text = station.Name
        holder.textViewShortDescription.text = station.getShortDetails(context)
        holder.textViewTags.text = station.TagsAll.replace(",", ", ")

        val inFavourites = favouriteManager.has(station.StationUuid)
        holder.starredStatusIcon.visibility = if (inFavourites) View.VISIBLE else View.GONE
        holder.starredStatusIcon.contentDescription = if (inFavourites) context.getString(R.string.action_favorite) else ""

        if (prefs.getBoolean("click_trend_icon_visible", true)) {
            holder.imageTrend.visibility = View.VISIBLE
            when {
                station.ClickTrend < 0 -> {
                    holder.imageTrend.setImageResource(R.drawable.ic_trending_down_black_24dp)
                    holder.imageTrend.contentDescription = context.getString(R.string.icon_click_trend_decreasing)
                }
                station.ClickTrend > 0 -> {
                    holder.imageTrend.setImageResource(R.drawable.ic_trending_up_black_24dp)
                    holder.imageTrend.contentDescription = context.getString(R.string.icon_click_trend_increasing)
                }
                else -> {
                    holder.imageTrend.setImageResource(R.drawable.ic_trending_flat_black_24dp)
                    holder.imageTrend.contentDescription = context.getString(R.string.icon_click_trend_stable)
                }
            }
        } else {
            holder.imageTrend.visibility = View.GONE
        }

        val flag = CountryFlagsLoader.instance.getFlag(activity, station.CountryCode)
        if (flag != null) {
            val k = flag.minimumWidth / flag.minimumHeight.toFloat()
            val viewHeight = holder.textViewShortDescription.textSize
            flag.setBounds(0, 0, (k * viewHeight).toInt(), viewHeight.toInt())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            holder.textViewShortDescription.setCompoundDrawablesRelative(flag, null, null, null)
        } else {
            @Suppress("DEPRECATION")
            holder.textViewShortDescription.setCompoundDrawables(flag, null, null, null)
        }

        if (isExpanded) {
            holder.viewDetails = holder.stubDetails?.inflate() ?: holder.viewDetails
            holder.stubDetails = null
            holder.viewTags = holder.viewDetails!!.findViewById(R.id.viewTags)
            holder.buttonVisitWebsite = holder.viewDetails!!.findViewById(R.id.buttonVisitWebsite)
            holder.buttonShare = holder.viewDetails!!.findViewById(R.id.buttonShare)
            holder.buttonBookmark = holder.viewDetails!!.findViewById(R.id.buttonBookmark)
            holder.buttonAddAlarm = holder.viewDetails!!.findViewById(R.id.buttonAddAlarm)
            holder.buttonCreateShortcut = holder.viewDetails!!.findViewById(R.id.buttonCreateShortcut)
            holder.buttonPlayInternalOrExternal = holder.viewDetails!!.findViewById(R.id.buttonPlayInRadioDroid)

            holder.buttonVisitWebsite!!.setOnClickListener { StationActions.openStationHomeUrl(activity, station) }
            holder.buttonShare!!.setOnClickListener { StationActions.share(activity, station) }

            if (favouriteManager.has(station.StationUuid)) {
                holder.buttonBookmark!!.visibility = View.GONE
            } else {
                holder.buttonBookmark!!.setOnClickListener { view ->
                    StationActions.markAsFavourite(context, station)
                    notifyItemChanged(holder.adapterPosition)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                && context.applicationContext.getSystemService(ShortcutManager::class.java).isRequestPinShortcutSupported) {
                holder.buttonCreateShortcut!!.visibility = View.VISIBLE
                holder.buttonCreateShortcut!!.setOnClickListener { station.prepareShortcut(context, CreatePinShortcutListener()) }
            } else {
                holder.buttonCreateShortcut!!.visibility = View.INVISIBLE
            }

            holder.buttonAddAlarm!!.setOnClickListener { StationActions.setAsAlarm(activity, station) }

            if (prefs.getBoolean("play_external", false)) {
                holder.buttonPlayInternalOrExternal!!.setOnClickListener { StationActions.playInRadioDroid(context, station) }
            } else {
                val ctx = context
                holder.buttonPlayInternalOrExternal!!.contentDescription = context.getString(R.string.detail_play_in_external_player)
                holder.buttonPlayInternalOrExternal!!.setImageDrawable(IconicsDrawable(context, CommunityMaterial.Icon2.cmd_play_box_outline).size(IconicsSize.dp(24)))
                holder.buttonPlayInternalOrExternal!!.setOnClickListener {
                    Utils.playAndWarnIfMetered(ctx.applicationContext as RadioDroidApp, station, PlayerType.EXTERNAL) {
                        PlayStationTask.playExternal(station, ctx).execute()
                    }
                }
            }

            holder.viewTags!!.setTags(station.TagsAll.split(","))
            holder.viewTags!!.mTagSelectionCallback = tagSelectionCallback
        }
        holder.viewDetails?.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }

    @TargetApi(26)
    inner class CreatePinShortcutListener : DataRadioStation.ShortcutReadyListener {
        override fun onShortcutReadyListener(shortcut: android.content.pm.ShortcutInfo) {
            val shortcutManager = context.applicationContext.getSystemService(ShortcutManager::class.java)
            if (shortcutManager.isRequestPinShortcutSupported) {
                shortcutManager.requestPinShortcut(shortcut, null)
            }
        }
    }

    override fun getItemCount() = filteredStationsList.size

    override fun onSwiped(viewHolder: StationViewHolder, direction: Int) {
        stationActionsListener!!.onStationSwiped(filteredStationsList[viewHolder.adapterPosition])
    }

    override fun onDragged(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Double, dY: Double) {}

    override fun onMoved(viewHolder: StationViewHolder, from: Int, to: Int) {
        notifyItemMoved(from, to)
        stationActionsListener!!.onStationMoved(from, to)
    }

    override fun onMoveEnded(viewHolder: StationViewHolder) {
        stationActionsListener!!.onStationMoveFinished()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(updateUIReceiver)
    }

    fun getFilter(): StationsFilter {
        if (filter == null) {
            filter = StationsFilter(context, filterType, object : StationsFilter.DataProvider {
                override fun getOriginalStationList() = stationsList
                override fun notifyFilteredStationsChanged(status: StationsFilter.SearchStatus, filteredStations: List<DataRadioStation>) {
                    filteredStationsList = filteredStations
                    notifyStationsChanged()
                    filterListener?.onSearchCompleted(status)
                }
            })
        }
        return filter!!
    }

    val context: Context get() = activity

    fun setupIcon(useCircularIcons: Boolean, imageView: ImageView, transparentImageView: ImageView) {
        if (useCircularIcons) {
            transparentImageView.visibility = View.VISIBLE
            imageView.layoutParams.height = imageView.layoutParams.width
            @Suppress("DEPRECATION")
            imageView.setBackgroundColor(context.resources.getColor(android.R.color.black))
        }
    }

    private fun setupCompactStyle(holder: StationViewHolder) {
        val res = context.resources
        holder.layoutMain.minimumHeight = res.getDimension(R.dimen.compact_style_item_minimum_height).toInt()
        holder.frameLayout.layoutParams.width = res.getDimension(R.dimen.compact_style_icon_container_width).toInt()
        holder.imageViewIcon.layoutParams.width = res.getDimension(R.dimen.compact_style_icon_width).toInt()
        holder.textViewShortDescription.visibility = View.GONE
        if (holder.transparentImageView.visibility == View.VISIBLE) {
            holder.transparentImageView.layoutParams.height = res.getDimension(R.dimen.compact_style_icon_height).toInt()
            holder.transparentImageView.layoutParams.width = res.getDimension(R.dimen.compact_style_icon_width).toInt()
            holder.imageViewIcon.layoutParams.height = res.getDimension(R.dimen.compact_style_icon_height).toInt()
        }
    }

    private fun highlightCurrentStation() {
        if (!PlayerServiceUtil.isPlaying()) return
        val oldPos = playingStationPosition
        val currentUuid = PlayerServiceUtil.getStationId()
        for (i in filteredStationsList.indices) {
            if (filteredStationsList[i].StationUuid == currentUuid) {
                playingStationPosition = i
                break
            }
        }
        if (playingStationPosition != oldPos) {
            if (oldPos > -1) notifyItemChanged(oldPos)
            if (playingStationPosition > -1) notifyItemChanged(playingStationPosition)
        }
    }

    private fun notifyChangedByStationUuid(uuid: String?) {
        for (i in filteredStationsList.indices) {
            if (filteredStationsList[i].StationUuid == uuid) {
                notifyItemChanged(i)
                break
            }
        }
    }
}
