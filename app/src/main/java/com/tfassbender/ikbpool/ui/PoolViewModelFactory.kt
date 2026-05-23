package com.tfassbender.ikbpool.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tfassbender.ikbpool.IkbPoolApp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY

val PoolViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) as IkbPoolApp
        PoolViewModel(
            repository = app.container.repository,
            engine = app.container.recommendationEngine,
        )
    }
}
