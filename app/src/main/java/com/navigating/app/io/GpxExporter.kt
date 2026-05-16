package com.navigating.app.io

import com.navigating.app.data.Feature
import com.navigating.app.data.ShapeType
import java.io.File

object GpxExporter {
    fun export(features: List<Feature>, outputFile: File) {
        val gpx = buildGpx(features)
        outputFile.writeText(gpx, Charsets.UTF_8)
    }

    private fun buildGpx(features: List<Feature>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("""<gpx version="1.1" creator="Navigating-App" xmlns="http://www.topografix.com/GPX/1/1">""")
        features.filter { it.shapeType == ShapeType.POINT }.forEach { f ->
            val pt = f.coordinates.firstOrNull() ?: return@forEach
            sb.append("""<wpt lat="${pt[1]}" lon="${pt[0]}"><name>${escape(f.name)}</name></wpt>""")
        }
        features.filter { it.shapeType == ShapeType.POLYLINE }.forEach { f ->
            sb.append("<trk><name>${escape(f.name)}</name><trkseg>")
            f.coordinates.forEach { sb.append("""<trkpt lat="${it[1]}" lon="${it[0]}"/>""") }
            sb.append("</trkseg></trk>")
        }
        features.filter { it.shapeType == ShapeType.POLYGON }.forEach { f ->
            sb.append("<rte><name>${escape(f.name)}</name>")
            f.coordinates.forEach { sb.append("""<rtept lat="${it[1]}" lon="${it[0]}"/>""") }
            sb.append("</rte>")
        }
        sb.append("</gpx>")
        return sb.toString()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
