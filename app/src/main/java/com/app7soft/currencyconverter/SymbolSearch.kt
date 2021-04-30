package com.app7soft.currencyconverter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SearchView
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

        SymbolSearch.setOnQueryTextListener(object: SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return false
            }

        })

    }

    fun back(view: View) {
        onBackPressed()
    }
}