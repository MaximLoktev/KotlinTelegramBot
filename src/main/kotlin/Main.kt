package org.example

import java.io.File

fun main() {
    val wordsFile = File("words.txt")
//    wordsFile.createNewFile()

    val strings = wordsFile.readLines()

    strings.forEach {
        println(it)
    }
}