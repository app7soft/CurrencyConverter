package com.app7soft.currencyconverter

import com.app7soft.currencyconverter.MainActivity.Companion.RateRunNumber
import com.app7soft.currencyconverter.MainActivity.Companion.ShowIntMin
import com.app7soft.currencyconverter.MainActivity.Companion.ShowIntRunNumber
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import timber.log.Timber

fun initDatabaseData(){
    val database = Firebase.database
    val myRef1 = database.getReference("ShowIntMin")
    val myRef2 = database.getReference("ShowIntRunNumber")
    val myRef3 = database.getReference("RateRunNumber")

    myRef1.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(dataSnapshot: DataSnapshot) {
            val value = dataSnapshot.getValue<Int>()
            Timber.tag("Mik").d("ShowIntMin: "+value.toString())
            if (value != null) { //zczytujemy wartość z Bazy Danych
                ShowIntMin = value
            }
        }

        override fun onCancelled(error: DatabaseError) {
            // Failed to read value
            Timber.tag("Mik").d("Failed to read ShowIntMin value")
        }
    })

    myRef2.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(dataSnapshot: DataSnapshot) {
            val value = dataSnapshot.getValue<Int>()
            Timber.tag("Mik").d("ShowIntRunNumber: "+value.toString())
            if (value != null) { //zczytujemy wartość z Bazy Danych
                ShowIntRunNumber = value
            }
        }
        override fun onCancelled(error: DatabaseError) {
            // Failed to read value
            Timber.tag("Mik").d("Failed to read ShowIntRunNumber value")
        }
    })

    myRef3.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(dataSnapshot: DataSnapshot) {
            val value = dataSnapshot.getValue<Int>()
            Timber.tag("Mik").d("RateRunNumber: "+value.toString())
            if (value != null) { //zczytujemy wartość z Bazy Danych
                RateRunNumber = value
            }
        }
        override fun onCancelled(error: DatabaseError) {
            // Failed to read value
            Timber.tag("Mik").d("Failed to read RateRunNumber value")
        }
    })
}