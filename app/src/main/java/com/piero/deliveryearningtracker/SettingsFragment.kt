package com.piero.deliveryearningtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.ads.AdView
import com.piero.deliveryearningtracker.utils.sendAnonymousStats
import com.piero.deliveryearningtracker.utils.scheduleStatsUpload
import androidx.work.WorkManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {
    private var adView: AdView? = null
    private val subscriptionListener: (Boolean) -> Unit = { isSubscribed ->
        Log.d("SettingsFragment", "Stato abbonamento aggiornato: isSubscribed=$isSubscribed")
        if (isAdded) {
            val dbHelper = (requireActivity().application as MyApplication).dbHelper
            val isAdsEnabled = DisableAds.loadAdsEnabledState(requireContext(), dbHelper)
            Log.d("SettingsFragment", "Aggiornamento annunci: isAdsEnabled=$isAdsEnabled")
            updateAdsVisibility(isAdsEnabled)
        } else {
            Log.w("SettingsFragment", "Frammento non attaccato, aggiornamento UI ignorato")
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d("SettingsFragment", "onCreatePreferences chiamato")
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val inviteFriendPref = findPreference<Preference>("invite_friend")
        inviteFriendPref?.let {
            it.isVisible = InviteConfig.IS_INVITE_FRIEND_ENABLED
            if (InviteConfig.IS_INVITE_FRIEND_ENABLED) {
                val summaryText = getString(
                    R.string.invite_friend_status,
                    InviteConfig.FRIENDS_REQUIRED_FOR_REWARD,
                    InviteConfig.REWARD_DAYS
                )
                it.summary = summaryText
            }
        }
    }

    private val selectFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            backupDatabaseToFolder(uri)
        } else {
            Toast.makeText(requireContext(), getString(R.string.pref_backup_aborted), Toast.LENGTH_SHORT).show()
        }
    }

    private val selectBackupLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            restoreDatabaseFromFile(uri)
        } else {
            Toast.makeText(requireContext(), getString(R.string.pref_restore_aborted), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d("SettingsFragment", "onAttach chiamato")
        val billingManager = (requireActivity().application as MyApplication).billingManager
        billingManager.addSubscriptionListener(subscriptionListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("SettingsFragment", "onViewCreated chiamato")
        val dbHelper = (requireActivity().application as MyApplication).dbHelper
        val isAdsEnabled = DisableAds.loadAdsEnabledState(requireContext(), dbHelper)
        Log.d("SettingsFragment", "Valore iniziale ads_enabled: $isAdsEnabled")
        updateAdsVisibility(isAdsEnabled)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            "backup_data" -> {
                selectFolderLauncher.launch(null)
                true
            }
            "night_mode" -> {
                val nightMode = (preference as SwitchPreferenceCompat).isChecked
                AppCompatDelegate.setDefaultNightMode(
                    if (nightMode) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                true
            }
            "restore_data" -> {
                selectBackupLauncher.launch("application/octet-stream")
                true
            }
            "reset_settings" -> {
                resetSettings()
                true
            }
            "remove_ads" -> {
                val billingManager = (requireActivity().application as MyApplication).billingManager
                billingManager.launchBillingFlow(requireActivity())
                true
            }
            "share_anonymous_stats" -> {
                val shareStats = (preference as SwitchPreferenceCompat).isChecked
                if (shareStats) {
                    sendAnonymousStats(requireContext())
                    scheduleStatsUpload(requireContext())
                } else {
                    WorkManager.getInstance(requireContext()).cancelUniqueWork("stats_upload")
                }
                true
            }
            "invite_friend" -> {
                startActivity(Intent(context, InviteFriendActivity::class.java))
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun updateAdsVisibility(isAdsEnabled: Boolean) {
        if (!isAdded) {
            Log.w("SettingsFragment", "updateAdsVisibility ignorato: frammento non attaccato")
            return
        }
        // Gestione annunci nel frammento (se presenti)
        val adContainer = view?.findViewById<LinearLayout>(R.id.ad_container) // Adatta l'ID
        if (adContainer != null) {
            if (isAdsEnabled) {
                adView = AdManager.setupBannerAd(requireActivity(), adContainer, true)
                Log.d("SettingsFragment", "Annunci visibili")
            } else {
                AdManager.destroyBannerAd(adView)
                adView = null
                adContainer.removeAllViews()
                Log.d("SettingsFragment", "Annunci nascosti")
                Toast.makeText(requireContext(), "Annunci rimossi!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreDatabaseFromFile(backupUri: Uri) {
        val context = requireContext()
        val dbFile = context.getDatabasePath("ordini.db")
        try {
            val inputStream = context.contentResolver.openInputStream(backupUri)
            val outputStream = FileOutputStream(dbFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            Toast.makeText(context, getString(R.string.pref_restore_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, String.format(getString(R.string.pref_error_restore), e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun resetSettings() {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit { clear() }
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    private fun backupDatabaseToFolder(folderUri: Uri) {
        val context = requireContext()
        val dbFile = context.getDatabasePath("ordini.db")
        val folder = DocumentFile.fromTreeUri(context, folderUri)
        if (folder != null) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = folder.createFile("application/x-sqlite3", "backup_ordini_$timestamp.db")
            if (backupFile != null) {
                try {
                    val inputStream = FileInputStream(dbFile)
                    val outputStream = context.contentResolver.openOutputStream(backupFile.uri)
                    inputStream.copyTo(outputStream!!)
                    inputStream.close()
                    outputStream.close()
                    Toast.makeText(context, getString(R.string.pref_backup_completed), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, String.format(getString(R.string.pref_error_backup), e.message), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, getString(R.string.pref_backup_unable_file), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, getString(R.string.pref_invalid_folder), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        Log.d("SettingsFragment", "onDestroyView chiamato")
        AdManager.destroyBannerAd(adView)
        super.onDestroyView()
    }

    override fun onDetach() {
        Log.d("SettingsFragment", "onDetach chiamato")
        val billingManager = (requireActivity().application as MyApplication).billingManager
        billingManager.removeSubscriptionListener(subscriptionListener)
        super.onDetach()
    }
}