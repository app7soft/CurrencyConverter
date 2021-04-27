package com.app7soft.currencyconverter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import com.app7soft.currencyconverter.MainActivity.Companion.mInterstitialAd
import com.google.android.gms.ads.AdRequest
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_menu.*
import timber.log.Timber

class Menu : AppCompatActivity() {
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        version.text = "Version " + BuildConfig.VERSION_NAME.toString()
        App7Soft.setMovementMethod(LinkMovementMethod.getInstance())
        adViewMenu.loadAd(AdRequest.Builder().build())
        sharedPreferences = getSharedPreferences("myPref", Context.MODE_PRIVATE)
    }

    fun BInfoClicked(view: View) {
        val intent = Intent(this, Info::class.java)
        startActivity(intent)
        finish()
    }

    fun BRateClicked(view: View) {
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.data = Uri.parse("https://play.google.com/store/apps/details?id=com.app7soft.currencyconverter")
        startActivity(intent)
    }

    fun BShareClicked(view: View) {
        val intent = Intent(this, SymbolSearch::class.java)
        startActivity(intent)
        finish()
    }

    fun back(view: View) {
        onBackPressed()
    }

    override fun onBackPressed() {
        finish()
        if (sharedPreferences.getInt("RunNumber", 10) >= MainActivity.ShowIntRunNumber && MainActivity().LastInterstitialMin(sharedPreferences) >= MainActivity.ShowIntMin){
            if (mInterstitialAd.isLoaded) {
                mInterstitialAd.show()
            } else {
                Timber.tag("Mik").d( "The interstitial wasn't loaded yet.")
            }
        }
    }

}