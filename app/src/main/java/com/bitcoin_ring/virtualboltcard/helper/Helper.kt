package com.bitcoin_ring.virtualboltcard.helper

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.Random
import java.util.regex.Pattern

class Helper {
    companion object {
        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            connectivityManager?.let {
                it.getNetworkCapabilities(connectivityManager.activeNetwork)?.apply {
                    return when {
                        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                        else -> false
                    }
                }
            }
            return false
        }

        fun generateKey(byteSize: Int): String {
            val random = Random()
            val key = ByteArray(byteSize)
            random.nextBytes(key)

            val sb = StringBuilder(key.size * 2)
            for (byte in key) {
                val intVal = byte.toInt() and 0xff
                if (intVal < 0x10) sb.append('0')
                sb.append(Integer.toHexString(intVal))
            }
            return sb.toString()
        }

        fun generateSecureKey(password: String): ByteArray {
            // Key derivation function goes here
            // TODO: This is a very simplified example, not suitable for production
            return password.toByteArray()
        }

        fun generateRandomString(length: Int): String {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            return (1..length)
                .map { allowedChars.random() }
                .joinToString("")
        }

        fun createRandomWord():String {
            val wordsPart1 = listOf(
                "happy",
                "sad",
                "angry",
                "excited",
                "calm",
                "sudden",
                "lazy",
                "bright",
                "dark",
                "funny",
                "serious",
                "wonky",
                "loud",
                "quiet",
                "rough",
                "smooth",
                "soft",
                "hard",
                "warm",
                "cold"
            )
            val wordsPart2 = listOf(
                "cat",
                "dog",
                "bird",
                "tree",
                "flower",
                "hill",
                "river",
                "ocean",
                "desk",
                "clock",
                "car",
                "bike",
                "book",
                "tv",
                "phone",
                "shoe",
                "hat",
                "city",
                "friend",
                "music"
            )
            val random = Random()
            val randomWordPart1 = capitalize(wordsPart1[random.nextInt(wordsPart1.size)])
            val randomWordPart2 = capitalize(wordsPart2[random.nextInt(wordsPart2.size)])

            val compositeWord = "$randomWordPart1-$randomWordPart2"
            return(compositeWord)
        }

        fun capitalize(word: String): String {
            return word.replaceFirstChar { it.uppercase() }
        }
    }
}

fun String.isValidUrl(): Boolean {
    val p = Pattern.compile(
        "^(https?:\\/\\/)" +        // scheme
                "((([a-z\\d]([a-z\\d-]*[a-z\\d])*)\\.)+[a-z]{2,}|" +  // domain name and extension
                "((\\d{1,3}\\.){3}\\d{1,3}))" +  // OR ip (v4) address
                "(\\:\\d+)?" +     // port
                "(\\/[-a-z\\d%_.~+]*)*" +   // path
                "(\\?[;&a-z\\d%_.~+=-]*)?" +   // query string
                "(\\#[-a-z\\d_]*)?$", // fragment locator
        Pattern.CASE_INSENSITIVE
    )
    val m = p.matcher(this)
    return m.matches()
}
