package com.example.methodmesh.modules.filelab

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object FileLabEngine {
    private const val PREFIX_LIMIT = 256 * 1024

    fun inspect(name: String, size: Long, open: () -> InputStream): FileInspection {
        val prefix = open().use { it.readNBytes(PREFIX_LIMIT) }
        val text = decodeTextPrefix(prefix)
        var match = FileSignatures.identify(prefix, name, text)
        val facts = mutableListOf<FileFact>()
        val warnings = mutableListOf<String>()
        val evidence = match.evidence.toMutableList()

        if (match.id == "zip") {
            val classified = SafeZipInspector.classify(open, nameHint = name)
            match = FileSignatures.Match(classified.id, classified.name, classified.mime, classified.confidence)
            facts += classified.facts
            warnings += classified.warnings
            evidence += DetectionEvidence("container", classified.reason)
        }

        val specialist = SpecialistInspectors.inspect(match.id, prefix, name)
        facts += specialist.first
        warnings += specialist.second
        extensionMismatch(name, match.id)?.let(warnings::add)

        val (sha, countedSize) = open().use(::sha256AndSize)
        val effectiveSize = if (size > 0) size else countedSize
        if (effectiveSize > prefix.size && match.id in setOf("csv", "tsv", "adif", "adx", "vcf", "fasta", "fastq", "nmea", "tle")) {
            warnings += "Structured counts are sampled from the first ${prefix.size} bytes, not the complete file."
        }
        val actions = buildList {
            add("share")
            add("hash.copy")
            if (ConversionGraph.from(match.id).isNotEmpty()) add("convert")
            if (match.id in setOf("epub", "mobi", "azw3", "pdf", "cbz", "cbr", "cb7", "cbt", "djvu")) add("read")
            if (match.id in setOf("zip", "cbz", "epub", "apk", "kmz", "docx", "xlsx", "xlsform", "pptx", "npz", "jar")) add("archive.inspect")
        }
        return FileInspection(
            displayName = name,
            formatId = match.id,
            formatName = match.name,
            mimeType = match.mime,
            confidence = match.confidence,
            sizeBytes = effectiveSize,
            sha256 = sha,
            facts = facts,
            warnings = warnings.distinct(),
            availableActions = actions,
            evidence = evidence.distinct(),
            sampledBytes = prefix.size,
            inspectionDepth = inspectionDepth(match.id),
            knowledge = FileFormatKnowledgeCatalogue.lookup(match.id, match.name)
        )
    }

    fun sha256(input: InputStream): String = sha256AndSize(input).first

    private fun sha256AndSize(input: InputStream): Pair<String, Long> {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            md.update(buffer, 0, count)
            size += count
        }
        return md.digest().joinToString("") { "%02x".format(it) } to size
    }

    private fun inspectionDepth(id: String): InspectionDepth = when (id) {
        "fits", "csv", "tsv", "adif", "adx", "vcf", "fasta", "fastq", "sigmf", "elf",
        "pe", "pcap", "pcapng", "wav", "npy", "dicom", "hdf5", "netcdf", "parquet" -> InspectionDepth.STRUCTURED
        "zip", "cbz", "epub", "apk", "docx", "xlsx", "xlsform", "pptx", "kmz", "npz", "jar" -> InspectionDepth.DEEP
        "pdf", "sqlite", "geopackage", "mbtiles", "mobi", "azw3", "gpx", "kml", "geojson", "rar", "cbr", "7z", "cb7",
        "tar", "cbt", "flac", "ogg", "mp3", "aac", "midi", "mp4", "m4a", "mov", "mkv", "webm",
        "png", "jpeg", "gif", "bmp", "webp", "tiff", "heic", "avif", "psd", "svg", "shp", "las",
        "bam", "cram", "bcf", "grib", "arrow", "orc", "stata", "spss", "sas7bdat", "gcode", "gerber",
        "stl", "obj", "ply", "gltf", "glb", "nmea", "tle", "cabrillo", "xform", "ics", "vcard", "eml", "rtf", "djvu",
        "nifti", "mrc", "czi", "mat", "rds", "rdata" -> InspectionDepth.BASIC
        else -> InspectionDepth.RECOGNISED
    }

    private fun extensionMismatch(name: String, detected: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return null
        val compatible = when (detected) {
            "jpeg" -> setOf("jpg", "jpeg")
            "tiff" -> setOf("tif", "tiff", "dng")
            "fits" -> setOf("fit", "fits", "fts")
            "sqlite" -> setOf("sqlite", "sqlite3", "db")
            "adif" -> setOf("adi", "adif")
            "adx" -> setOf("adx")
            "xlsform" -> setOf("xlsx")
            "mobi" -> setOf("mobi", "prc", "azw")
            "markdown" -> setOf("md", "markdown", "qmd", "rmd")
            "source" -> setOf("py", "r", "kt", "java", "c", "h", "cpp", "js", "ts", "sh")
            "ogg" -> setOf("ogg", "opus")
            "midi" -> setOf("mid", "midi")
            "heic" -> setOf("heic", "heif")
            "arrow" -> setOf("arrow", "feather")
            "fasta" -> setOf("fasta", "fa", "fna", "faa")
            "fastq" -> setOf("fastq", "fq")
            "sigmf" -> setOf("sigmf-meta")
            "iq" -> setOf("iq", "cfile")
            "gcode" -> setOf("gcode", "gco", "nc")
            "gerber" -> setOf("gbr", "gerber")
            "7z" -> setOf("7z")
            "cbr" -> setOf("cbr")
            "cb7" -> setOf("cb7")
            "cbt" -> setOf("cbt")
            "vcard" -> setOf("vcf")
            else -> setOf(detected)
        }
        return if (ext !in compatible && detected != "unknown") {
            "Filename extension .$ext does not match detected $detected content."
        } else null
    }

    private fun decodeTextPrefix(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
        if (bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        val nulRate = bytes.count { it == 0.toByte() }.toDouble() / bytes.size
        if (nulRate > 0.02) return null
        return runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
    }
}

internal object FileSignatures {
    data class Match(
        val id: String,
        val name: String,
        val mime: String?,
        val confidence: Int,
        val evidence: List<DetectionEvidence> = emptyList()
    )

    private data class ExtensionType(val id: String, val name: String, val mime: String? = null)

