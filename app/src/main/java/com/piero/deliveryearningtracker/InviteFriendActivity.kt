package com.piero.deliveryearningtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.appcompat.widget.Toolbar

class InviteFriendActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var statusText: TextView
    private lateinit var inviteCodeText: TextView
    private lateinit var inviteButton: Button
    private var adView: AdView? = null
    private lateinit var toolbar: Toolbar

    override fun onDestroy() {
        AdManager.destroyBannerAd(adView) // Pulizia
        adView = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        AdManager.resumeBannerAd(adView)
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView, dbHelper)
    }

    override fun onPause() {
        AdManager.pauseBannerAd(adView)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invite_friend)

        // Inizializza le view con findViewById
        toolbar = findViewById(R.id.toolbar)
        statusText = findViewById(R.id.statusText)
        inviteCodeText = findViewById(R.id.inviteCodeText)
        inviteButton = findViewById(R.id.inviteButton)

        // Imposta la Toolbar
        setSupportActionBar(toolbar)
        // Abilita la freccia di "indietro"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Gestisci il click sulla freccia
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Torna all'activity precedente
        }


        // Inizializza DatabaseHelper
        dbHelper = DatabaseHelper(this)
        dbHelper.initializeDatabase()

        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView, dbHelper)

        if (!InviteConfig.IS_INVITE_FRIEND_ENABLED) {
            inviteButton.isEnabled = false
            statusText.text = getString(R.string.invite_friend_feature_disabled)
            return
        }

        val inviteCode = dbHelper.getInviteCode()
        val inviteLink = getInviteLink(inviteCode)
        val inviteMessage = getString(R.string.invite_friend_message, inviteLink, inviteCode)

        inviteCodeText.text = getString(R.string.invite_friend_code_label, inviteCode)
        statusText.text = getString(R.string.invite_friend_status, InviteConfig.FRIENDS_REQUIRED_FOR_REWARD, InviteConfig.REWARD_DAYS)
        inviteButton.text = getString(R.string.invite_friend_button)

        inviteButton.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, inviteMessage)
                type = "text/html"
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.invite_friend_button)))
        }

        checkInvites()
    }

    private fun getInviteLink(inviteCode: String): String {
        return "https://play.google.com/store/apps/details?id=com.piero.deliveryearningtracker&referrer=codiceAmico%3D$inviteCode"
    }

    private fun checkInvites() {
        val inviteCode = dbHelper.getInviteCode()
        Firebase.firestore.collection("invites")
            .whereEqualTo("referrerCode", inviteCode)
            .get()
            .addOnSuccessListener { result ->
                val inviteCount = result.size()
                statusText.text = getString(R.string.invite_friend_count, inviteCount, InviteConfig.FRIENDS_REQUIRED_FOR_REWARD)
                if (inviteCount >= InviteConfig.FRIENDS_REQUIRED_FOR_REWARD) {
                    grantReward()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.invite_friend_connection_error), Toast.LENGTH_SHORT).show()
            }
    }

    private fun grantReward() {
        dbHelper.insertSubscription(InviteConfig.REWARD_DAYS)
        Toast.makeText(this, getString(R.string.invite_friend_reward_granted, InviteConfig.REWARD_DAYS), Toast.LENGTH_LONG).show()
    }

}