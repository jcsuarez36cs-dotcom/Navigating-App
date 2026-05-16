package com.navigating.app.io

import com.navigating.app.data.Feature
import com.navigating.app.data.ShapeType
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ShapefileParser {

    fun parse(shpFile: File, dbfFile: File?): List<Feature> {
        val shapes = parseShp(shpFile)
        val attributes = if (dbfFile?.exists() == true) parseDbf(dbfFile) else emptyList()
        return shapes.mapIndexed { i, pair ->
            val attrs = if (i < attributes.size) attributes[i] else emptyMap()
            val name = attrs["NAME"] ?: attrs["name"] ?: attrs["Name"] ?:
                       attrs.values.firstOrNull() ?: "Feature ${i + 1}"
            Feature(
                name = name,
                shapeType = pair.first,
                coordinates = pair.second,
                attributes = attrs,
                sourceFile = shpFile.name
            )
        }
    }

    private fun parseShp(file: File): List<Pair<ShapeType, List<List<Double>>>> {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes)
        buf.position(100)
        val result = mutableListOf<Pair<ShapeType, List<List<Double>>>>()

        while (buf.remaining() >= 12) {
            buf.order(ByteOrder.BIG_ENDIAN)
            @Suppress("UNUSED_VARIABLE")
            val recNum = buf.int
            val contentLen = buf.int
            if (contentLen <= 0 || buf.remaining() < 4) break
            buf.order(ByteOrder.LITTLE_ENDIAN)
            val shapeType = buf.int
            when (shapeType) {
                0 -> { /* Null shape */ }
                1 -> {
                    if (buf.remaining() >= 16) {
                        val x = buf.double
                        val y = buf.double
                        result.add(Pair(ShapeType.POINT, listOf(listOf(x, y))))
                    }
                }
                3 -> {
                    val geom = readPolyGeom(buf)
                    if (geom.isNotEmpty()) result.add(Pair(ShapeType.POLYLINE, geom))
                }
                5 -> {
                    val geom = readPolyGeom(buf)
                    if (geom.isNotEmpty()) result.add(Pair(ShapeType.POLYGON, geom))
                }
                else -> {
                    val remaining = contentLen * 2 - 4
                    if (remaining > 0 && buf.remaining() >= remaining) {
                        buf.position(buf.position() + remaining)
                    }
                }
            }
        }
        return result
    }

    private fun readPolyGeom(buf: ByteBuffer): List<List<Double>> {
        if (buf.remaining() < 32 + 4 + 4) return emptyList()
        buf.position(buf.position() + 32)
        val numParts = buf.int
        val numPoints = buf.int
        if (numParts <= 0 || numPoints <= 0) return emptyList()
        if (buf.remaining() < numParts * 4 + numPoints * 16) return emptyList()
        @Suppress("UNUSED_VARIABLE")
        val parts = IntArray(numParts) { buf.int }
        val allPoints = Array(numPoints) {
            val x = buf.double
            val y = buf.double
            listOf(x, y)
        }
        return allPoints.toList()
    }

    private fun parseDbf(file: File): List<Map<String, String>> {
        val bytes = file.readBytes()
        if (bytes.size < 32) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val numRecords = buf.int
        val headerSize = buf.short.toInt() and 0xFFFF
        val recordSize = buf.short.toInt() and 0xFFFF
        val fields = mutableListOf<Pair<String, Int>>()
        var pos = 32
        while (pos + 32 <= headerSize && bytes[pos] != 0x0D.toByte()) {
            val nameBytes = bytes.copyOfRange(pos, pos + 11)
            val name = String(nameBytes).trimEnd('\u0000').trim()
            val fieldLen = bytes[pos + 16].toInt() and 0xFF
            fields.add(Pair(name, fieldLen))
            pos += 32
        }
        if (fields.isEmpty() || recordSize <= 0) return emptyList()
        val records = mutableListOf<Map<String, String>>()
        var recPos = headerSize
        repeat(numRecords) {
            if (recPos + recordSize > bytes.size) return@repeat
            val recBytes = bytes.copyOfRange(recPos, recPos + recordSize)
            val map = mutableMapOf<String, String>()
            var fieldPos = 1
            for ((fname, flen) in fields) {
                if (fieldPos + flen <= recBytes.size) {
                    val value = String(recBytes, fieldPos, flen, Charsets.ISO_8859_1).trim()
                    map[fname] = value
                }
                fieldPos += flen
            }
            records.add(map)
            recPos += recordSize
        }
        return records
    }
}