    /*
     * Extension fallback is intentionally broad but lower-confidence than content
     * signatures. It lets File Lab name specialist field/scientific formats that
     * have no stable short magic header while keeping the evidence honest.
     */
    private val extensions: Map<String, ExtensionType> = mapOf(
        // archives / comics
        "rar" to ExtensionType("rar", "RAR archive", "application/vnd.rar"),
        "cbr" to ExtensionType("cbr", "CBR comic archive (RAR)", "application/vnd.comicbook-rar"),
        "7z" to ExtensionType("7z", "7-Zip archive", "application/x-7z-compressed"),
        "cb7" to ExtensionType("cb7", "CB7 comic archive (7-Zip)", "application/x-cb7"),
        "tar" to ExtensionType("tar", "TAR archive", "application/x-tar"),
        "cbt" to ExtensionType("cbt", "CBT comic archive (TAR)", "application/x-cbt"),
        "zip" to ExtensionType("zip", "ZIP archive", "application/zip"),
        "cbz" to ExtensionType("cbz", "CBZ comic archive", "application/vnd.comicbook+zip"),
        "gz" to ExtensionType("gzip", "GZIP compressed data", "application/gzip"),
        "bz2" to ExtensionType("bzip2", "BZip2 compressed data", "application/x-bzip2"),
        "xz" to ExtensionType("xz", "XZ compressed data", "application/x-xz"),
        "zst" to ExtensionType("zstd", "Zstandard compressed data", "application/zstd"),
        "jar" to ExtensionType("jar", "Java archive", "application/java-archive"),
        // documents / ebooks
        "txt" to ExtensionType("txt", "Plain text", "text/plain"),
        "md" to ExtensionType("markdown", "Markdown text", "text/markdown"),
        "markdown" to ExtensionType("markdown", "Markdown text", "text/markdown"),
        "html" to ExtensionType("html", "HTML document", "text/html"),
        "htm" to ExtensionType("html", "HTML document", "text/html"),
        "xml" to ExtensionType("xml", "XML document", "application/xml"),
        "yaml" to ExtensionType("yaml", "YAML document", "application/yaml"),
        "yml" to ExtensionType("yaml", "YAML document", "application/yaml"),
        "toml" to ExtensionType("toml", "TOML document", "application/toml"),
        "ini" to ExtensionType("ini", "INI/configuration text", "text/plain"),
        "rtf" to ExtensionType("rtf", "Rich Text Format", "application/rtf"),
        "epub" to ExtensionType("epub", "EPUB ebook", "application/epub+zip"),
        "mobi" to ExtensionType("mobi", "Mobipocket ebook", "application/x-mobipocket-ebook"),
        "azw" to ExtensionType("mobi", "Kindle/Mobipocket ebook", "application/x-mobipocket-ebook"),
        "azw3" to ExtensionType("azw3", "Kindle AZW3 ebook", "application/vnd.amazon.ebook"),
        "djvu" to ExtensionType("djvu", "DjVu document", "image/vnd.djvu"),
        "djv" to ExtensionType("djvu", "DjVu document", "image/vnd.djvu"),
        "chm" to ExtensionType("chm", "Compiled HTML Help", "application/vnd.ms-htmlhelp"),
        "doc" to ExtensionType("ole", "Microsoft Compound Document", "application/msword"),
        "xls" to ExtensionType("ole", "Microsoft Compound Spreadsheet", "application/vnd.ms-excel"),
        "ppt" to ExtensionType("ole", "Microsoft Compound Presentation", "application/vnd.ms-powerpoint"),
        // raster / vector images
        "jpg" to ExtensionType("jpeg", "JPEG image", "image/jpeg"),
        "jpeg" to ExtensionType("jpeg", "JPEG image", "image/jpeg"),
        "png" to ExtensionType("png", "PNG image", "image/png"),
        "gif" to ExtensionType("gif", "GIF image", "image/gif"),
        "bmp" to ExtensionType("bmp", "Bitmap image", "image/bmp"),
        "webp" to ExtensionType("webp", "WebP image", "image/webp"),
        "tif" to ExtensionType("tiff", "TIFF image", "image/tiff"),
        "tiff" to ExtensionType("tiff", "TIFF image", "image/tiff"),
        "heic" to ExtensionType("heic", "HEIC image", "image/heic"),
        "heif" to ExtensionType("heic", "HEIF image", "image/heif"),
        "avif" to ExtensionType("avif", "AVIF image", "image/avif"),
        "svg" to ExtensionType("svg", "SVG vector image", "image/svg+xml"),
        "psd" to ExtensionType("psd", "Adobe Photoshop document", "image/vnd.adobe.photoshop"),
        "ico" to ExtensionType("ico", "Windows icon", "image/x-icon"),
        "dng" to ExtensionType("tiff", "Digital Negative / TIFF image", "image/x-adobe-dng"),
        // audio/video/media containers
        "wav" to ExtensionType("wav", "WAVE audio", "audio/wav"),
        "flac" to ExtensionType("flac", "FLAC audio", "audio/flac"),
        "ogg" to ExtensionType("ogg", "Ogg media", "audio/ogg"),
        "opus" to ExtensionType("ogg", "Ogg/Opus audio", "audio/ogg"),
        "mp3" to ExtensionType("mp3", "MP3 audio", "audio/mpeg"),
        "aac" to ExtensionType("aac", "AAC audio", "audio/aac"),
        "m4a" to ExtensionType("m4a", "MPEG-4 audio", "audio/mp4"),
        "mid" to ExtensionType("midi", "MIDI sequence", "audio/midi"),
        "midi" to ExtensionType("midi", "MIDI sequence", "audio/midi"),
        "mp4" to ExtensionType("mp4", "MPEG-4 media", "video/mp4"),
        "mov" to ExtensionType("mov", "QuickTime movie", "video/quicktime"),
        "mkv" to ExtensionType("mkv", "Matroska media", "video/x-matroska"),
        "webm" to ExtensionType("webm", "WebM media", "video/webm"),
        "avi" to ExtensionType("avi", "AVI video", "video/x-msvideo"),
        "flv" to ExtensionType("flv", "Flash video", "video/x-flv"),
        // astronomy / science / research data
        "fit" to ExtensionType("fits", "FITS astronomical data", "application/fits"),
        "fits" to ExtensionType("fits", "FITS astronomical data", "application/fits"),
        "fts" to ExtensionType("fits", "FITS astronomical data", "application/fits"),
        "xisf" to ExtensionType("xisf", "XISF astronomical image", "application/octet-stream"),
        "h5" to ExtensionType("hdf5", "HDF5 scientific data", "application/x-hdf5"),
        "hdf5" to ExtensionType("hdf5", "HDF5 scientific data", "application/x-hdf5"),
        "nc" to ExtensionType("netcdf", "NetCDF scientific data", "application/x-netcdf"),
        "grib" to ExtensionType("grib", "GRIB weather data", "application/x-grib"),
        "grb" to ExtensionType("grib", "GRIB weather data", "application/x-grib"),
        "grib2" to ExtensionType("grib", "GRIB2 weather data", "application/x-grib"),
        "parquet" to ExtensionType("parquet", "Apache Parquet data", "application/vnd.apache.parquet"),
        "arrow" to ExtensionType("arrow", "Apache Arrow IPC data", "application/vnd.apache.arrow.file"),
        "feather" to ExtensionType("arrow", "Apache Arrow/Feather data", "application/vnd.apache.arrow.file"),
        "orc" to ExtensionType("orc", "Apache ORC data", "application/octet-stream"),
        "npy" to ExtensionType("npy", "NumPy array", "application/octet-stream"),
        "npz" to ExtensionType("npz", "NumPy ZIP archive", "application/zip"),
        "dcm" to ExtensionType("dicom", "DICOM medical image/data", "application/dicom"),
        "edf" to ExtensionType("edf", "European Data Format biosignal", "application/octet-stream"),
        "bdf" to ExtensionType("edf", "BioSemi Data Format biosignal", "application/octet-stream"),
        "dta" to ExtensionType("stata", "Stata dataset", "application/x-stata"),
        "sav" to ExtensionType("spss", "SPSS system file", "application/x-spss-sav"),
        "zsav" to ExtensionType("spss", "SPSS compressed system file", "application/x-spss-sav"),
        "sas7bdat" to ExtensionType("sas7bdat", "SAS dataset", "application/x-sas-data"),
        "rds" to ExtensionType("rds", "R serialized object", "application/octet-stream"),
        "rdata" to ExtensionType("rdata", "R workspace", "application/octet-stream"),
        "mat" to ExtensionType("mat", "MATLAB data file", "application/octet-stream"),
        "nii" to ExtensionType("nifti", "NIfTI neuroimaging data", "application/octet-stream"),
        "mrc" to ExtensionType("mrc", "MRC electron-microscopy volume", "application/octet-stream"),
        "czi" to ExtensionType("czi", "Zeiss CZI microscopy image", "application/octet-stream"),
        // genomics / bioinformatics
        "fasta" to ExtensionType("fasta", "FASTA sequence data", "text/plain"),
        "fa" to ExtensionType("fasta", "FASTA sequence data", "text/plain"),
        "fna" to ExtensionType("fasta", "FASTA nucleotide sequence data", "text/plain"),
        "faa" to ExtensionType("fasta", "FASTA protein sequence data", "text/plain"),
        "fastq" to ExtensionType("fastq", "FASTQ sequence data", "text/plain"),
        "fq" to ExtensionType("fastq", "FASTQ sequence data", "text/plain"),
        "bam" to ExtensionType("bam", "BAM alignment data", "application/x-bam"),
        "cram" to ExtensionType("cram", "CRAM alignment data", "application/x-cram"),
        "bcf" to ExtensionType("bcf", "BCF variant data", "application/octet-stream"),
        // radio / navigation / geospatial
        "adi" to ExtensionType("adif", "ADIF amateur-radio log", "text/plain"),
        "adif" to ExtensionType("adif", "ADIF amateur-radio log", "text/plain"),
        "adx" to ExtensionType("adx", "ADIF XML amateur-radio log", "application/xml"),
        "sigmf-meta" to ExtensionType("sigmf", "SigMF metadata", "application/json"),
        "sigmf-data" to ExtensionType("sigmf-data", "SigMF sample data", "application/octet-stream"),
        "iq" to ExtensionType("iq", "Raw I/Q sample data", "application/octet-stream"),
        "cfile" to ExtensionType("iq", "Complex I/Q sample data", "application/octet-stream"),
        "tle" to ExtensionType("tle", "Two-line element set", "text/plain"),
        "nmea" to ExtensionType("nmea", "NMEA navigation log", "text/plain"),
        "gpx" to ExtensionType("gpx", "GPX track/waypoint data", "application/gpx+xml"),
        "kml" to ExtensionType("kml", "KML geospatial data", "application/vnd.google-earth.kml+xml"),
        "kmz" to ExtensionType("kmz", "KMZ geospatial archive", "application/vnd.google-earth.kmz"),
        "geojson" to ExtensionType("geojson", "GeoJSON geospatial data", "application/geo+json"),
        "gpkg" to ExtensionType("geopackage", "OGC GeoPackage", "application/geopackage+sqlite3"),
        "mbtiles" to ExtensionType("mbtiles", "MBTiles SQLite tileset", "application/x-sqlite3"),
        "shp" to ExtensionType("shp", "ESRI Shapefile geometry", "application/x-shapefile"),
        "shx" to ExtensionType("shx", "ESRI Shapefile index", "application/x-shapefile"),
        "dbf" to ExtensionType("dbf", "dBASE table / Shapefile attributes", "application/x-dbf"),
        "prj" to ExtensionType("prj", "Coordinate reference system text", "text/plain"),
        "las" to ExtensionType("las", "LAS lidar point cloud", "application/vnd.las"),
        "laz" to ExtensionType("laz", "LAZ compressed lidar point cloud", "application/vnd.laszip"),
        // network captures / executables / firmware
        "pcap" to ExtensionType("pcap", "Packet capture", "application/vnd.tcpdump.pcap"),
        "pcapng" to ExtensionType("pcapng", "PCAP Next Generation capture", "application/x-pcapng"),
        "so" to ExtensionType("elf", "ELF shared library", "application/x-elf"),
        "elf" to ExtensionType("elf", "ELF binary", "application/x-elf"),
        "exe" to ExtensionType("pe", "Windows PE executable", "application/vnd.microsoft.portable-executable"),
        "dll" to ExtensionType("pe", "Windows PE library", "application/vnd.microsoft.portable-executable"),
        "dex" to ExtensionType("dex", "Android DEX bytecode", "application/vnd.android.dex"),
        "wasm" to ExtensionType("wasm", "WebAssembly binary", "application/wasm"),
        "class" to ExtensionType("java-class", "Java class file", "application/java-vm"),
        "hex" to ExtensionType("intel-hex", "Intel HEX firmware", "text/plain"),
        "uf2" to ExtensionType("uf2", "UF2 firmware image", "application/octet-stream"),
        "bin" to ExtensionType("binary", "Generic binary image", "application/octet-stream"),
        // engineering / fabrication / 3D
        "gcode" to ExtensionType("gcode", "G-code toolpath", "text/plain"),
        "gco" to ExtensionType("gcode", "G-code toolpath", "text/plain"),
        "gbr" to ExtensionType("gerber", "Gerber PCB layer", "text/plain"),
        "gerber" to ExtensionType("gerber", "Gerber PCB layer", "text/plain"),
        "drl" to ExtensionType("excellon", "Excellon drill file", "text/plain"),
        "stl" to ExtensionType("stl", "STL 3D mesh", "model/stl"),
        "obj" to ExtensionType("obj", "Wavefront OBJ 3D model", "model/obj"),
        "ply" to ExtensionType("ply", "PLY polygon model", "application/octet-stream"),
        "gltf" to ExtensionType("gltf", "glTF 3D model", "model/gltf+json"),
        "glb" to ExtensionType("glb", "Binary glTF 3D model", "model/gltf-binary"),
        "dxf" to ExtensionType("dxf", "AutoCAD DXF drawing", "image/vnd.dxf"),
        // common structured text / interchange
        "csv" to ExtensionType("csv", "CSV tabular data", "text/csv"),
        "tsv" to ExtensionType("tsv", "TSV tabular data", "text/tab-separated-values"),
        "json" to ExtensionType("json", "JSON", "application/json"),
        "ndjson" to ExtensionType("ndjson", "Newline-delimited JSON", "application/x-ndjson"),
        "vcf" to ExtensionType("vcf", "Variant Call Format or vCard", "text/plain"),
        "ics" to ExtensionType("ics", "iCalendar data", "text/calendar"),
        "eml" to ExtensionType("eml", "RFC 822 email message", "message/rfc822"),
        "log" to ExtensionType("log", "Log text", "text/plain"),
        "sql" to ExtensionType("sql", "SQL script", "application/sql"),
        // source code: useful for inspection even when no conversion is appropriate
        "py" to ExtensionType("source", "Python source code", "text/x-python"),
        "r" to ExtensionType("source", "R source code", "text/plain"),
        "qmd" to ExtensionType("markdown", "Quarto Markdown document", "text/markdown"),
        "rmd" to ExtensionType("markdown", "R Markdown document", "text/markdown"),
        "kt" to ExtensionType("source", "Kotlin source code", "text/plain"),
        "java" to ExtensionType("source", "Java source code", "text/x-java-source"),
        "c" to ExtensionType("source", "C source code", "text/plain"),
        "h" to ExtensionType("source", "C/C++ header", "text/plain"),
        "cpp" to ExtensionType("source", "C++ source code", "text/plain"),
        "js" to ExtensionType("source", "JavaScript source code", "text/javascript"),
        "ts" to ExtensionType("source", "TypeScript source code", "text/plain"),
        "sh" to ExtensionType("source", "Shell script", "text/x-shellscript")
    )

