package com.github.fschroffner.radiodroid3.recording

import android.app.ProgressDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.R
import java.io.File

class RecordingsAdapter(private val context: Context) : RecyclerView.Adapter<RecordingsAdapter.RecordingItemViewHolder>() {

    inner class RecordingItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val viewRoot: ViewGroup = itemView as ViewGroup
        val textViewTitle: TextView = itemView.findViewById(R.id.textViewTitle)
        val textViewTime: TextView = itemView.findViewById(R.id.textViewTime)
    }

    private var recordings: List<DataRecording>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingItemViewHolder {
        return RecordingItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_recording, parent, false))
    }

    override fun onBindViewHolder(holder: RecordingItemViewHolder, position: Int) {
        val recording = recordings!![position]
        holder.textViewTitle.text = recording.Name
        holder.viewRoot.setOnClickListener { openRecording(recording) }
    }

    fun setRecordings(newRecordings: List<DataRecording>) {
        val current = recordings
        if (current != null && newRecordings.size == current.size) {
            if (newRecordings.indices.all { newRecordings[it] == current[it] }) return
        }
        recordings = newRecordings
        notifyDataSetChanged()
    }

    override fun getItemCount() = recordings?.size ?: 0

    @Suppress("DEPRECATION")
    private fun openRecording(theData: DataRecording) {
        val dialog = ProgressDialog.show(context, "Loading...", "Please wait...", true, false)
        val path = RecordingsManager.getRecordDir() + "/" + theData.Name
        if (BuildConfig.DEBUG) Log.d(TAG, "play: $path")

        val intent = Intent(path).apply { action = Intent.ACTION_VIEW }
        val file = File(path)
        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        intent.setDataAndType(fileUri, "audio/*")

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ->
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN -> {
                intent.clipData = ClipData.newUri(context.contentResolver, "Record", fileUri)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> {
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach {
                    context.grantUriPermission(it.activityInfo.packageName, fileUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    dialog.dismiss()
                }
            }
        }

        context.startActivity(intent)
        dialog.dismiss()
    }

    companion object {
        private const val TAG = "RecordingsAdapter"
    }
}
