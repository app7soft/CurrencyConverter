package com.app7soft.currencyconverter


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
import android.widget.Filter
import android.widget.Filterable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app7soft.currencyconverter.MainActivity.Companion.SymbolsNames
import com.app7soft.currencyconverter.MainActivity.Companion.SymbolsToNamesCollection
import com.app7soft.currencyconverter.MainActivity.Companion.sharedPreferences
import com.google.android.gms.ads.AdRequest
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.custom_list_row.view.*
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList


class RecyclerView_Adapter(private var countryList: ArrayList<String>) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    var countryFilterList = ArrayList<String>()
    var countryFilterListTEMP = ArrayList<String>()

    lateinit var mcontext: Context
    lateinit var Recycler: RecyclerView

    class CountryHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    init {
        countryFilterList = countryList
    }

    //dodane zeby moc odwloac sie do RecyclerView
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        Recycler = recyclerView
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
        holder.itemView.Nazwa.text = SymbolsToNamesCollection[countryFilterList[position]].toString()


        //holder.itemView.Flaga.setImageResource(FlagaResource(countryFilterList[position]))
        try {
            holder.itemView.Flaga.setImageDrawable(ContextCompat.getDrawable(mcontext, FlagaResource(countryFilterList[position])))
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log(countryFilterList[position])
        }



        holder.itemView.setOnClickListener {
            Log.d("Selected:", countryFilterList[position])
            val intent = Intent()
            intent.putExtra("Symbol", countryFilterList[position])
            (mcontext as SymbolSearch).setResult(Activity.RESULT_OK, intent)
            Timber.tag("Mik").d("Chosen Symbol: " + countryFilterList[position])
            (mcontext as SymbolSearch).finish()
            showAdd()
        }
    }


    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                Timber.tag("Mik").d("perform Filtering")
                val charSearch = constraint.toString()
                if (charSearch.isEmpty()) {
                    countryFilterListTEMP = countryList
                } else {
                    val resultList1 = ArrayList<String>() //Lista po symbolach
                    for (row in countryList) {
                        if (row.toLowerCase(Locale.ROOT).contains(charSearch.toLowerCase(Locale.ROOT))) {
                            resultList1.add(row)
                        }
                    }
                    for (row in SymbolsNames) {
                        if (row.toLowerCase(Locale.ROOT).contains(charSearch.toLowerCase(Locale.ROOT))) {
                            //Timber.tag("Mik").d(row.toString())
                            //Timber.tag("Mik").d("Symbol is: "+SymbolsToNamesCollection.filterValues { it == row }.keys.elementAt(0))
                            resultList1.add(SymbolsToNamesCollection.filterValues { it == row }.keys.elementAt(0))
                        }
                    }
                    //Dupliaty usun????
                    //Timber.tag("Mik").d("Result List: " + resultList1.toString())
                    countryFilterListTEMP = ArrayList(resultList1.distinct())
                    //Timber.tag("Mik").d("Result After Remove duplicates: " + countryFilterList.toString())
                    //Timber.tag("Mik").d("Test List: "+TestList.toString())

                }
                val filterResults = FilterResults()
                filterResults.values = countryFilterListTEMP
                return filterResults
                /*if (!Recycler.isComputingLayout and (Recycler.scrollState == SCROLL_STATE_IDLE)) {
                    Timber.tag("Mik").d("NOT NOT Scrolling, Sending results....")
                    return filterResults
                } else {
                    Timber.tag("Mik").d("SSSSSSScrollingSSSSSSSSSSSSSSSSS")
                    Recycler.suppressLayout(true) //zatrzymujemy przewijanie layoutu
                    Recycler.suppressLayout(false)
                    Recycler.recycledViewPool.clear()
                    return filterResults
                }*/
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results != null) {
                    countryFilterList = results.values as ArrayList<String>
                }
                notifyDataSetChanged()
             }

        }
    }

    private fun showAdd(){
        if (sharedPreferences.getInt("RunNumber", 10) >= MainActivity.ShowIntRunNumber && F().LastInterstitialMin(sharedPreferences) >= MainActivity.ShowIntMin){
            if (MainActivity.mInterstitialAd.isLoaded) {
                MainActivity.mInterstitialAd.show()
            } else {
                Timber.tag("Mik").d("The interstitial wasn't loaded yet.")
            }
        }
    }

}

