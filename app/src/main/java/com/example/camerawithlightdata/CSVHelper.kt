package com.example.camerawithlightdata

import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter

class CSVHelper {
    lateinit var mFileWriter: FileWriter

    fun appendStringToCSV(input: String, filename: String) {
        var baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
        var filepath = baseDir + File.separator + filename
        var f = File(filepath)

        val writer = CSVWriter(FileWriter(filename), CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)
        val entries = input.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        writer.writeNext(entries)
        writer.close()
    }
}