    fun identify(prefix: ByteArray, name: String, textPrefix: String?): Match {
        fun starts(vararg b: Int) = prefix.size >= b.size && b.indices.all { prefix[it].toInt() and 0xff == b[it] }
        fun asciiAt(offset: Int, value: String): Boolean = prefix.size >= offset + value.length &&
            runCatching { String(prefix, offset, value.length, StandardCharsets.US_ASCII) == value }.getOrDefault(false)
        fun m(id: String, n: String, mime: String?, c: Int, why: String) =
            Match(id, n, mime, c, listOf(DetectionEvidence("content", why)))
        val lower = name.lowercase()
        val ext = extensionOf(lower)
        val trimmed = textPrefix?.trimStart().orEmpty()
        val bookMobi = prefix.size >= 68 && asciiAt(60, "BOOKMOBI")
        val bmffBrand = if (prefix.size >= 12 && asciiAt(4, "ftyp")) runCatching { String(prefix, 8, 4, StandardCharsets.US_ASCII) }.getOrDefault("") else ""
        val rar = starts(0x52,0x61,0x72,0x21,0x1a,0x07,0x00) || starts(0x52,0x61,0x72,0x21,0x1a,0x07,0x01,0x00)
        val sevenZip = starts(0x37,0x7a,0xbc,0xaf,0x27,0x1c)
        val tar = asciiAt(257, "ustar")
        val iso = asciiAt(32769, "CD001")
        val textual = isProbablyText(prefix, textPrefix)
        return when {
            starts(0x25, 0x50, 0x44, 0x46) -> m("pdf", "PDF", "application/pdf", 100, "PDF magic bytes")
            rar -> m(if (ext == "cbr") "cbr" else "rar", if (ext == "cbr") "CBR comic archive (RAR)" else "RAR archive", if (ext == "cbr") "application/vnd.comicbook-rar" else "application/vnd.rar", 100, "RAR signature")
            sevenZip -> m(if (ext == "cb7") "cb7" else "7z", if (ext == "cb7") "CB7 comic archive (7-Zip)" else "7-Zip archive", if (ext == "cb7") "application/x-cb7" else "application/x-7z-compressed", 100, "7-Zip signature")
            tar -> m(if (ext == "cbt") "cbt" else "tar", if (ext == "cbt") "CBT comic archive (TAR)" else "TAR archive", if (ext == "cbt") "application/x-cbt" else "application/x-tar", 98, "ustar archive header")
            starts(0x50, 0x4b, 0x03, 0x04) || starts(0x50, 0x4b, 0x05, 0x06) || starts(0x50, 0x4b, 0x07, 0x08) -> m("zip", "ZIP-family container", "application/zip", 98, "ZIP signature")
            starts(0x1f, 0x8b) -> m("gzip", "GZIP compressed data", "application/gzip", 100, "GZIP signature")
            starts(0x42, 0x5a, 0x68) -> m("bzip2", "BZip2 compressed data", "application/x-bzip2", 100, "BZip2 signature")
            starts(0xfd, 0x37, 0x7a, 0x58, 0x5a, 0x00) -> m("xz", "XZ compressed data", "application/x-xz", 100, "XZ signature")
            starts(0x28, 0xb5, 0x2f, 0xfd) -> m("zstd", "Zstandard compressed data", "application/zstd", 100, "Zstandard frame signature")
            starts(0x53,0x51,0x4c,0x69,0x74,0x65,0x20,0x66,0x6f,0x72,0x6d,0x61,0x74,0x20,0x33,0x00) -> when (ext) {
                "gpkg" -> m("geopackage", "OGC GeoPackage", "application/geopackage+sqlite3", 100, "SQLite header plus .gpkg extension")
                "mbtiles" -> m("mbtiles", "MBTiles SQLite tileset", "application/x-sqlite3", 100, "SQLite header plus .mbtiles extension")
                else -> m("sqlite", "SQLite 3 database", "application/vnd.sqlite3", 100, "SQLite header")
            }
            starts(0x7f, 0x45, 0x4c, 0x46) -> m("elf", "ELF binary", "application/x-elf", 100, "ELF header")
            starts(0x4d, 0x5a) -> m("pe", "DOS/Windows PE-family binary", "application/vnd.microsoft.portable-executable", 95, "MZ executable header")
            starts(0xca,0xfe,0xba,0xbe) -> m("java-class", "Java class file", "application/java-vm", 100, "CAFEBABE class signature")
            starts(0x64,0x65,0x78,0x0a) -> m("dex", "Android DEX bytecode", "application/vnd.android.dex", 100, "DEX header")
            starts(0x00,0x61,0x73,0x6d) -> m("wasm", "WebAssembly binary", "application/wasm", 100, "WebAssembly magic")
            starts(0xfe,0xed,0xfa,0xce) || starts(0xce,0xfa,0xed,0xfe) || starts(0xfe,0xed,0xfa,0xcf) || starts(0xcf,0xfa,0xed,0xfe) -> m("macho", "Mach-O executable", "application/x-mach-binary", 100, "Mach-O magic")
            starts(0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) -> m("png", "PNG image", "image/png", 100, "PNG signature")
            starts(0xff,0xd8,0xff) -> m("jpeg", "JPEG image", "image/jpeg", 100, "JPEG signature")
            starts(0x47,0x49,0x46,0x38) -> m("gif", "GIF image", "image/gif", 100, "GIF87a/89a signature")
            starts(0x42,0x4d) -> m("bmp", "Bitmap image", "image/bmp", 100, "BM bitmap signature")
            starts(0x52,0x49,0x46,0x46) && asciiAt(8, "WEBP") -> m("webp", "WebP image", "image/webp", 100, "RIFF/WEBP signature")
            starts(0x49,0x49,0x2a,0x00) || starts(0x4d,0x4d,0x00,0x2a) -> m("tiff", if (ext == "dng") "Digital Negative / TIFF image" else "TIFF / possible GeoTIFF image", if (ext == "dng") "image/x-adobe-dng" else "image/tiff", 100, "TIFF byte-order/magic")
            starts(0x38,0x42,0x50,0x53) -> m("psd", "Adobe Photoshop document", "image/vnd.adobe.photoshop", 100, "8BPS signature")
            prefix.size >= 132 && asciiAt(128, "DICM") -> m("dicom", "DICOM medical image/data", "application/dicom", 100, "DICOM preamble marker")
            starts(0x53,0x49,0x4d,0x50,0x4c,0x45,0x20,0x20,0x3d) -> m("fits", "FITS astronomical data", "application/fits", 100, "FITS SIMPLE card")
            starts(0x89,0x48,0x44,0x46,0x0d,0x0a,0x1a,0x0a) -> m("hdf5", "HDF5 scientific data", "application/x-hdf5", 100, "HDF5 signature")
            starts(0x43,0x44,0x46,0x01) || starts(0x43,0x44,0x46,0x02) || starts(0x43,0x44,0x46,0x05) -> m("netcdf", "NetCDF scientific data", "application/x-netcdf", 100, "NetCDF CDF header")
            starts(0x47,0x52,0x49,0x42) -> m("grib", "GRIB weather data", "application/x-grib", 100, "GRIB header")
            starts(0x50,0x41,0x52,0x31) -> m("parquet", "Apache Parquet data", "application/vnd.apache.parquet", 100, "PAR1 header")
            starts(0x41,0x52,0x52,0x4f,0x57,0x31) -> m("arrow", "Apache Arrow IPC data", "application/vnd.apache.arrow.file", 100, "ARROW1 header")
            starts(0x93,0x4e,0x55,0x4d,0x50,0x59) -> m("npy", "NumPy array", "application/octet-stream", 100, "NumPy NPY signature")
            prefix.size >= 348 && (asciiAt(344, "n+1") || asciiAt(344, "ni1")) -> m("nifti", "NIfTI neuroimaging data", "application/octet-stream", 100, "NIfTI magic at byte 344")
            starts(0x4c,0x41,0x53,0x46) -> m("las", if (ext == "laz") "LAZ/LAS lidar point cloud" else "LAS lidar point cloud", if (ext == "laz") "application/vnd.laszip" else "application/vnd.las", 100, "LASF header")
            starts(0x42,0x41,0x4d,0x01) -> m("bam", "BAM alignment data", "application/x-bam", 100, "BAM header")
            starts(0x43,0x52,0x41,0x4d) -> m("cram", "CRAM alignment data", "application/x-cram", 100, "CRAM header")
            starts(0x42,0x43,0x46) -> m("bcf", "BCF variant data", "application/octet-stream", 100, "BCF header")
            starts(0x0a,0x0d,0x0d,0x0a) -> m("pcapng", "PCAP Next Generation capture", "application/x-pcapng", 100, "pcapng section header")
            starts(0xd4,0xc3,0xb2,0xa1) || starts(0xa1,0xb2,0xc3,0xd4) || starts(0x4d,0x3c,0xb2,0xa1) || starts(0xa1,0xb2,0x3c,0x4d) -> m("pcap", "Packet capture", "application/vnd.tcpdump.pcap", 100, "pcap magic")
            starts(0x52,0x49,0x46,0x46) && asciiAt(8, "WAVE") -> m("wav", "WAVE audio", "audio/wav", 100, "RIFF/WAVE signature")
            starts(0x66,0x4c,0x61,0x43) -> m("flac", "FLAC audio", "audio/flac", 100, "fLaC signature")
            starts(0x4f,0x67,0x67,0x53) -> m("ogg", "Ogg media", "application/ogg", 100, "OggS signature")
            prefix.size >= 2 && (prefix[0].toInt() and 0xff) == 0xff && ((prefix[1].toInt() and 0xf6) == 0xf0) -> m("aac", "AAC ADTS audio", "audio/aac", 95, "AAC ADTS sync word")
            starts(0x49,0x44,0x33) || (prefix.size >= 2 && (prefix[0].toInt() and 0xff) == 0xff && ((prefix[1].toInt() and 0xe0) == 0xe0)) -> m("mp3", "MP3 audio", "audio/mpeg", 95, "ID3 or MPEG audio frame header")
            starts(0x4d,0x54,0x68,0x64) -> m("midi", "MIDI sequence", "audio/midi", 100, "MThd signature")
            starts(0x1a,0x45,0xdf,0xa3) -> m(if (ext == "webm") "webm" else "mkv", if (ext == "webm") "WebM media" else "Matroska media", if (ext == "webm") "video/webm" else "video/x-matroska", 100, "EBML container header")
            starts(0x52,0x49,0x46,0x46) && asciiAt(8, "AVI ") -> m("avi", "AVI video", "video/x-msvideo", 100, "RIFF/AVI signature")
            starts(0x46,0x4c,0x56) -> m("flv", "Flash video", "video/x-flv", 100, "FLV signature")
            bmffBrand.isNotBlank() -> bmff(bmffBrand, ext)
            bookMobi -> m(if (ext == "azw3") "azw3" else "mobi", "Mobipocket/Kindle ebook", "application/x-mobipocket-ebook", 98, "BOOKMOBI header")
            starts(0x41,0x54,0x26,0x54,0x46,0x4f,0x52,0x4d) -> m("djvu", "DjVu document", "image/vnd.djvu", 98, "DjVu AT&TFORM header")
            starts(0x49,0x54,0x53,0x46) -> m("chm", "Compiled HTML Help", "application/vnd.ms-htmlhelp", 100, "ITSF header")
            starts(0xd0,0xcf,0x11,0xe0,0xa1,0xb1,0x1a,0xe1) -> m("ole", "OLE Compound Document", "application/x-ole-storage", 100, "Compound File Binary header")
            iso -> m("iso9660", "ISO 9660 disc image", "application/x-iso9660-image", 100, "ISO 9660 CD001 descriptor")
            starts(0x67,0x6c,0x54,0x46) -> m("glb", "Binary glTF 3D model", "model/gltf-binary", 100, "glTF binary header")
            starts(0x0a,0x32,0x46,0x55,0x57,0x51,0x5d,0x9e) -> m("uf2", "UF2 firmware image", "application/octet-stream", 95, "UF2 block header")
            trimmed.startsWith("##fileformat=VCF") -> m("vcf", "Variant Call Format", "text/vcf", 100, "VCF fileformat header")
            trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) -> m("vcard", "vCard contact data", "text/vcard", 100, "VCARD marker")
            trimmed.startsWith("BEGIN:VCALENDAR", ignoreCase = true) -> m("ics", "iCalendar data", "text/calendar", 100, "VCALENDAR marker")
            trimmed.contains("START-OF-LOG:", ignoreCase = true) && trimmed.contains("CALLSIGN:", ignoreCase = true) -> m("cabrillo", "Cabrillo amateur-radio contest log", "text/plain", 98, "Cabrillo log headers")
            trimmed.startsWith("ply") && trimmed.lineSequence().firstOrNull()?.trim() == "ply" -> m("ply", "PLY polygon model", "application/octet-stream", 95, "PLY text header")
            trimmed.startsWith("solid ") && ext == "stl" -> Match("stl", "STL 3D mesh", "model/stl", 85, listOf(DetectionEvidence("extension+content", "ASCII STL solid header")))
            trimmed.startsWith(":") && Regex("^:[0-9A-Fa-f]{8,}").containsMatchIn(trimmed.lineSequence().firstOrNull().orEmpty()) -> m("intel-hex", "Intel HEX firmware", "text/plain", 95, "Intel HEX record")
            trimmed.startsWith("\$GP") || trimmed.startsWith("\$GN") || trimmed.startsWith("\$GL") -> m("nmea", "NMEA navigation log", "text/plain", 95, "NMEA sentence prefix")
            looksLikeTle(trimmed) -> m("tle", "Two-line element set", "text/plain", 90, "TLE line-number/catalogue structure")
            trimmed.startsWith("<?xml") && (trimmed.contains("xmlns:jr=", ignoreCase = true) || trimmed.contains("http://openrosa.org/xforms", ignoreCase = true)) -> m("xform", "ODK/OpenRosa XForm", "application/xml", 97, "OpenRosa/XForms namespace")
            trimmed.startsWith("<?xml") && Regex("<gpx(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> m("gpx", "GPX track/waypoint data", "application/gpx+xml", 97, "GPX XML root")
            trimmed.startsWith("<?xml") && Regex("<kml(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> m("kml", "KML geospatial data", "application/vnd.google-earth.kml+xml", 97, "KML XML root")
            trimmed.startsWith("<svg", ignoreCase = true) || (trimmed.startsWith("<?xml") && trimmed.contains("<svg", ignoreCase = true)) -> m("svg", "SVG vector image", "image/svg+xml", 97, "SVG XML root")
            trimmed.contains("<ADIF_VER:", ignoreCase = true) || trimmed.contains("<CALL:", ignoreCase = true) -> m("adif", "ADIF amateur-radio log", "text/plain", 95, "ADIF field tags")
            trimmed.startsWith("<?xml") && (trimmed.contains("<ADX", ignoreCase = true) || trimmed.contains("<RECORDS", ignoreCase = true)) -> m("adx", "ADIF XML amateur-radio log", "application/xml", 90, "ADX XML structure")
            trimmed.startsWith(">") -> m("fasta", "FASTA sequence data", "text/plain", 85, "FASTA record marker")
            (ext == "fastq" || ext == "fq") && trimmed.startsWith("@") -> Match("fastq", "FASTQ sequence data", "text/plain", 85, listOf(DetectionEvidence("extension+content", "FASTQ extension plus @ record marker")))
            lower.endsWith(".sigmf-meta") && trimmed.startsWith("{") -> Match("sigmf", "SigMF metadata", "application/json", 95, listOf(DetectionEvidence("extension+content", ".sigmf-meta JSON")))
            looksLikeGerber(trimmed) -> m("gerber", "Gerber PCB layer", "text/plain", 90, "Gerber RS-274X commands")
            looksLikeGcode(trimmed, ext) -> Match("gcode", "G-code toolpath", "text/plain", 85, listOf(DetectionEvidence("content", "G/M tool commands")))
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                val geo = if (trimmed.contains("\"type\"") && (trimmed.contains("FeatureCollection") || trimmed.contains("Feature\""))) "geojson" else "json"
                m(geo, if (geo == "geojson") "GeoJSON geospatial data" else "JSON", if (geo == "geojson") "application/geo+json" else "application/json", 78, "JSON-like leading token")
            }
            trimmed.startsWith("<?xml") -> m("xml", "XML document", "application/xml", 78, "XML declaration")
            trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true) -> m("html", "HTML document", "text/html", 85, "HTML root/doctype")
            trimmed.startsWith("{\\rtf") -> m("rtf", "Rich Text Format", "application/rtf", 95, "RTF control header")
            extensionMatch(ext) != null -> extensionMatch(ext)!!
            textual -> m("txt", "Plain text", "text/plain", 55, "Predominantly printable text with no more specific recognised structure")
            else -> Match("unknown", "Unknown / generic file", null, 20, listOf(DetectionEvidence("fallback", "No recognised signature or extension")))
        }
    }

    private fun extensionMatch(ext: String): Match? {
        val type = extensions[ext] ?: return null
        return Match(type.id, type.name, type.mime, 62, listOf(DetectionEvidence("extension", ".$ext filename extension")))
    }

    private fun extensionOf(lowerName: String): String {
        if (lowerName.endsWith(".sigmf-meta")) return "sigmf-meta"
        if (lowerName.endsWith(".sigmf-data")) return "sigmf-data"
        return lowerName.substringAfterLast('.', "")
    }

    private fun bmff(brand: String, ext: String): Match {
        val lowerBrand = brand.lowercase()
        return when {
            lowerBrand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1") || ext in setOf("heic", "heif") -> Match("heic", "HEIF/HEIC image", "image/heic", 95, listOf(DetectionEvidence("content", "ISO-BMFF ftyp brand $brand")))
            lowerBrand in setOf("avif", "avis") || ext == "avif" -> Match("avif", "AVIF image", "image/avif", 95, listOf(DetectionEvidence("content", "ISO-BMFF ftyp brand $brand")))
            ext == "m4a" -> Match("m4a", "MPEG-4 audio", "audio/mp4", 90, listOf(DetectionEvidence("content+extension", "ISO-BMFF ftyp brand $brand")))
            ext == "mov" || lowerBrand == "qt  " -> Match("mov", "QuickTime movie", "video/quicktime", 95, listOf(DetectionEvidence("content", "ISO-BMFF/QuickTime ftyp brand $brand")))
            else -> Match("mp4", "ISO Base Media / MPEG-4 container", "video/mp4", 92, listOf(DetectionEvidence("content", "ISO-BMFF ftyp brand $brand")))
        }
    }

    private fun looksLikeTle(text: String): Boolean {
        val lines = text.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.take(3).toList()
        val offset = if (lines.size >= 3 && !lines[0].startsWith("1 ")) 1 else 0
        return lines.size >= offset + 2 && lines[offset].startsWith("1 ") && lines[offset + 1].startsWith("2 ") &&
            lines[offset].length >= 50 && lines[offset + 1].length >= 50
    }

    private fun looksLikeGerber(text: String): Boolean =
        text.contains("%FS", ignoreCase = true) && (text.contains("%MO", ignoreCase = true) || text.contains("D01*"))

    private fun looksLikeGcode(text: String, ext: String): Boolean {
        if (ext in setOf("gcode", "gco")) return true
        val commands = text.lineSequence().take(80).count { line ->
            val t = line.trimStart()
            Regex("^(G|M)\\d+(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(t)
        }
        return commands >= 3
    }

    private fun isProbablyText(prefix: ByteArray, text: String?): Boolean {
        if (text == null || prefix.isEmpty()) return false
        var controls = 0
        var printable = 0
        prefix.take(64 * 1024).forEach { byte ->
            val v = byte.toInt() and 0xff
            when {
                v == 9 || v == 10 || v == 13 -> printable++
                v in 32..126 || v >= 0x80 -> printable++
                else -> controls++
            }
        }
        return printable > 0 && controls.toDouble() / (printable + controls).coerceAtLeast(1) < 0.02
    }
}

