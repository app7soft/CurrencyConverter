package com.app7soft.currencyconverter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.app7soft.currencyconverter.MainActivity.Companion.SymbolsToNamesCollection
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.custom_list_row.view.*
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

class RecyclerView_Adapter(private var countryList: ArrayList<String>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    var countryFilterList = ArrayList<String>()

    lateinit var mcontext: Context

    class CountryHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    init {
        countryFilterList = countryList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val countryListView =
            LayoutInflater.from(parent.context).inflate(R.layout.custom_list_row, parent, false)
        val sch = CountryHolder(countryListView)
        mcontext = parent.context
        return sch
    }

    override fun getItemCount(): Int {
        return countryFilterList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.Symbol.text = countryFilterList[position]
        holder.itemView.Flaga.setImageResource(FlagaResource(countryFilterList[position]))
        holder.itemView.Nazwa.text = SymbolsToNamesCollection[countryFilterList[position]].toString()

        holder.itemView.setOnClickListener {
            //val intent = Intent(mcontext, DetailsActivity::class.java)
            //intent.putExtra("passselectedcountry", countryFilterList[position])
            //mcontext.startActivity(intent)
            Log.d("Selected:", countryFilterList[position])
            val intent = Intent()
            intent.putExtra("Symbol", countryFilterList[position])
            (mcontext as SymbolSearch).setResult(Activity.RESULT_OK, intent)
            Timber.tag("Mik").d("Chosen Symbol: "+countryFilterList[position])
            (mcontext as SymbolSearch).finish()
        }
    }


    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()
                if (charSearch.isEmpty()) {
                    countryFilterList = countryList
                } else {
                    val resultList = ArrayList<String>()
                    for (row in countryList) {
                        if (row.toLowerCase(Locale.ROOT).contains(charSearch.toLowerCase(Locale.ROOT))) {
                            resultList.add(row)
                        }
                    }
                    countryFilterList = resultList
                }
                val filterResults = FilterResults()
                filterResults.values = countryFilterList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                countryFilterList = results?.values as ArrayList<String>
                notifyDataSetChanged()
            }

        }
    }

}