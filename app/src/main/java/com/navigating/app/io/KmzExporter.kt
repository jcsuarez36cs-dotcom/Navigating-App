package com.navigating.app.io

import com.navigating.app.data.Feature
import com.navigating.app.data.ShapeType
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object KmzExporter {
    fun export(features: List<Feature>, outputFile: File) {
        val kml = buildKml(features)
        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(kml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun buildKml(features: List<Feature>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>")
        sb.append("<name>Navigating-App Export</name>")
        features.forEach { f ->
            sb.append("<Placemark>")
            sb.append("<name>${escape(f.name)}</name>")
            if (f.attributes.isNotEmpty()) {
                sb.append("<ExtendedData>")
                f.attributes.forEach { (k, v) ->
                    sb.append("<Data name=\"${escape(k)}\"><value>${escape(v)}</value></Data>")
                }
                sb.append("</ExtendedData>")
            }
            when (f.shapeType) {
                ShapeType.POINT -> {
                    val pt = f.coordinates.firstOrNull() ?: return@forEach
                    sb.append("<Point><coordinates>${pt[0]},${pt[1]},0</coordinates></Point>")
                }
                ShapeType.POLYLINE -> {
                    sb.append("<LineString><coordinates>")
                    f.coordinates.forEach { sb.append("${it[0]},${it[1]},0 ") }
                    sb.append("</coordinates></LineString>")
                }
                ShapeType.POLYGON -> {
                    sb.append("<Polygon><outerBoundaryIs><LinearRing><coordinates>")
                    f.coordinates.forEach { sb.append("${it[0]},${it[1]},0 ") }
                    f.coordinates.firstOrNull()?.let { sb.append("${it[0]},${it[1]},0") }
                    sb.append("</coordinates></LinearRing></outerBoundaryIs></Polygon>")
                }
            }
            sb.append("</Placemark>")
        }
        sb.append("</Document></kml>")
        return sb.toString()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
