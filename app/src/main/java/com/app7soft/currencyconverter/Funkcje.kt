package com.app7soft.currencyconverter

import android.content.SharedPreferences
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*

open class F {
    fun  RatingDays(sharepref: SharedPreferences):Int{
        val date1 = sharepref.getLong("DataZapytaniaRating", 0)
        if(date1.toInt() == 0){
            return 100 //Jeśli nie było jeszcze zapytania o rating to traktujemy jakby juz mineło 100 dni od ostatniego zapytania
        } else {
            val diff = Date().getTime()-date1
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            Timber.tag("Mik").d( "Last Rating asked (days ago): "+days.toInt().toString())
            return days.toInt()
        }
    }

    public fun  LastInterstitialMin(sharepref: SharedPreferences):Int{
        val date1 = sharepref.getLong("InterstitialTime", 0)
        if(date1.toInt() == 0){
            //Timber.tag("Mik").d("LastInterstitialMin: 6000")
            return 6000 //Jeśli nie było jeszcze wyswietlenia to trkatujemy jakby mineło 6000 min
        } else {
            val diff = Date().getTime()-date1
            val seconds = diff / 1000
            val minutes = seconds / 60
            //Timber.tag("Mik").d("LastInterstitialMin: "+minutes.toInt().toString())
            return minutes.toInt()
        }
    }

}