package com.app7soft.currencyconverter

import android.content.SharedPreferences
import java.util.*

class Funkcje {
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
            return days.toInt()
        }
    }
}