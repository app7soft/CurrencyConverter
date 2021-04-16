package com.app7soft.currencyconverter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import com.google.android.gms.ads.AdRequest
import kotlinx.android.synthetic.main.activity_info.*
import kotlinx.android.synthetic.main.activity_menu.*
import kotlinx.android.synthetic.main.activity_menu.App7Soft
import kotlinx.android.synthetic.main.activity_menu.version

class Info : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)
        version.text = "Version " + BuildConfig.VERSION_NAME.toString()
        App7Soft.setMovementMethod(LinkMovementMethod.getInstance())
        adViewInfo.loadAd(AdRequest.Builder().build())
    }

    fun back(view: View) {
        onBackPressed()
    }
}