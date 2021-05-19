package com.app7soft.currencyconverter


import MySingleton
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
import androidx.core.widget.TextViewCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import net.objecthunter.exp4j.ExpressionBuilder
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue


class MainActivity : AppCompatActivity() {
    companion object {
        var InterstitialID = "ca-app-pub-5209961561922052/7183758786" //Real for 361
        //var InterstitialID = "ca-app-pub-3940256099942544/1033173712" //TestAd
        lateinit var mInterstitialAd: InterstitialAd
        var AdErrorCount = 0 //próbujemy wczytywac maksymalnie 5 razy co 2 sekundy
        var ShowIntRunNumber: Int = 12 //Interstitial Reklmay pokazujemy od 12 uruchomienia
        var ShowIntMin: Int = 480  //Interstitial pokazujemy nieczęsciej niz co 480 min = 8h
        var RateRunNumber: Int = 10 //pytamy o rating przy 10 uruchomieniu jeśli nie zczyta sie z bazy danych
        var currencySymbols: ArrayList<String> = ArrayList()
        var SymbolsNames: ArrayList<String> = ArrayList()
        var SymbolsToNamesCollection = HashMap<String, Any>()
        lateinit var sharedPreferences: SharedPreferences
    }

    private var CurrencyRatesResponse: JSONObject? = null //Obecne kursy walut zwócone przez API
    private var SymbolsToNamesResponse: JSONObject? = null //Mapowanie symboli na pełna Nazwe waluty zwócone przez API
    private var ratesCollection = HashMap<String, Any>()

    lateinit var manager: ReviewManager
    var reviewInfo: ReviewInfo? = null

