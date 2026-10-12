package com.example.localreader

import android.app.Application
import com.example.localreader.di.AppGraph

class LocalReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