internal object SafeZipInspector {
    data class Classification(
        val id: String,
        val name: String,
        val mime: String?,
        val confidence: Int,
        val facts: List<FileFact>,
        val warnings: List<String>,
        val reason: String
    )

    private const val ENTRY_LIMIT = 10_000
    private const val DECLARED_LIMIT = 4L * 1024 * 1024 * 1024
    private const val NESTED_ENTRY_LIMIT = 50
    private const val NESTED_READ_LIMIT = 4 * 1024 * 1024
    private const val TOTAL_NESTED_READ_LIMIT = 16 * 1024 * 1024

    fun classify(open: () -> InputStream, nameHint: String? = null): Classification =
        classify(open, depth = 0, nameHint = nameHint)

    private fun classify(open: () -> InputStream, depth: Int, nameHint: String? = null): Classification {
        val names = mutableListOf<String>()
        val nestedSummaries = mutableListOf<String>()
        var declared = 0L
        var entriesSeen = 0
        var nestedRead = 0
        var encryptionMetadata = false
        var epubMime: String? = null
        var workbookXml: String? = null

        open().use { raw ->
            ZipInputStream(raw).use { zip ->
                while (entriesSeen < ENTRY_LIMIT) {
                    val entry = zip.nextEntry ?: break
                    entriesSeen++
                    if (names.size < ENTRY_LIMIT) names += entry.name
                    if (entry.size > 0) declared = if (entry.size > Long.MAX_VALUE - declared) Long.MAX_VALUE else declared + entry.size
                    val lower = entry.name.lowercase()
                    if (lower == "meta-inf/encryption.xml") encryptionMetadata = true
                    if (!entry.isDirectory && lower == "mimetype") {
                        epubMime = readEntryBounded(zip, 128).toString(StandardCharsets.US_ASCII).trim()
                        continue
                    }
                    if (!entry.isDirectory && lower == "xl/workbook.xml") {
                        workbookXml = readEntryBounded(zip, 256 * 1024).toString(StandardCharsets.UTF_8)
                        continue
                    }
                    if (depth < 2 && !entry.isDirectory && nestedSummaries.size < NESTED_ENTRY_LIMIT && nestedRead < TOTAL_NESTED_READ_LIMIT &&
                        lower.matches(Regex(".*\\.(zip|cbz|epub|apk|kmz|docx|xlsx|pptx)$")) &&
                        (entry.size in 1..NESTED_READ_LIMIT.toLong() || entry.size < 0)
                    ) {
                        val remaining = (TOTAL_NESTED_READ_LIMIT - nestedRead).coerceAtMost(NESTED_READ_LIMIT)
                        val bytes = readEntryBounded(zip, remaining)
                        nestedRead += bytes.size
                        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
                            val nested = runCatching { classify({ ByteArrayInputStream(bytes) }, depth + 1, entry.name) }.getOrNull()
                            nested?.let { nestedSummaries += "${entry.name}: ${it.id}" }
                        }
                    }
                }
            }
        }

