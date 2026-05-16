package com.navigating.app.io

import com.navigating.app.data.Feature
import com.navigating.app.data.ShapeType
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ShapefileWriter {

    fun write(features: List<Feature>, outputDir: File, baseName: String) {
        if (features.isEmpty()) return
        val shapeType = features.first().shapeType
        val shpFile = File(outputDir, "$baseName.shp")
        val shxFile = File(outputDir, "$baseName.shx")
        val dbfFile = File(outputDir, "$baseName.dbf")
        writeShpShx(features, shapeType, shpFile, shxFile)
        writeDbf(features, dbfFile)
    }

    private fun writeShpShx(features: List<Feature>, shapeType: ShapeType, shpFile: File, shxFile: File) {
        val shpOut = shpFile.outputStream().buffered()
        val shxOut = shxFile.outputStream().buffered()
        val records = features.map { buildRecord(it, shapeType) }
        var shpLen = 50
        records.forEach { shpLen += 4 + it.size / 2 }
        shpOut.write(buildFileHeader(shpLen, shapeType, features))
        shxOut.write(buildFileHeader(50 + records.size * 4, shapeType, features))
        var offset = 50
        records.forEachIndexed { i, rec ->
            val contentLen = rec.size / 2
            val recHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            recHeader.putInt(i + 1)
            recHeader.putInt(contentLen)
            shpOut.write(recHeader.array())
            shpOut.write(rec)
            val shxRec = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            shxRec.putInt(offset)
            shxRec.putInt(contentLen)
            shxOut.write(shxRec.array())
            offset += 4 + contentLen
        }
        shpOut.flush(); shpOut.close()
        shxOut.flush(); shxOut.close()
    }

    private fun buildRecord(feature: Feature, shapeType: ShapeType): ByteArray {
        return when (shapeType) {
            ShapeType.POINT -> {
                val pt = feature.coordinates.firstOrNull() ?: listOf(0.0, 0.0)
                val buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(1)
                buf.putDouble(pt.getOrElse(0) { 0.0 })
                buf.putDouble(pt.getOrElse(1) { 0.0 })
                buf.array()
            }
            ShapeType.POLYLINE, ShapeType.POLYGON -> {
                val stCode = if (shapeType == ShapeType.POLYLINE) 3 else 5
                val pts = feature.coordinates
                val bbox = computeBbox(pts)
                val size = 4 + 32 + 4 + 4 + 4 + pts.size * 16
                val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(stCode)
                buf.putDouble(bbox[0]); buf.putDouble(bbox[1])
                buf.putDouble(bbox[2]); buf.putDouble(bbox[3])
                buf.putInt(1)
                buf.putInt(pts.size)
                buf.putInt(0)
                pts.forEach { pt ->
                    buf.putDouble(pt.getOrElse(0) { 0.0 })
                    buf.putDouble(pt.getOrElse(1) { 0.0 })
                }
                buf.array()
            }
        }
    }

    private fun computeBbox(pts: List<List<Double>>): DoubleArray {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        pts.forEach {
            minX = minOf(minX, it.getOrElse(0) { 0.0 })
            minY = minOf(minY, it.getOrElse(1) { 0.0 })
            maxX = maxOf(maxX, it.getOrElse(0) { 0.0 })
            maxY = maxOf(maxY, it.getOrElse(1) { 0.0 })
        }
        return doubleArrayOf(minX, minY, maxX, maxY)
    }

    private fun buildFileHeader(fileLenWords: Int, shapeType: ShapeType, features: List<Feature>): ByteArray {
        val stCode = when (shapeType) {
            ShapeType.POINT -> 1
            ShapeType.POLYLINE -> 3
            ShapeType.POLYGON -> 5
        }
        val allPts = features.flatMap { it.coordinates }
        val bbox = if (allPts.isNotEmpty()) computeBbox(allPts) else doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val buf = ByteBuffer.allocate(100)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.putInt(9994)
        repeat(5) { buf.putInt(0) }
        buf.putInt(fileLenWords)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(1000)
        buf.putInt(stCode)
        buf.putDouble(bbox[0]); buf.putDouble(bbox[1])
        buf.putDouble(bbox[2]); buf.putDouble(bbox[3])
        repeat(4) { buf.putDouble(0.0) }
        return buf.array()
    }

    private fun writeDbf(features: List<Feature>, dbfFile: File) {
        val allKeys = features.flatMap { it.attributes.keys }.distinct()
        if (allKeys.isEmpty()) {
            writeDbfWithFields(features, listOf("NAME"), dbfFile)
        } else {
            writeDbfWithFields(features, allKeys, dbfFile)
        }
    }

    private fun writeDbfWithFields(features: List<Feature>, fields: List<String>, dbfFile: File) {
        val fieldLen = 50
        val numFields = fields.size
        val headerSize = 32 + numFields * 32 + 1
        val recordSize = 1 + numFields * fieldLen
        val out = dbfFile.outputStream().buffered()
        val hdr = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        hdr.put(3)
        hdr.put(24); hdr.put(1); hdr.put(1)
        hdr.putInt(features.size)
        hdr.putShort(headerSize.toShort())
        hdr.putShort(recordSize.toShort())
        repeat(20) { hdr.put(0) }
        out.write(hdr.array())
        fields.forEach { fname ->
            val fd = ByteArray(32)
            val nameBytes = fname.toByteArray(Charsets.US_ASCII)
            System.arraycopy(nameBytes, 0, fd, 0, minOf(nameBytes.size, 10))
            fd[11] = 'C'.code.toByte()
            fd[16] = fieldLen.toByte()
            out.write(fd)
        }
        out.write(0x0D)
        features.forEach { f ->
            out.write(0x20)
            fields.forEach { fname ->
                val value = (f.attributes[fname] ?: if (fname == "NAME") f.name else "").take(fieldLen)
                val padded = value.padEnd(fieldLen, ' ')
                out.write(padded.toByteArray(Charsets.ISO_8859_1))
            }
        }
        out.write(0x1A)
        out.flush(); out.close()
    }
}
