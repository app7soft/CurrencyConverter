package com.app7soft.currencyconverter

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app7soft.currencyconverter.MainActivity.Companion.ShowIntMin
import com.app7soft.currencyconverter.MainActivity.Companion.ShowIntRunNumber
import com.app7soft.currencyconverter.MainActivity.Companion.currencySymbols
import com.app7soft.currencyconverter.MainActivity.Companion.sharedPreferences
import com.google.android.gms.ads.AdRequest
import kotlinx.android.synthetic.main.activity_symbol_search.*
import timber.log.Timber
import java.util.*

class SymbolSearch: AppCompatActivity()  {
    lateinit var adapter: RecyclerView_Adapter
    lateinit var Recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        if(currencySymbols.size==0){
            finish() //Jeśli apka będzie długo w backgroundzie i zabite zostanie MainActivity to odpalamy od nowa
        }else {
            setContentView(R.layout.activity_symbol_search)
            adViewSymbolSearch.loadAd(AdRequest.Builder().build())
            if (MainActivity.StrangeLanguage) setLocale(this,"en")


            Recycler = findViewById(R.id.SymbolRecyclerView)
            Recycler.layoutManager = LinearLayoutManager(SymbolRecyclerView.context)
            Recycler.setHasFixedSize(true)
            adapter = RecyclerView_Adapter(currencySymbols)
            Recycler.adapter = adapter

            SymbolSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    adapter.filter.filter(newText)
                    return false
                }

            })
        }

    }

    fun back(view: View) {
        onBackPressed()
    }

    override fun onBackPressed() {
        finish()
        if (sharedPreferences.getInt("RunNumber", 10) >= ShowIntRunNumber && F().LastInterstitialMin(sharedPreferences) >= ShowIntMin){
            if (MainActivity.mInterstitialAd.isLoaded) {
                MainActivity.mInterstitialAd.show()
            } else {
                Timber.tag("Mik").d( "The interstitial wasn't loaded yet.")
            }
        }
    }

    fun setLocale(activity: Activity, languageCode: String?) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val resources: Resources = activity.resources
        val config: Configuration = resources.getConfiguration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.getDisplayMetrics())
    }
}