        val lower = names.map(String::lowercase)
        val files = names.filterNot { it.endsWith("/") }
        val ignorableComicMetadata = setOf(
            "comicinfo.xml", "thumbs.db", ".ds_store"
        )
        val comicPayloadFiles = files.filter { file ->
            val leaf = file.substringAfterLast('/').lowercase()
            leaf !in ignorableComicMetadata && !leaf.startsWith("__macosx")
        }
        val imageFiles = comicPayloadFiles.count { it.lowercase().matches(Regex(".*\\.(jpg|jpeg|png|webp|gif)$")) }
        val cbzExtensionHint = nameHint?.substringAfterLast('.', "")?.equals("cbz", ignoreCase = true) == true
        val looksLikeComicArchive = comicPayloadFiles.isNotEmpty() &&
            imageFiles.toDouble() / comicPayloadFiles.size >= 0.80 &&
            (imageFiles >= 2 || (cbzExtensionHint && imageFiles >= 1))
        val workbookSheets = workbookXml.orEmpty().let { xml ->
            Regex("<sheet[^>]+name=\"([^\"]+)\"", RegexOption.IGNORE_CASE).findAll(xml)
                .map { it.groupValues[1].lowercase() }.toSet()
        }
        val looksLikeXlsForm = "survey" in workbookSheets && ("settings" in workbookSheets || "choices" in workbookSheets)
        val type = when {
            epubMime == "application/epub+zip" && lower.any { it == "meta-inf/container.xml" } -> Triple("epub", "EPUB ebook", "application/epub+zip")
            lower.any { it == "androidmanifest.xml" } -> Triple("apk", "Android APK", "application/vnd.android.package-archive")
            lower.any { it == "[content_types].xml" } && lower.any { it.startsWith("word/") } -> Triple("docx", "Office Open XML document", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            lower.any { it == "[content_types].xml" } && lower.any { it.startsWith("xl/") } && looksLikeXlsForm -> Triple("xlsform", "XLSForm workbook", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            lower.any { it == "[content_types].xml" } && lower.any { it.startsWith("xl/") } -> Triple("xlsx", "Office Open XML workbook", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            lower.any { it == "[content_types].xml" } && lower.any { it.startsWith("ppt/") } -> Triple("pptx", "Office Open XML presentation", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
            lower.any { it.endsWith(".kml") } -> Triple("kmz", "KMZ geospatial archive", "application/vnd.google-earth.kmz")
            looksLikeComicArchive -> Triple("cbz", "CBZ comic archive", "application/vnd.comicbook+zip")
            else -> Triple("zip", "ZIP archive", "application/zip")
        }
        val suspicious = names.filter { it.startsWith("/") || it.replace('\\', '/').split('/').any { part -> part == ".." } }
        val warnings = buildList {
            if (suspicious.isNotEmpty()) add("Archive contains ${suspicious.size} potentially unsafe paths; File Lab never extracts them by path.")
            if (entriesSeen >= ENTRY_LIMIT) add("Archive inventory stopped at $ENTRY_LIMIT entries.")
            if (declared >= DECLARED_LIMIT) add("Archive declares at least 4 GiB uncompressed content; conversion/extraction remains bounded.")
            if (encryptionMetadata) add("Archive contains encryption metadata; File Lab does not remove DRM or bypass protected resources.")
            if (nestedRead >= TOTAL_NESTED_READ_LIMIT) add("Nested archive inspection stopped at the ${TOTAL_NESTED_READ_LIMIT / (1024 * 1024)} MiB safety budget.")
        }
        val facts = mutableListOf(
            FileFact("Entries sampled", entriesSeen.toString()),
            FileFact("Files sampled", files.size.toString()),
            FileFact("Image files", imageFiles.toString()),
            FileFact("CBZ filename hint", cbzExtensionHint.toString()),
            FileFact("Declared uncompressed bytes", declared.toString())
        )
        if (workbookSheets.isNotEmpty()) facts += FileFact("Workbook sheets", workbookSheets.sorted().joinToString(", "))
        if (looksLikeXlsForm) facts += FileFact("XLSForm structure", "survey sheet with choices/settings metadata")
        if (nestedSummaries.isNotEmpty()) facts += FileFact("Nested archives", nestedSummaries.joinToString(" | "))
        return Classification(type.first, type.second, type.third, 98, facts, warnings, "ZIP member structure matches ${type.second}")
    }

    private fun readEntryBounded(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (total < maxBytes) {
            val n = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (n <= 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}

internal object SpecialistInspectors {
    fun inspect(format: String, bytes: ByteArray, name: String): Pair<List<FileFact>, List<String>> = when (format) {
        "fits" -> fits(bytes)
        "adif" -> adif(bytes)
        "csv", "tsv" -> delimited(bytes, if (format == "tsv") '\t' else ',')
        "vcf" -> vcf(bytes)
        "fasta" -> fasta(bytes)
        "sigmf", "json", "geojson" -> jsonHints(bytes, name)
        "elf" -> elf(bytes)
        "pe" -> pe(bytes)
        "wav" -> wav(bytes)
        "rar", "cbr" -> rar(bytes, format == "cbr")
        "7z", "cb7" -> sevenZip(bytes, format == "cb7")
        "pcap" -> pcap(bytes)
        "pcapng" -> listOf(FileFact("Capture format", "pcapng")) to emptyList()
        "npy" -> npy(bytes)
        "hdf5" -> listOf(FileFact("Container", "HDF5")) to emptyList()
        "netcdf" -> netcdf(bytes)
        "parquet" -> listOf(FileFact("Container", "Apache Parquet")) to emptyList()
        "dicom" -> listOf(FileFact("Preamble", "DICM marker present")) to emptyList()
        "txt", "markdown", "html", "xml", "yaml", "toml", "ini", "log", "source", "sql",
        "nmea", "tle", "cabrillo", "xform", "gcode", "gerber", "intel-hex", "ics", "vcard", "eml" -> textFacts(bytes)
        else -> emptyList<FileFact>() to emptyList()
    }

    private fun fits(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        val text = bytes.take(28_800).toByteArray().toString(StandardCharsets.US_ASCII)
        val cards = text.chunked(80).takeWhile { !it.startsWith("END") }
        fun card(key: String) = cards.firstOrNull { it.startsWith(key.padEnd(8)) }
            ?.substringAfter("=")?.substringBefore("/")?.trim()?.trim('\'')
        return listOfNotNull(
            card("BITPIX")?.let { FileFact("BITPIX", it) },
            card("NAXIS")?.let { FileFact("Axes", it) },
            card("NAXIS1")?.let { FileFact("Width", it) },
            card("NAXIS2")?.let { FileFact("Height", it) },
            card("OBJECT")?.let { FileFact("Object", it) },
            card("DATE-OBS")?.let { FileFact("Observation", it) },
            card("EXPTIME")?.let { FileFact("Exposure", it) },
            card("FILTER")?.let { FileFact("Filter", it) },
            card("TELESCOP")?.let { FileFact("Telescope", it) },
            card("INSTRUME")?.let { FileFact("Instrument", it) },
            card("CTYPE1")?.let { FileFact("WCS axis 1", it) },
            card("CTYPE2")?.let { FileFact("WCS axis 2", it) },
            card("CRVAL1")?.let { FileFact("WCS reference 1", it) },
            card("CRVAL2")?.let { FileFact("WCS reference 2", it) },
            FileFact("Header cards sampled", cards.size.toString())
        ) to emptyList()
    }

    private fun adif(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        val text = bytes.toString(StandardCharsets.UTF_8)
        val qsos = Regex("<EOR>", RegexOption.IGNORE_CASE).findAll(text).count()
        val calls = Regex("<CALL:[0-9]+[^>]*>([^<\\r\\n]+)", RegexOption.IGNORE_CASE).findAll(text).map { it.groupValues[1].trim() }.toSet()
        val bands = Regex("<BAND:[0-9]+[^>]*>([^<\\r\\n]+)", RegexOption.IGNORE_CASE).findAll(text).map { it.groupValues[1].trim() }.toSet()
        return listOf(FileFact("QSOs sampled", qsos.toString()), FileFact("Unique callsigns sampled", calls.size.toString()), FileFact("Bands sampled", bands.sorted().joinToString(", "))) to emptyList()
    }

    private fun delimited(bytes: ByteArray, delimiter: Char): Pair<List<FileFact>, List<String>> {
        val rows = parseDelimitedSample(bytes.toString(StandardCharsets.UTF_8), delimiter, 10_001)
        val columns = rows.firstOrNull()?.size ?: 0
        val malformed = rows.drop(1).count { it.size != columns }
        val warnings = buildList { if (malformed > 0) add("$malformed sampled rows have a different field count") }
        return listOf(
            FileFact("Columns", columns.toString()),
            FileFact("Rows sampled", (rows.size - 1).coerceAtLeast(0).toString()),
            FileFact("Delimiter", if (delimiter == '\t') "tab" else delimiter.toString())
        ) to warnings
    }

    private fun parseDelimitedSample(text: String, delimiter: Char, limit: Int): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun finishField() { row.add(field.toString()); field.setLength(0) }
        fun finishRow() { finishField(); rows.add(row); row = mutableListOf() }
        while (i < text.length && rows.size < limit) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> finishField()
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
                }
                else -> field.append(c)
            }
            i++
        }
        if (rows.size < limit && (field.isNotEmpty() || row.isNotEmpty())) finishRow()
        return rows
    }

    private fun vcf(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        val lines = bytes.toString(StandardCharsets.UTF_8).lineSequence().take(100_000).toList()
        val variants = lines.count { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.firstOrNull { it.startsWith("#CHROM") }?.split('\t').orEmpty()
        return listOf(FileFact("Variants sampled", variants.toString()), FileFact("Samples", (header.size - 9).coerceAtLeast(0).toString())) to emptyList()
    }

    private fun fasta(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        val text = bytes.toString(StandardCharsets.UTF_8)
        val records = text.lineSequence().count { it.startsWith(">") }
        val residues = text.lineSequence().filterNot { it.startsWith(">") }.sumOf { it.trim().length }
        return listOf(FileFact("Sequences sampled", records.toString()), FileFact("Residues sampled", residues.toString())) to emptyList()
    }

    private fun jsonHints(bytes: ByteArray, name: String): Pair<List<FileFact>, List<String>> {
        val text = bytes.toString(StandardCharsets.UTF_8)
        if (!name.lowercase().endsWith(".sigmf-meta")) return emptyList<FileFact>() to emptyList()
        fun value(key: String) = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(\\\"[^\\\"]*\\\"|[-+0-9.eE]+)")
            .find(text)?.groupValues?.get(1)?.trim('"')
        return listOfNotNull(
            value("core:sample_rate")?.let { FileFact("Sample rate", it) },
            value("core:frequency")?.let { FileFact("Centre frequency", it) },
            value("core:datatype")?.let { FileFact("Datatype", it) },
            value("core:description")?.let { FileFact("Description", it) }
        ) to emptyList()
    }

    private fun elf(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        if (bytes.size < 20) return emptyList<FileFact>() to listOf("Truncated ELF header")
        val bits = when (bytes[4].toInt()) { 1 -> "32-bit"; 2 -> "64-bit"; else -> "unknown" }
        val endian = when (bytes[5].toInt()) { 1 -> "little-endian"; 2 -> "big-endian"; else -> "unknown" }
        return listOf(FileFact("Class", bits), FileFact("Byte order", endian), FileFact("ABI", (bytes[7].toInt() and 0xff).toString())) to emptyList()
    }


    private fun rar(bytes: ByteArray, comic: Boolean): Pair<List<FileFact>, List<String>> {
        val version = if (bytes.size >= 8 && bytes[6] == 0x01.toByte()) "RAR 5" else "RAR 4 or earlier"
        val facts = listOf(FileFact("Archive family", version), FileFact("Comic container", if (comic) "yes" else "no"))
        return facts to listOf("RAR member enumeration/extraction is not bundled in File Lab yet; the container is recognised but not unpacked.")
    }

    private fun sevenZip(bytes: ByteArray, comic: Boolean): Pair<List<FileFact>, List<String>> {
        val version = if (bytes.size >= 8) "${bytes[6].toInt() and 0xff}.${bytes[7].toInt() and 0xff}" else "unknown"
        return listOf(
            FileFact("Archive family", "7-Zip"),
            FileFact("Archive version", version),
            FileFact("Comic container", if (comic) "yes" else "no")
        ) to listOf("7-Zip member enumeration/extraction is not bundled in File Lab yet; the container is recognised but not unpacked.")
    }

    private fun pe(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        if (bytes.size < 64) return emptyList<FileFact>() to listOf("Truncated DOS/PE header")
        val offset = (bytes[0x3c].toInt() and 0xff) or ((bytes[0x3d].toInt() and 0xff) shl 8) or
            ((bytes[0x3e].toInt() and 0xff) shl 16) or ((bytes[0x3f].toInt() and 0xff) shl 24)
        val pePresent = offset >= 0 && bytes.size >= offset + 6 && bytes[offset] == 0x50.toByte() && bytes[offset + 1] == 0x45.toByte()
        val machine = if (pePresent) ((bytes[offset + 4].toInt() and 0xff) or ((bytes[offset + 5].toInt() and 0xff) shl 8)) else -1
        val machineName = when (machine) { 0x014c -> "x86"; 0x8664 -> "x86-64"; 0x01c0, 0x01c4 -> "ARM"; 0xaa64 -> "ARM64"; else -> if (machine >= 0) "0x%04x".format(machine) else "unknown" }
        return listOf(FileFact("PE header", if (pePresent) "present" else "not found in sample"), FileFact("Machine", machineName)) to emptyList()
    }

    private fun pcap(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        if (bytes.size < 24) return emptyList<FileFact>() to listOf("Truncated pcap global header")
        val magic = bytes.take(4).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val endian = if (magic in setOf("d4c3b2a1", "4d3cb2a1")) "little-endian" else "big-endian"
        val ns = magic in setOf("4d3cb2a1", "a1b23c4d")
        return listOf(FileFact("Byte order", endian), FileFact("Timestamp resolution", if (ns) "nanoseconds" else "microseconds")) to emptyList()
    }

    private fun npy(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        if (bytes.size < 10) return emptyList<FileFact>() to listOf("Truncated NumPy header")
        val major = bytes[6].toInt() and 0xff
        val minor = bytes[7].toInt() and 0xff
        val headerLen = if (major <= 1) {
            (bytes[8].toInt() and 0xff) or ((bytes[9].toInt() and 0xff) shl 8)
        } else if (bytes.size >= 12) {
            (bytes[8].toInt() and 0xff) or ((bytes[9].toInt() and 0xff) shl 8) or ((bytes[10].toInt() and 0xff) shl 16) or ((bytes[11].toInt() and 0xff) shl 24)
        } else 0
        return listOf(FileFact("NPY version", "$major.$minor"), FileFact("Header bytes", headerLen.toString())) to emptyList()
    }

    private fun netcdf(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        val version = if (bytes.size >= 4) when (bytes[3].toInt() and 0xff) { 1 -> "classic"; 2 -> "64-bit offset"; 5 -> "64-bit data"; else -> "unknown" } else "unknown"
        return listOf(FileFact("NetCDF variant", version)) to emptyList()
    }

    private fun textFacts(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        val text = bytes.toString(StandardCharsets.UTF_8)
        val lines = text.lineSequence().count()
        val nonBlank = text.lineSequence().count { it.isNotBlank() }
        return listOf(FileFact("Lines sampled", lines.toString()), FileFact("Non-blank lines sampled", nonBlank.toString())) to emptyList()
    }
    private fun wav(bytes: ByteArray): Pair<List<FileFact>, List<String>> {
        if (bytes.size < 44) return emptyList<FileFact>() to listOf("WAVE header is shorter than the standard 44-byte PCM header")
        fun le16(offset: Int) = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
        fun le32(offset: Int) = (bytes[offset].toLong() and 0xff) or ((bytes[offset + 1].toLong() and 0xff) shl 8) or ((bytes[offset + 2].toLong() and 0xff) shl 16) or ((bytes[offset + 3].toLong() and 0xff) shl 24)
        return listOf(
            FileFact("Channels", le16(22).toString()),
            FileFact("Sample rate", le32(24).toString()),
            FileFact("Bits per sample", le16(34).toString())
        ) to emptyList()
    }
}

object ConversionGraph {
    private val textToPdf = setOf(
        "txt", "markdown", "csv", "tsv", "json", "geojson", "xml", "yaml", "toml", "ini", "log", "source", "sql",
        "adif", "adx", "vcf", "fasta", "fastq", "gpx", "kml", "nmea", "tle", "cabrillo", "xform", "gcode", "gerber", "intel-hex", "ics", "vcard", "eml"
    )
    private val imageToPdf = setOf("png", "jpeg", "webp", "bmp", "gif")

    private val routes = buildList {
        add(ConversionRoute("cbz", "pdf", lossless = false, notes = "Images are embedded into PDF pages."))
        add(ConversionRoute("pdf", "cbz", lossless = false, notes = "PDF pages are rasterised to JPEG."))
        textToPdf.forEach { source ->
            add(ConversionRoute(source, "pdf", lossless = false, notes = "Text is laid out into paginated PDF pages; original text remains unchanged."))
        }
        imageToPdf.forEach { source ->
            add(ConversionRoute(source, "pdf", lossless = false, notes = if (source == "gif") "The first GIF frame is rendered to PDF." else "The image is rendered to a PDF page."))
        }
    }

    fun from(sourceFormat: String): List<ConversionRoute> = routes.filter { it.sourceFormat == sourceFormat }
    fun find(sourceFormat: String, targetFormat: String): ConversionRoute? =
        routes.firstOrNull { it.sourceFormat == sourceFormat && it.targetFormat == targetFormat }

    fun isTextToPdf(sourceFormat: String): Boolean = sourceFormat in textToPdf
    fun isImageToPdf(sourceFormat: String): Boolean = sourceFormat in imageToPdf
}
