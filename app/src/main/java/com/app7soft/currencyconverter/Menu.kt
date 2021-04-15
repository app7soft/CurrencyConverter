package com.app7soft.currencyconverter

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import com.google.android.gms.ads.AdRequest
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_menu.*

class Menu : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        version.text = "Version " + BuildConfig.VERSION_NAME.toString()
        App7Soft.setMovementMethod(LinkMovementMethod.getInstance())
        adViewMenu.loadAd(AdRequest.Builder().build())
    }

    fun BInfoClicked(view: View) {
        onBackPressed()
    }

    fun BRateClicked(view: View) {
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.data = Uri.parse("https://play.google.com/store/apps/details?id=com.app7soft.currencyconverter")
        startActivity(intent)
    }

    fun BPrivacyClicked(view: View) {
        onBackPressed()
    }

    fun back(view: View) {
        onBackPressed()
    }

}