package com.example.myapplication.app

import android.app.Application

class MyApplication : Application() {
    val serviceGraph: ServiceGraph by lazy {
        ServiceGraph(this)
    }
}
