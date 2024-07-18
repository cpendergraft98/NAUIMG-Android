package com.example.nauimg

import android.content.Context
import org.json.JSONArray
import java.io.File

object FileUtils {
    private const val FILE_NAME = "location_data.json"

    fun readJSONArrayFromFile(context: Context): JSONArray{
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()){
            val content = file.readText()
            JSONArray(content)
        }else{
            JSONArray()
        }
    }

    fun writeJSONArrayToFile(context: Context, jsonArray: JSONArray){
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(jsonArray.toString())
    }
}