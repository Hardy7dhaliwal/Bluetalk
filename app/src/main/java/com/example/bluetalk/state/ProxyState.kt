package com.example.bluetalk.state

sealed class ProxyState{
    class StartRREQ( val path: String): ProxyState()
    class StartRREP(val path:String): ProxyState()
}

