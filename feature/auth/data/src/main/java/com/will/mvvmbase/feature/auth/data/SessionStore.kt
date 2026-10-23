package com.will.mvvmbase.feature.auth.data

import com.will.mvvmbase.feature.auth.domain.AuthSessionTokens

interface SessionStore {
    fun readSession(): AuthSessionTokens?
    fun saveSession(tokens: AuthSessionTokens)
    fun clear()
}
