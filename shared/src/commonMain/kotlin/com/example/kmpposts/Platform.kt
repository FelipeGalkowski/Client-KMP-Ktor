package com.example.kmpposts

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform