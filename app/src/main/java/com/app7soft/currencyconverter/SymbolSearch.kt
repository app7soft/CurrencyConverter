package com.app7soft.currencyconverter

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app7soft.currencyconverter.MainActivity.Companion.currencySymbols
import com.google.android.gms.ads.AdRequest
import kotlinx.android.synthetic.main.activity_symbol_search.*

class SymbolSearch: AppCompatActivity()  {
    lateinit var adapter: RecyclerView_Adapter
    lateinit var Recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symbol_search)
        adViewSymbolSearch.loadAd(AdRequest.Builder().build())

        Recycler = findViewById(R.id.SymbolRecyclerView)
        Recycler.layoutManager = LinearLayoutManager(SymbolRecyclerView.context)
        Recycler.setHasFixedSize(true)
        adapter = RecyclerView_Adapter(currencySymbols)
        Recycler.adapter = adapter

    }

    fun back(view: View) {
        onBackPressed()
    }
}