package com.app7soft.currencyconverter


import MySingleton
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.*
import com.google.android.gms.ads.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.time.*
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    companion object {
        //var InterstitialID = "ca-app-pub-5209961561922052/7183758786" //Real for 361
        var InterstitialID = "ca-app-pub-3940256099942544/1033173712" //TestAd
        lateinit var mInterstitialAd: InterstitialAd
        var AdErrorCount = 0 //próbujemy wczytywac maksymalnie 5 razy co 2 sekundy
        var ShowIntRunNumber: Int = 4 //Interstitial Reklmay pokazujemy od 4 uruchomienia
        var ShowIntMin: Int = 480 //Interstitial pokazujemy nieczęsciej niz co 480 min = 8h
        var currencySymbols: ArrayList<String> = ArrayList()
        var SymbolsNames: ArrayList<String> = ArrayList()
        var SymbolsToNamesCollection = HashMap<String, Any>()
        lateinit var sharedPreferences: SharedPreferences
    }

    private var CurrencyRatesResponse: JSONObject? = null //Obecne kursy walut zwócone przez API
    private var SymbolsToNamesResponse: JSONObject? = null //Mapowanie symboli na pełna Nazwe waluty zwócone przez API
    //private var SymbolsToNamesCollection = HashMap<String, Any>()
    private var ratesCollection = HashMap<String, Any>()
    //lateinit var sharedPreferences: SharedPreferences
    var MaxLength: Int = 17
    var RatesUpdateOnStart: Boolean = true
    var gson = Gson()
    var json: String? ="" //temporary string to convert gson.JsonObject to JSONObject
    var CurrentCurrency: String =""

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        if(BuildConfig.DEBUG){
            Timber.uprootAll()
            Timber.plant(Timber.DebugTree())
        }
        setContentView(R.layout.activity_main)
        DataLoading.setVisibility(View.GONE)

        sharedPreferences = getSharedPreferences("myPref", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val CurrentCountry = resources.configuration.locale
        CurrentCurrency = Currency.getInstance(CurrentCountry).currencyCode

        //Timber.tag("Mik").d(Currency.getInstance("LLL").getDisplayName())

        if(!sharedPreferences.contains("RunNumber")){
            Timber.tag("Mik").d("APLIKACJA URUCHOMIONA PIERWSZY RAZ....")
            //Timber.tag("Mik").d("Currency code: "+CurrentCurrency)
            editor.putInt("RunNumber", 1)
            editor.putString("LastUpdate", " rates no updated yet")
            editor.putString("Currency1Symbol", CurrentCurrency)
            if(CurrentCurrency != "EUR"){
                editor.putString("Currency2Symbol", "EUR")
            }else{
                editor.putString("Currency2Symbol", "USD")
            }
            editor.commit()
            Currency1.text = "0"

        }else {
            Timber.tag("Mik").d("To NIE jest pierwsza instalacja aplikacji")
            editor.putInt("RunNumber", sharedPreferences.getInt("RunNumber", 0) + 1) //Inkrementujemy numer uruchomienia
            Currency1.text = sharedPreferences.getString("Currency1Amount", "0")
            editor.commit()
        }
        setFlags() //Ustawiamy Flagi i Symbole

        RefreshDate.text = getString(R.string.LastUpdate) + " " + sharedPreferences.getString("LastUpdate", "")

        if (sharedPreferences.contains("CurrencyRatesResponse")) {
            CurrencyRatesResponse = gson.fromJson(sharedPreferences.getString("CurrencyRatesResponse", ""), JSONObject::class.java)
            SymbolsToNamesResponse = gson.fromJson(sharedPreferences.getString("SymbolsToNamesResponse", ""), JSONObject::class.java)
            InitRates()
            InitNames()
            updateChildCurrencies()
        }

        //If first time or last update was mor than one hour
        if(!sharedPreferences.contains("RunNumber") or (LastUpdateDiff()>60)){
            getRequest() //Updejt walut
        }

        //setInitView()

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
                    if (AdErrorCount <4) {
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

        //initInterstitialAds(sharedPreferences)
        initDatabaseData()  //zczytujemy dane z Bazy Danych Firebase
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
                    //Timber.tag("Mik").d(response.toString())
                    /*timestamp = response.getLong("timestamp")
                    val dt = Instant.ofEpochSecond(timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()*/
                    //Timber.tag("Mik").d("responseOK")
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


                },
                {
                    //Timber.tag("Mik").d("response Not OK")
                    RefreshDate.text = getString(R.string.LastUpdate) + " " + sharedPreferences.getString("LastUpdate", "")
                    if ((RatesUpdateOnStart == false) or (sharedPreferences.getInt("RunNumber", 0) == 1)) {
                        //Timber.tag("Mik").d(RatesUpdateOnStart.toString())
                        //Timber.tag("Mik").d(sharedPreferences.getInt("RunNumber", 0).toString())
                        showAlertDialog()
                    } //else if (rating starszy niż kilka dni) pokaż alert
                    RatesUpdateOnStart = false
                    //jeśli sie nie ma internetu to pobieramy ostatnie waluty które były
                }
        ))

        MySingleton.getInstance(this).addToRequestQueue(JsonObjectRequest(Request.Method.GET,
                urlCurrencySymbols,
                null,
                { response ->
                    ///completionHandlerForSymbolNames(response)
                    SymbolsToNamesResponse = response.getJSONObject("symbols")  //namesResponse = response.getJSONObject("symbols")
                    InitNames()
                    editor.putString("SymbolsToNamesResponse", gson.toJson(SymbolsToNamesResponse))
                    editor.commit()
                    DataLoading.setVisibility(View.GONE)
                    //Timber.tag("Mik").d("SymbolsToNamesResponse:"+SymbolsToNamesResponse.toString())
                },
                {
                    DataLoading.setVisibility(View.GONE)
                    //jeśli sie nie ma internetu to pobieramy ostatnie waluty które były
                }
        ))

    }

    private fun InitRates(){
        ratesCollection = ConvertJsonToHash(CurrencyRatesResponse!!)
        currencySymbols = ArrayList(ratesCollection.keys)
        currencySymbols.sort()
        //Timber.tag("Mik").d("CurrentCurrency: "+CurrentCurrency)
        //usuwam najpopularniejsze symbole żeby dodac je jeszcze raz
        //CurrentCurrency="EUR"
        if(CurrentCurrency !in arrayListOf("USD", "EUR","JPY", "GBP", "AUD", "CAD", "CHF", "CNY", "BTC")){
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
        currencySymbols.addAll(0, listOf("USD", "EUR","JPY", "GBP", "AUD", "CAD", "CHF", "CNY", "BTC"))
        if(CurrentCurrency !in arrayListOf("USD", "EUR","JPY", "GBP", "AUD", "CAD", "CHF", "CNY", "BTC")){
            currencySymbols.add(0, CurrentCurrency)
        }
        //Timber.tag("Mik").d("Currency Symbol After: "+currencySymbols.toString())
    }

    private fun InitNames(){
        SymbolsToNamesCollection = ConvertJsonToHash(SymbolsToNamesResponse!!)
        //Timber.tag("Mik").d(SymbolsToNamesCollection.toString())
        for ((key, value) in SymbolsToNamesCollection) {
            val sym = Currency.getInstance(key).getDisplayName()
            if(key != sym){
                SymbolsToNamesCollection[key] = Currency.getInstance(key).getDisplayName()
            }
        }
        //Timber.tag("Mik").d(SymbolsToNamesCollection.toString())

        for (row in SymbolsToNamesCollection.values) {
            SymbolsNames.add(row.toString())
        }
        //Timber.tag("Mik").d(SymbolsToNamesCollection.values.toString())
        //Timber.tag("Mik").d("SymbolsNames: "+SymbolsNames.toString())
    }

    private fun ConvertJsonToHash(rates: JSONObject): HashMap<String, Any>{
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

    fun Calculate(cur1: String): String{
        val c1: Double
        val c2: Double
        var str: String
        //val namesAndValuesMap = matchCurrencyNamesWithCodes(CurrencyRatesResponse, SymbolsToNamesResponse)

        c1 = cur1.replace("\\s".toRegex(), "").replace(',', '.').toDouble()

        /*if(from_spinner.selectedItem == null){ //jeśli nie ma żadnych danych
            return "0"
        }*/

        //Timber.tag("Mik").d("ratesCollection[1]: "+ratesCollection[sharedPreferences.getString("Currency1Symbol", "EUR")])
        //Timber.tag("Mik").d("Symbol1: "+sharedPreferences.getString("Currency1Symbol", "EUR"))
        //Timber.tag("Mik").d("ratesCollection[2]: "+ratesCollection[sharedPreferences.getString("Currency2Symbol", "EUR")])
        //Timber.tag("Mik").d("Symbol2: "+sharedPreferences.getString("Currency2Symbol", "EUR"))
        c2 = calculateEquivalent(ratesCollection[sharedPreferences.getString("Currency1Symbol", "EUR")]!!, ratesCollection[sharedPreferences.getString("Currency2Symbol", "USD")]!!, c1)
        //Timber.tag("Mik").d("CalculateEquivalent: "+c2.toString())

        if (c2 == 0.0){
            return "0"
        }
        else if(c2>=1){ //lub Waluta
            str = String.format("%.2f", c2)
            if (str.takeLast(1)=="0"){
                str=str.dropLast(1)
            }
        }else{ //dla Kryptowalut
            str = String.format("%.10f", c2)
            while (str.takeLast(1)=="0"){
                str=str.dropLast(1)
            }
            while (str.length>=5 && str.takeLast(3).toString().toInt()>=101){
                str=str.dropLast(1)
            }
        }

        return str.replace('.', ',')
    }

    fun GroupBy3(s: String): String {
        var newS: String = ""
        var s1: String = ""
        var s2: String = ""
        var s = s.replace("\\s".toRegex(), "")
        if (s.contains(',')){
            s1=s.split(',')[0]
            s2=','+s.split(',')[1]
        } else {
            s1=s
        }

        if(s1.length <=3 ) {
            return s
        }else{
            while (s1.length>3){
                newS = s1.takeLast(3)+" "+newS
                s1=s1.dropLast(3)
            }
            return (s1+" "+newS).dropLast(1)+s2
        }

    }

    fun Convert(v: View) {
        when (v.id) {
            R.id.TS0 -> {
                if (Currency1.text.toString() == "0") {
                    //zostawiamy tak jak jest
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "0")
                    updateChildCurrencies()
                }
            }
            R.id.TS1 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "1"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "1")
                }
                updateChildCurrencies()
            }
            R.id.TS2 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "2"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "2")
                }
                updateChildCurrencies()
            }
            R.id.TS3 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "3"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "3")
                }
                updateChildCurrencies()
            }
            R.id.TS4 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "4"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "4")
                }
                updateChildCurrencies()
            }
            R.id.TS5 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "5"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "5")
                }
                updateChildCurrencies()
            }
            R.id.TS6 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "6"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "6")
                }
                updateChildCurrencies()
            }
            R.id.TS7 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "7"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "7")
                }
                updateChildCurrencies()
            }
            R.id.TS8 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "8"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "8")
                }
                updateChildCurrencies()
            }
            R.id.TS9 -> {
                if (Currency1.text.toString() == "0") {
                    Currency1.text = "9"
                } else if (Currency1.text.length < MaxLength) {
                    Currency1.text = GroupBy3(Currency1.text.toString() + "9")
                }
                updateChildCurrencies()
            }
            R.id.TSp -> {
                //Timber.tag("Mik").d(Currency1.text.toString().contains(',').toString())
                if ((Currency1.text.length < MaxLength) && !Currency1.text.toString()
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
                if (Currency1.text.length == 1) {
                    Currency1.text = "0"
                    Currency2.text = "0"
                } else {
                    Currency1.text = GroupBy3(Currency1.text.dropLast(1).toString())
                    updateChildCurrencies()
                }
            }
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

        //editor.putString("Currency1Symbol", from_spinner.selectedItem.toString())
        editor.putString("Currency1Amount", Currency1.text.toString())
        //editor.putString("Currency2Symbol", to_spinner.selectedItem.toString())
        editor.commit()
    }

    fun showAlertDialog(){
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.LoadingFailed))
        builder.setMessage(getString(R.string.CheckConnection))
        builder.setPositiveButton("OK"){ dialog, which ->
        }
        builder.show()
    }

    private fun LastUpdateDiff(): Int{
        val date1 = sharedPreferences.getLong("LastUpdateTimestamp", 0)
        val diff = Date().getTime()-date1
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
        val intent = Intent(this, SymbolSearch::class.java)
        startActivityForResult(intent, 1)
    }

    fun Search2(view: View) {
        val intent = Intent(this, SymbolSearch::class.java)
        startActivityForResult(intent, 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {//Search 1 waluty
            if (resultCode == Activity.RESULT_OK) {
                val RetrunSymbol  = data!!.getStringExtra("Symbol")
                //Timber.tag("Mik").d("Search1 returned: "+RetrunSymbol)
                Currency1Symbol.text=RetrunSymbol
                if(FlagaResource(RetrunSymbol.toString()) != 0){
                    Flaga1.setImageResource(FlagaResource(RetrunSymbol))
                }else{
                    Flaga1.setImageResource(R.drawable.pusta_flaga)
                }
                val editor = sharedPreferences.edit()
                editor.putString("Currency1Symbol", RetrunSymbol)
                editor.commit()
                updateChildCurrencies() //przelicz z nowymi ustawieniami
            }
        }

        if (requestCode == 2) {//Search 2 waluty
            if (resultCode == Activity.RESULT_OK) {
                val RetrunSymbol  = data!!.getStringExtra("Symbol")
                //Timber.tag("Mik").d("Search2 returned: "+RetrunSymbol)
                Currency2Symbol.text=RetrunSymbol
                if(FlagaResource(RetrunSymbol) != 0){
                    Flaga2.setImageResource(FlagaResource(RetrunSymbol))
                }else{
                    Flaga2.setImageResource(R.drawable.pusta_flaga)
                }
                val editor = sharedPreferences.edit()
                editor.putString("Currency2Symbol", RetrunSymbol)
                editor.commit()
                updateChildCurrencies() //przelicz z nowymi ustawieniami
            }
        }
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
        editor.putString("Currency1Symbol", c2)
        editor.putString("Currency2Symbol", c1)
        editor.commit()
        setFlags()
        Currency2.text = GroupBy3(Calculate(Currency1.text.toString()))
    }

    private fun updateChildCurrencies(){
        Currency2.text = GroupBy3(Calculate(Currency1.text.toString()))
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

    public fun showAdd(){
       if (sharedPreferences.getInt("RunNumber", 10) >= MainActivity.ShowIntRunNumber && MainActivity().LastInterstitialMin(sharedPreferences) >= MainActivity.ShowIntMin){
            if (mInterstitialAd.isLoaded) {
                mInterstitialAd.show()
            } else {
                Timber.tag("Mik").d( "The interstitial wasn't loaded yet.")
            }
       }
    }

}