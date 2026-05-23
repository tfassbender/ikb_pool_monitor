package com.tfassbender.ikbpool.data.source

interface PoolDataSource<T> {
    suspend fun fetch(): T
}