    var MaxLength: Int = 19 //Max length of main number
    var MaxEQLength: Int = 40 //Max length of equation
    var RatesUpdateOnStart: Boolean = true
    var gson = Gson()
    var json: String? = "" //temporary string to convert gson.JsonObject to JSONObject
    var CurrentCurrency: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            Timber.uprootAll()
            Timber.plant(Timber.DebugTree())
        }
        setContentView(R.layout.activity_main)
        DataLoading.setVisibility(View.GONE)

        //C1actual_size = resources.getDimension(R.dimen._30ssp)
        //C2actual_size = resources.getDimension(R.dimen._30ssp)

        sharedPreferences = getSharedPreferences("myPref", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        //val CurrentCountry = resources.configuration.locale
        //val CurrentCountry = "en"
        //Timber.tag("Mik").d(Locale.getDefault().toString())

        try {
            CurrentCurrency = Currency.getInstance(Locale.getDefault()).currencyCode
        }catch (e: IllegalArgumentException) {
            CurrentCurrency = "EUR"; // default symbol
        }

        if (!sharedPreferences.contains("RunNumber")) {
            Timber.tag("Mik").d("APLIKACJA URUCHOMIONA PIERWSZY RAZ....")
            //Timber.tag("Mik").d("Currency code: "+CurrentCurrency)
            editor.putInt("RunNumber", 1)
            editor.putString("LastUpdate", " rates no updated yet")
            editor.putString("Currency1Symbol", CurrentCurrency)
            if (CurrentCurrency != "EUR") {
                editor.putString("Currency2Symbol", "EUR")
            } else {
                editor.putString("Currency2Symbol", "USD")
            }
            editor.commit()
            Currency1.text = "0"

        } else {
            Timber.tag("Mik").d("To NIE jest pierwsza instalacja aplikacji")
            editor.putInt("RunNumber", sharedPreferences.getInt("RunNumber", 0) + 1) //Inkrementujemy numer uruchomienia
            Currency1.text = sharedPreferences.getString("Currency1Amount", "0")
            editor.commit()
        }
        setFlags() //Ustawiamy Flagi i Symbole

        //Currency1.text="test"


        RefreshDate.text = getString(R.string.LastUpdate) + " " + sharedPreferences.getString("LastUpdate", "")

        if (sharedPreferences.contains("CurrencyRatesResponse")) {
            CurrencyRatesResponse = gson.fromJson(sharedPreferences.getString("CurrencyRatesResponse", ""), JSONObject::class.java)
            SymbolsToNamesResponse = gson.fromJson(sharedPreferences.getString("SymbolsToNamesResponse", ""), JSONObject::class.java)
            InitRates()
            InitNames()
            updateChildCurrencies()
        }
        initDatabaseData() //zczytujemy dane z Bazy Danych Firebase
        if ((Build.VERSION.SDK_INT >= 21) and (F().RatingDays(sharedPreferences)>10)) {
            initReviews()
        }
        //If first time or last update was mor than one hour
        if (!sharedPreferences.contains("RunNumber") or (LastUpdateDiff() > 60)) {
            getRequest() //Updejt walut
        } else {
            RatesUpdateOnStart = false
        }


        val refresh = findViewById(R.id.refresh) as ImageView;
        refresh.setOnClickListener {
            getRequest()
            showAdd()
        }


        var requestConfiguration = MobileAds.getRequestConfiguration().toBuilder().build()
        MobileAds.setRequestConfiguration(requestConfiguration)
        MobileAds.initialize(this)
        adViewMain.loadAd(AdRequest.Builder().build())

        mInterstitialAd = InterstitialAd(this).apply {
            adUnitId = InterstitialID
            adListener = (object : AdListener() {
                override fun onAdLoaded() {
                    Timber.tag("Mik").d("The interstitial Loaded")
                    AdErrorCount = 0
                }

                override fun onAdClosed() {
                    val editor = sharedPreferences.edit()
                    editor.putLong("InterstitialTime", Date().getTime())
                    editor.commit()
                    Timber.tag("Mik").d("The interstitial Closed. Reloading...")
                    mInterstitialAd.loadAd(AdRequest.Builder().build())
                }

                override fun onAdFailedToLoad(errorCode: Int) {
                    //Timber.tag("Mik").d("Ad Failed to Load with code: " + errorCode.toString())
                    AdErrorCount++
                    //Timber.tag("Mik").d("AdErrorCount: " + AdErrorCount.toString())
                    if (AdErrorCount < 4) {
                        Handler().postDelayed({
                            //Timber.tag("Mik").d("Try to load again...")
                            mInterstitialAd.loadAd(AdRequest.Builder().build())
                        }, 5000)
                    }
                }
            })
        }
        //.tag("Mik").d("Loading Interstitial")
        AdErrorCount = 0
        mInterstitialAd.loadAd(AdRequest.Builder().build())

        Currency1.setTextSize(TypedValue.COMPLEX_UNIT_PX, CurrencySize(Currency1.text.length))
        Currency2.setTextSize(TypedValue.COMPLEX_UNIT_PX, CurrencySize(Currency2.text.length))
        Currency1.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {

            }

            override fun beforeTextChanged(s: CharSequence, start: Int,
                                           count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int,
                                       before: Int, count: Int) {
                //Timber.tag("Mik").d("tekst sie zmienił, ilosc lini to: "+Currency1.lineCount)
                Currency1.setTextSize(TypedValue.COMPLEX_UNIT_PX, CurrencySize(count))

            }
        })

        Currency2.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {}

            override fun beforeTextChanged(s: CharSequence, start: Int,
                                           count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int,
                                       before: Int, count: Int) {
                //Timber.tag("Mik").d("ilosc znakow text 2: " + count)
                Currency2.setTextSize(TypedValue.COMPLEX_UNIT_PX, CurrencySize(count))
            }
        })

        //Zapytanie o rating opóżniamy o 1,5sek żeby zdążyło sie zainicjalizować Review i zczytać z DB RateRunReview
        Handler().postDelayed({
            if ((sharedPreferences.getInt("RunNumber", 0) >= RateRunNumber) and (Build.VERSION.SDK_INT >= 21)){
                askReview()
            }
        }, 1500)

    }

    private fun getRequest() {
        val editor = sharedPreferences.edit()
        DataLoading.setVisibility(View.VISIBLE)
        RefreshDate.text = getString(R.string.UpdatingRates)
        Timber.tag("Mik").d("Updating rates....")
        //Timber.tag("Mik").d(RatesUpdateOnStart.toString())

        val urlCurrencyRates = "http://data.fixer.io/api/latest?access_key=7ce1c2205caeba66fbc17e38b3767be5"
        val urlCurrencySymbols = "http://data.fixer.io/api/symbols?access_key=7ce1c2205caeba66fbc17e38b3767be5"

        MySingleton.getInstance(this).addToRequestQueue(JsonObjectRequest(Request.Method.GET,
                urlCurrencyRates,
                null,
                { response ->
                    Timber.tag("Mik").d("Response: " + response)
                    if (response.toString().contains("error")) {
                        showAlertDialog104()
                        DataLoading.setVisibility(View.GONE)
                        RefreshDate.text = getString(R.string.LastUpdate) + " " + sharedPreferences.getString("LastUpdate", "")
                        RatesUpdateOnStart = false
                    } else {
                        val sdf = SimpleDateFormat("d MMM yyyy HH:mm")
                        val currentDate = sdf.format(Date())
                        RefreshDate.text = getString(R.string.LastUpdate) + " " + currentDate
                        editor.putString("LastUpdate", currentDate.toString())
                        editor.putLong("LastUpdateTimestamp", Date().getTime())
                        //completionHandlerForRates(response)
                        CurrencyRatesResponse = response.getJSONObject("rates")   //symbolsResponse = response.getJSONObject("rates")
                        editor.putString("CurrencyRatesResponse", gson.toJson(CurrencyRatesResponse))
                        editor.commit()
                        RatesUpdateOnStart = false
                        //Timber.tag("Mik").d("CurrencyRatesResponse:"+CurrencyRatesResponse.toString())
                        InitRates()
                        updateChildCurrencies() //przelicz z nowymi danymi
                        //Timber.tag("Mik").d("CurrencyRatesResponse:"+CurrencyRatesResponse.toString())
                    }
                },
                { error ->
                    Timber.tag("Mik").d("response Not OK: " + error)
                    RefreshDate.text = getString(R.string.LastUpdate) + " " + sharedPreferences.getString("LastUpdate", "")
                    if ((RatesUpdateOnStart == false) or (sharedPreferences.getInt("RunNumber", 0) == 1)) {
                        showAlertDialog()
                    } else {
                        Toast.makeText(this, R.string.CheckConnection, Toast.LENGTH_LONG).show();
                    }
                    RatesUpdateOnStart = false
                    //jeśli sie nie ma internetu to pobieramy ostatnie waluty które były
                }
        ))

        MySingleton.getInstance(this).addToRequestQueue(JsonObjectRequest(Request.Method.GET,
                urlCurrencySymbols,
                null,
                { response ->
                    if (response.toString().contains("error")) {
                        showAlertDialog104()
                    } else {
                        SymbolsToNamesResponse = response.getJSONObject("symbols")  //namesResponse = response.getJSONObject("symbols")
                        InitNames()
                        editor.putString("SymbolsToNamesResponse", gson.toJson(SymbolsToNamesResponse))
                        editor.commit()
                        DataLoading.setVisibility(View.GONE)
                    }
                    //Timber.tag("Mik").d("SymbolsToNamesResponse:"+SymbolsToNamesResponse.toString())
                },
                {
                    DataLoading.setVisibility(View.GONE)
                    //jeśli sie nie ma internetu to pobieramy ostatnie waluty które były
                }
        ))

    }

    private fun InitRates() {
        ratesCollection = ConvertJsonToHash(CurrencyRatesResponse!!)
        currencySymbols = ArrayList(ratesCollection.keys)
        currencySymbols.sort()
        //Timber.tag("Mik").d("CurrentCurrency: "+CurrentCurrency)
        //usuwam najpopularniejsze symbole żeby dodac je jeszcze raz
        //CurrentCurrency="EUR"
        if (CurrentCurrency !in arrayListOf("USD", "EUR", "JPY", "GBP", "AUD", "CAD", "CHF", "CNY", "BTC")) {
            currencySymbols.removeAt(currencySymbols.indexOf(CurrentCurrency))
        }
        currencySymbols.removeAt(currencySymbols.indexOf("USD"))
        currencySymbols.removeAt(currencySymbols.indexOf("EUR"))
        currencySymbols.removeAt(currencySymbols.indexOf("JPY"))
        currencySymbols.removeAt(currencySymbols.indexOf("GBP"))
        currencySymbols.removeAt(currencySymbols.indexOf("AUD"))
        currencySymbols.removeAt(currencySymbols.indexOf("CAD"))
        currencySymbols.removeAt(currencySymbols.indexOf("CHF"))
        currencySymbols.removeAt(currencySymbols.indexOf("CNY"))
        currencySymbols.removeAt(currencySymbols.indexOf("BTC"))
        //Timber.tag("Mik").d("Currency Symbol Before: "+currencySymbols.toString())
        currencySymbols.addAll(0, listOf("USD", "EUR", "JPY", "GBP", "AUD", "CAD", "CHF", "CNY", "BTC"))
        if (CurrentCurrency !in arrayListOf("USD", "EUR", "JPY", "GBP", "AUD", "CAD", "CHF", "CNY", "BTC")) {
            currencySymbols.add(0, CurrentCurrency)
        }
        //Timber.tag("Mik").d("Currency Symbol After: "+currencySymbols.toString())
    }

    private fun InitNames() {
        SymbolsToNamesCollection = ConvertJsonToHash(SymbolsToNamesResponse!!)
        //Timber.tag("Mik").d(SymbolsToNamesCollection.toString())
        for ((key, value) in SymbolsToNamesCollection) {
            try {
                //if(key == "JEP") throw IllegalArgumentException()
                val sym = Currency.getInstance(key).getDisplayName()
                if (key != sym) {
                    SymbolsToNamesCollection[key] = Currency.getInstance(key).getDisplayName()
                }
            }catch (e: IllegalArgumentException) {
                //Timber.tag("Mik").d("przechwyciłem wyjątek")
            }

            /*val sym = Currency.getInstance(key).getDisplayName()
            if (key != sym) {
                SymbolsToNamesCollection[key] = Currency.getInstance(key).getDisplayName()
            }*/
        }
        //Timber.tag("Mik").d(SymbolsToNamesCollection.toString())

        for (row in SymbolsToNamesCollection.values) {
            SymbolsNames.add(row.toString())
        }
        //Timber.tag("Mik").d(SymbolsToNamesCollection.values.toString())
        //Timber.tag("Mik").d("SymbolsNames: "+SymbolsNames.toString())
    }

    private fun ConvertJsonToHash(rates: JSONObject): HashMap<String, Any> {
        val ratesHashMap: HashMap<String, Any> = hashMapOf()

        (0 until rates.names().length()).forEach { i ->
            val key = rates.names().getString(i)
            val value = rates.get(key)

            ratesHashMap[key] = value
        }
        return ratesHashMap
    }

    private fun calculateEquivalent(currency1: Any, currency2: Any, amount: Double): Double {
        return amount * currency2.toString().toDouble() / currency1.toString().toDouble()
    }

    fun Calculate(cur1: String): String {
        val c1: Double
        val c2: Double
        var str: String
        //val namesAndValuesMap = matchCurrencyNamesWithCodes(CurrencyRatesResponse, SymbolsToNamesResponse)

        Timber.tag("Mik").d("c1: "+cur1)
        c1 = cur1.replace("\\s".toRegex(), "").replace(',', '.').toDouble()
        Timber.tag("Mik").d("c1: "+c1.toString())

        //Timber.tag("Mik").d("ratesCollection[1]: "+ratesCollection[sharedPreferences.getString("Currency1Symbol", "EUR")])
        //Timber.tag("Mik").d("Symbol1: "+sharedPreferences.getString("Currency1Symbol", "EUR"))
        //Timber.tag("Mik").d("ratesCollection[2]: "+ratesCollection[sharedPreferences.getString("Currency2Symbol", "EUR")])
        //Timber.tag("Mik").d("Symbol2: "+sharedPreferences.getString("Currency2Symbol", "EUR"))
        c2 = calculateEquivalent(ratesCollection[sharedPreferences.getString("Currency1Symbol", "EUR")]!!, ratesCollection[sharedPreferences.getString("Currency2Symbol", "USD")]!!, c1)
        //Timber.tag("Mik").d("CalculateEquivalent: "+c2.toString())

        str = ustaw_dokladnosc(c2)

        /*
        if (c2 == 0.0) {
            return "0"
        } else if (c2 > 999999999) {
            str = String.format("%.0f", c2)
        } else if (c2 > 99999999) {
            str = String.format("%.1f", c2)
        } else if (c2 >= 0.01) { //lub Waluta
            str = String.format("%.2f", c2)
            if (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
        } else { //dla Kryptowalut
            str = String.format("%.10f", c2)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            while (str.length >= 5 && str.takeLast(3).toString().toInt() >= 101) {
                str = str.dropLast(1)
            }
        }*/

        return str.replace('.', ',')
    }

    fun GroupBy3(sss: String): String {
        var newS: String = ""
        var s1: String = ""
        var s2: String = ""
        var re: String = ""
        var ujemna: Boolean = false
        var s: String = sss

        if (s.first()=='-'){
            ujemna = true
            s=s.drop(1)
        }

        s = s.replace("\\s".toRegex(), "")
        if (s.contains(',')) {
            s1 = s.split(',')[0]
            s2 = ',' + s.split(',')[1]
        } else {
            s1 = s
        }

        if (s1.length <= 3) {
            re = s
        } else {
            while (s1.length > 3) {
                newS = s1.takeLast(3) + " " + newS
                s1 = s1.dropLast(3)
            }
            re = (s1 + " " + newS).dropLast(1) + s2
        }

        if (ujemna){
            return '-'+re
        }else{
            return re
        }
    }

    fun Convert(v: View) {
        when (v.id) {
            R.id.TS0 -> {
                if (Currency1.text.toString() == "0") {
                    //zostawiamy tak jak jest
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "0"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "0")
                    updateChildCurrencies()
                }
            }
            R.id.TS1 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "1"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "1"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "1")
                    updateChildCurrencies()
                }
            }
            R.id.TS2 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "2"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "2"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "2")
                    updateChildCurrencies()
                }
            }
            R.id.TS3 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "3"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "3"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "3")
                    updateChildCurrencies()
                }
            }
            R.id.TS4 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "4"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "4"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "4")
                    updateChildCurrencies()
                }
            }
            R.id.TS5 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "5"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "5"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "5")
                    updateChildCurrencies()
                }
            }
            R.id.TS6 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "6"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "6"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "6")
                    updateChildCurrencies()
                }
            }
            R.id.TS7 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "7"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "7"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "7")
                    updateChildCurrencies()
                }
            }
            R.id.TS8 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "8"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "8"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "8")
                    updateChildCurrencies()
                }
            }
            R.id.TS9 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "9"
                    updateChildCurrencies()
                } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if (Currency1.text.length < MaxEQLength) {
                        Currency1.text = Currency1.text.toString() + "9"
                        przelicz()
                    }
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "9")
                    updateChildCurrencies()
                }
            }
            R.id.TSp -> {
                //Timber.tag("Mik").d(Currency1.text.toString().contains(',').toString())
                //zabezpieczenie przed dwoma przecinkami w jednej liczbie
                var s=Currency1.text
                Timber.tag("Mik").d("s: "+s)
                if(s.last().isDigit()) {
                    s=s.dropLastWhile { it.isDigit() }
                    Timber.tag("Mik").d("s:"+s+":")
                    Timber.tag("Mik").d("s.length:"+s.length)
                    if (s.length>0){
                        if (s.last() == ',') return
                    }
                }
                //koniec zabezpieczenia przed dwoma przecinkami

                if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("×")) or (Currency1.text.contains("÷"))) {
                    if ((Currency1.text.length < MaxEQLength - 1) and (Currency1.text.last() != ',') and (Currency1.text.last() != '+') and (Currency1.text.last() != '-') and (Currency1.text.last() != '×') and (Currency1.text.last() != '÷')) {
                        Currency1.text = Currency1.text.toString() + ","
                    }
                } else if ((Currency1.text.length < MaxLength - 1) && !Currency1.text.toString()
                                .contains(',')
                ) {
                    Currency1.text = Currency1.text.toString() + ","
                }
            }
            R.id.C -> {
                showAdd()
                Currency1.text = "0"
                Currency2.text = "0"
            }
            R.id.Del -> {
                showAdd()
                if ((Currency1.text.length == 1) or ((Currency1.text.first() == '-') and (Currency1.text.length == 2))) {
                    Currency1.text = "0"
                    Currency2.text = "0"
                } else {
                    if ((Currency1.text.first() == '-')) {
                        Currency1.text = GroupBy3(Currency1.text.dropLast(1).toString())
                        updateChildCurrencies()
                    } else if ((Currency1.text.contains("+")) or (Currency1.text.contains("-")) or (Currency1.text.contains("÷")) or (Currency1.text.contains("×"))) {
                        Currency1.text = Currency1.text.dropLast(1).toString()
                        przeliczDel()
                    } else {
                        Currency1.text = GroupBy3(Currency1.text.dropLast(1).toString())
                        updateChildCurrencies()
                    }
                }
            }
            R.id.plus -> {
                if ((Currency1.text.length < MaxEQLength - 1) and (Currency1.text.last().isDigit())) {
                    Currency1.text = Currency1.text.toString() + "+"
                }
            }
            R.id.minus -> {
                if ((Currency1.text.length < MaxEQLength - 1) and (Currency1.text.last().isDigit())) {
                    Currency1.text = Currency1.text.toString() + "-"
                }
            }
            R.id.razy -> {
                if ((Currency1.text.length < MaxEQLength - 1) and (Currency1.text.last().isDigit())) {
                    Currency1.text = Currency1.text.toString() + "×"
                }
            }
            R.id.dziel -> {
                if ((Currency1.text.length < MaxEQLength - 1) and (Currency1.text.last().isDigit())) {
                    Currency1.text = Currency1.text.toString() + "÷"
                }
            }
            R.id.wynik -> {
                var str = Currency1.text.toString().replace("\\s".toRegex(), "") //remove white spaces
                if (!str.last().isDigit()) {
                    return
                }
                if (!str.contains("+") and !str.contains("-") and !str.contains("×") and !str.contains("÷")) return
                str = str.replace(',', '.').replace("÷", "/").replace("×", "*")
                Timber.tag("Mik").d(str)
                val expression = ExpressionBuilder(str).build()
                try {
                    // Calculate the result and display
                    val result = expression.evaluate()
                    Timber.tag("Mik").d("Result is: " + result.toString())
                    val s = ustaw_dokladnosc(result)
                    Currency1.text = GroupBy3(s.replace('.', ','))
                    updateChildCurrencies()

                } catch (ex: ArithmeticException) {
                    // Display an error message
                    Currency1.text = "e"
                    Currency2.text = "e"
                    Timber.tag("Mik").d("Exception Caught")
                }
                showAdd()

            }
        }
    }

    fun przelicz(){
        var str = Currency1.text.toString().replace("\\s".toRegex(), "") //remove white spaces
        if (!str.last().isDigit()) { //jeśli na końcu jest przecinek lub znak to usuwamy
            return
        }
        if (!str.contains("+") and !str.contains("-") and !str.contains("×") and !str.contains("÷")) return
        str = str.replace(',', '.').replace("÷", "/").replace("×", "*")
        val expression = ExpressionBuilder(str).build()
        try {
            // Calculate the result and display
            val result = expression.evaluate()
            //Timber.tag("Mik").d("Result is: " + result.toString())
            val s = ustaw_dokladnosc(result)
            //Currency1.text = GroupBy3(s.replace('.', ','))
            Currency2.text = GroupBy3(Calculate(s))

        } catch (ex: ArithmeticException) {
            // Display an error message
            Currency2.text = "e"
        }
    }

    fun przeliczDel(){
        var str = Currency1.text.toString().replace("\\s".toRegex(), "") //remove white spaces
        if (!str.last().isDigit()) {
            str=str.dropLast(1)
        }
        //if (!str.contains("+") and !str.contains("-") and !str.contains("×") and !str.contains("÷")) return
        str = str.replace(',', '.').replace("÷", "/").replace("×", "*")
        val expression = ExpressionBuilder(str).build()
        try {
            // Calculate the result and display
            val result = expression.evaluate()
            //Timber.tag("Mik").d("Result is: " + result.toString())
            val s = ustaw_dokladnosc(result)
            //Currency1.text = GroupBy3(s.replace('.', ','))
            Currency2.text = GroupBy3(Calculate(s))

        } catch (ex: ArithmeticException) {
            // Display an error message
            Currency2.text = "e"
        }
    }


    /*fun SettingsClicked(view: View) {
        if (mInterstitialAd.isLoaded) {
            MainActivity.mInterstitialAd.show()
        }
    }*/

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("Mik").d("onDestroy called...")
        val editor = sharedPreferences.edit()
        Timber.tag("Mik").d("Currency1: " + Currency1.text.toString())

        if(IsCurrency1Number()) {
            editor.putString("Currency1Amount", Currency1.text.toString())
        }
        editor.commit()
    }

    fun showAlertDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.LoadingFailed))
        builder.setMessage(getString(R.string.CheckConnection))
        builder.setPositiveButton("OK") { dialog, which ->
        }
        //builder.show()
        val dialog: AlertDialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundColor(resources.getColor(R.color.Grey));
    }

    fun showAlertDialog104() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.LoadingFailed))
        builder.setMessage(getString(R.string.Connection104))
        builder.setPositiveButton("OK") { dialog, which ->
        }
        //builder.show()
        val dialog: AlertDialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundColor(getResources().getColor(R.color.Grey));
    }

    private fun LastUpdateDiff(): Int {
        val date1 = sharedPreferences.getLong("LastUpdateTimestamp", 0)
        val diff = Date().getTime() - date1
        val seconds = diff / 1000
        val minutes = seconds / 60
        Timber.tag("Mik").d("LastUpdateDiff: " + minutes.toInt().toString())
        return minutes.toInt()
    }

    fun BMenuClicked(view: View) {
        val intent = Intent(this, Menu::class.java)
        startActivity(intent)
    }

    fun Search1(view: View) {
        if(currencySymbols.size>100) {  //jesli waluty sie nie sciagnely to nie mozna wyszukiwać
            val intent = Intent(this, SymbolSearch::class.java)
            startActivityForResult(intent, 1)
        } else {
            showAlertDialog()
        }
    }

    fun Search2(view: View) {
        if(currencySymbols.size>100) {
            val intent = Intent(this, SymbolSearch::class.java)
            startActivityForResult(intent, 2)
        } else {
            showAlertDialog()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {//Search 1 waluty
            if (resultCode == Activity.RESULT_OK) {
                val RetrunSymbol = data!!.getStringExtra("Symbol")
                //Timber.tag("Mik").d("Search1 returned: "+RetrunSymbol)
                Currency1Symbol.text = RetrunSymbol
                if (FlagaResource(RetrunSymbol.toString()) != 0) {
                    Flaga1.setImageResource(FlagaResource(RetrunSymbol))
                } else {
                    Flaga1.setImageResource(R.drawable.pusta_flaga)
                }
                val editor = sharedPreferences.edit()
                editor.putString("Currency1Symbol", RetrunSymbol)
                editor.commit()
                if(!IsCurrency1Number()) {
                    przelicz()
                }else {
                    updateChildCurrencies() //przelicz z nowymi ustawieniami
                }
            }
        }

        if (requestCode == 2) {//Search 2 waluty
            if (resultCode == Activity.RESULT_OK) {
                val RetrunSymbol = data!!.getStringExtra("Symbol")
                //Timber.tag("Mik").d("Search2 returned: "+RetrunSymbol)
                Currency2Symbol.text = RetrunSymbol
                if (FlagaResource(RetrunSymbol) != 0) {
                    Flaga2.setImageResource(FlagaResource(RetrunSymbol))
                } else {
                    Flaga2.setImageResource(R.drawable.pusta_flaga)
                }
                val editor = sharedPreferences.edit()
                editor.putString("Currency2Symbol", RetrunSymbol)
                editor.commit()
                if(!IsCurrency1Number()) {
                    przelicz()
                }else {
                    updateChildCurrencies() //przelicz z nowymi ustawieniami
                }
            }
        }
    }

    public fun ustaw_dokladnosc(c2: Double):String {
        var str: String = ""
        var ujemna: Boolean = false
        //Timber.tag("Mik").d(c2.toString())
        var c = c2
        if(c2<0) {
            ujemna=true
            c=c2.absoluteValue
        }

        if (c == 0.0) {
            return "0"
        }else if (c >= 0.1) { //lub Waluta
            str = String.format("%.2f", c)
            if (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else if (c >= 0.01) {
            str = String.format("%.3f", c)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else if (c >= 0.001) {
            str = String.format("%.4f", c)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else if (c >= 0.0001) {
            str = String.format("%.5f", c)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else if (c >= 0.00001) {
            str = String.format("%.6f", c)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else if (c >= 0.000001) {
            str = String.format("%.7f", c)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else if (c >= 0.0000001) {
            str = String.format("%.8f", c)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else if (c >= 0.00000001) {
            str = String.format("%.9f", c)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else if (c >= 0.000000001) {
            str = String.format("%.10f", c)
            while (str.takeLast(1) == "0") {
                str = str.dropLast(1)
            }
            if ((str.takeLast(1) == ".") or (str.takeLast(1) == ",")) {
                str = str.dropLast(1)
            }
        }else return "0"

    /*else { //dla Kryptowalut
        str = String.format("%.10f", c)
        while (str.takeLast(1) == "0") {
            str = str.dropLast(1)
        }
        while (str.length >= 5 && str.takeLast(3).toString().toInt() >= 101) {
            str = str.dropLast(1)
        }
    }*/
        if(ujemna) str='-'+str
        return str
    }

    private fun setFlags()
    {
        //Timber.tag("Mik").d("Init Currency 1: "+Currency1.text)
        //Currency2.text = GroupBy3(Calculate(Currency1.text.toString()))
        //Timber.tag("Mik").d("Init Currency 2: "+Currency2.text)
        val Currency1Sym = sharedPreferences.getString("Currency1Symbol", "EUR")!!
        val Currency2Sym = sharedPreferences.getString("Currency2Symbol", "EUR") !!
        Currency1Symbol.text = Currency1Sym
        Currency2Symbol.text = Currency2Sym

        if (FlagaResource(Currency1Sym) != 0) {
            Flaga1.setImageResource(FlagaResource(Currency1Sym))
        } else {
            Flaga1.setImageResource(R.drawable.pusta_flaga)
        }

        if (FlagaResource(Currency2Sym) != 0) {
            Flaga2.setImageResource(FlagaResource(Currency2Sym))
        } else {
            Flaga2.setImageResource(R.drawable.pusta_flaga)
        }
    }

    fun swap(v: View) {
        val editor = sharedPreferences.edit()
        val c1 = sharedPreferences.getString("Currency1Symbol", "EUR")
        val c2 = sharedPreferences.getString("Currency2Symbol", "EUR")
        //Swapa można zrobić tylko jeśli jest liczba. W przypadku równania nie można zrobić

        if(IsCurrency1Number()) {
            editor.putString("Currency1Symbol", c2)
            editor.putString("Currency2Symbol", c1)
            editor.commit()
            setFlags()
            updateChildCurrencies()
        }
        showAdd()
    }

    public fun IsCurrency1Number():Boolean{
        var tmp = Currency1.text.toString()
        if (tmp.first()=='-') {
            tmp=tmp.drop(1)
        }
        Timber.tag("Mik").d("tmp: " + tmp)
        tmp = tmp.replace(",", "").replace(" ", "")
        return tmp.isDigitsOnly()
    }

    private fun updateChildCurrencies(){
        if(IsCurrency1Number()) {
            Currency2.text = GroupBy3(Calculate(Currency1.text.toString()))
        }
        Kurs.text = "1 "+sharedPreferences.getString("Currency1Symbol", "EUR")+" = "+Calculate("1")+" "+sharedPreferences.getString("Currency2Symbol", "EUR")
    }


    public fun CurrencySize(c: Int):Float{
        if (c<12) return resources.getDimension(R.dimen._30ssp)
        else if (c<14) return resources.getDimension(R.dimen._25ssp)
        else if (c<15) return resources.getDimension(R.dimen._24ssp)
        else if (c<16) return resources.getDimension(R.dimen._23ssp)
        else if (c<17) return resources.getDimension(R.dimen._22ssp)
        else if (c<18) return resources.getDimension(R.dimen._21ssp)
        else if (c<19) return resources.getDimension(R.dimen._20ssp)
        else if (c<20) return resources.getDimension(R.dimen._19ssp)
        else if (c<21) return resources.getDimension(R.dimen._18ssp)
        else if (c<22) return resources.getDimension(R.dimen._17ssp)
        return resources.getDimension(R.dimen._16ssp)

        /*while (Currency1.lineCount > 1){
            C1actual_size = C1actual_size-1
        }
        if (Currency1.lineCount > 1) {
            C1actual_size = resources.getDimension(R.dimen._20ssp)
            return resources.getDimension(R.dimen._20ssp)
        }else return C1actual_size*/
    }

    private fun showAdd(){
       if (sharedPreferences.getInt("RunNumber", 10) >= ShowIntRunNumber && F().LastInterstitialMin(sharedPreferences) >= ShowIntMin){
           if (mInterstitialAd.isLoaded) {
                mInterstitialAd.show()
            } else {
                Timber.tag("Mik").d("The interstitial wasn't loaded yet.")
            }
       }
    }

    private fun initReviews() {
        manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                reviewInfo = request.result
                Timber.tag("Mik").d("initReview() Done")
            } else {
                Timber.tag("Mik").d("Problem z initReview()")
            }
        }
    }

    private fun askReview(){
        if (reviewInfo != null) {
            val editor = sharedPreferences.edit()
            editor.putLong("DataZapytaniaRating", Date().getTime()) //Zapisujemy date zapytania o rating
            editor.commit()
            manager.launchReviewFlow(this, reviewInfo!!).addOnFailureListener {//Cos poszło nie tak, ktoś nie zrobił review
                // Log error and continue with the flow
                Timber.tag("Mik").d( "There was issue with review, User did not completed Review")
            }.addOnCompleteListener { _ ->
                // NIezależnie czy user dał submit czy nie będzie on Complete. Jeśli user już robił feedback to poprostu nie pokaże sie okienko.
                Timber.tag("Mik").d( "Review was succesfull")
            }
        }
    }

}