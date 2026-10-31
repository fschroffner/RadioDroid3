package com.github.fschroffner.radiodroid3.ui.playlist

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.rustamg.filedialogs.FileDialog
import com.rustamg.filedialogs.OpenFileDialog
import com.rustamg.filedialogs.SaveFileDialog
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Handles playlist file import/export operations, SAF document pickers, and legacy file dialogs.
 */
class PlaylistFileManager(
    private val activity: ComponentActivity,
    private val fragmentManager: FragmentManager,
    private val favouriteManager: FavouriteManager,
    private val historyManager: HistoryManager,
    private val selectedMenuItemProvider: () -> Int
) {

    private val tag = "PlaylistFileManager"

    val saveFileLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            Log.d(tag, "Chosen save path: $uri")
            try {
                val os: OutputStream = activity.contentResolver.openOutputStream(uri) ?: return@registerForActivityResult
                val writer = OutputStreamWriter(os)
                if (selectedMenuItemProvider() == R.id.nav_item_starred) {
                    favouriteManager.SaveM3UWriter(writer)
                } else if (selectedMenuItemProvider() == R.id.nav_item_history) {
                    historyManager.SaveM3UWriter(writer)
                }
            } catch (e: Exception) {
                Log.e(tag, "Unable to write to file $e")
            }
        }
    }

    val loadFileLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            Log.d(tag, "Chosen load path: $uri")
            try {
                val inputStream: InputStream = activity.contentResolver.openInputStream(uri) ?: return@registerForActivityResult
                val reader = InputStreamReader(inputStream)
                favouriteManager.LoadM3USimple(reader)
            } catch (e: Exception) {
                Log.e(tag, "Unable to load from file $e")
            }
        }
    }

    fun saveFavourites() {
        val dialog = SaveFileDialog()
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, Utils.getThemeResId(activity))
        val args = Bundle().apply {
            putString(FileDialog.EXTENSION, ".m3u")
        }
        dialog.arguments = args
        dialog.show(fragmentManager, SaveFileDialog::class.java.name)
    }

    fun saveFavouritesSimple() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        saveFileLauncher.launch(intent)
    }

    fun loadFavourites() {
        val dialogOpen = OpenFileDialog()
        dialogOpen.setStyle(DialogFragment.STYLE_NO_TITLE, Utils.getThemeResId(activity))
        val argsOpen = Bundle().apply {
            putString(FileDialog.EXTENSION, ".m3u")
        }
        dialogOpen.arguments = argsOpen
        dialogOpen.show(fragmentManager, OpenFileDialog::class.java.name)
    }

    fun loadFavouritesSimple() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        loadFileLauncher.launch(intent)
    }

    fun onFileSelected(dialog: FileDialog, file: File) {
        try {
            Log.i(tag, "save to ${file.parent}/${file.name}")
            if (dialog is SaveFileDialog) {
                if (selectedMenuItemProvider() == R.id.nav_item_starred) {
                    favouriteManager.SaveM3U(file.parent, file.name)
                } else if (selectedMenuItemProvider() == R.id.nav_item_history) {
                    historyManager.SaveM3U(file.parent, file.name)
                }
            } else if (dialog is OpenFileDialog) {
                favouriteManager.LoadM3U(file.parent, file.name)
            }
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
    }

    fun handlePermissionsResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            ActivityMain.PERM_REQ_STORAGE_FAV_LOAD -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveFavourites()
                } else {
                    Log.w(tag, "Storage permission not granted -> simple load")
                    loadFavouritesSimple()
                }
            }
            ActivityMain.PERM_REQ_STORAGE_FAV_SAVE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveFavourites()
                } else {
                    Log.w(tag, "Storage permission not granted -> simple save")
                    saveFavouritesSimple()
                }
            }
        }
    }
}
