package com.example.methodmesh.modules.filelab

/**
 * Offline plain-language file-format knowledge used by the inspector.
 *
 * This is deliberately descriptive rather than authoritative executable logic:
 * detection remains content-first in FileSignatures/SafeZipInspector. The catalogue
 * explains the resulting format ID and never changes the detection result.
 */
data class FileFormatKnowledge(
    val category: String,
    val description: String,
    val technicalIdentity: String,
    val typicalUses: String,
    val typicalSoftware: List<String> = emptyList(),
    val commonProducers: List<String> = emptyList(),
    val relatedFormats: List<String> = emptyList(),
    val caution: String = "",
    val fileLabSupport: String = "Recognition/basic inspection"
)

object FileFormatKnowledgeCatalogue {
    private val entries: Map<String, FileFormatKnowledge> = mapOf(
        "7z" to FileFormatKnowledge(
            category = "Archive",
            description = "A compressed archive created with the 7-Zip format. It can contain many files and folders in one package.",
            technicalIdentity = "A 7z container using LZMA/LZMA2 or other compression methods.",
            typicalUses = "software distribution, backups, large compressed bundles",
            typicalSoftware = listOf("7-Zip", "PeaZip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "aac" to FileFormatKnowledge(
            category = "Audio",
            description = "A compressed digital audio file using Advanced Audio Coding.",
            technicalIdentity = "A raw or ADTS-wrapped AAC audio stream.",
            typicalUses = "music, speech, streaming audio",
            typicalSoftware = listOf("VLC", "ffmpeg", "Audacity"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "adif" to FileFormatKnowledge(
            category = "Amateur radio",
            description = "An amateur-radio contact log stored in the ADIF interchange format.",
            technicalIdentity = "A tagged text record format defined by the Amateur Data Interchange Format specification.",
            typicalUses = "ham-radio QSO logs, contest and station log exchange",
            typicalSoftware = listOf("Log4OM", "N1MM Logger+", "WSJT-X", "fldigi"),
            relatedFormats = listOf("adx", "cabrillo"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "adx" to FileFormatKnowledge(
            category = "Amateur radio",
            description = "An amateur-radio contact log using the XML form of ADIF.",
            technicalIdentity = "XML-based ADIF interchange data.",
            typicalUses = "ham-radio QSO exchange and archival",
            typicalSoftware = listOf("Log4OM", "ADIF-compatible logging software"),
            relatedFormats = listOf("adif"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "apk" to FileFormatKnowledge(
            category = "Android",
            description = "An Android application package. It contains an app manifest, compiled code, resources and signatures.",
            technicalIdentity = "A ZIP-family package with Android-specific manifest, DEX and resource structures.",
            typicalUses = "installing and distributing Android applications",
            typicalSoftware = listOf("Android package installer", "jadx", "APKTool"),
            relatedFormats = listOf("dex", "zip"),
            caution = "An APK can contain executable code. Installing it is a separate trust decision.",
            fileLabSupport = "Deep/container inspection"
        ),
        "arrow" to FileFormatKnowledge(
            category = "Data science",
            description = "A columnar data file using Apache Arrow or Feather.",
            technicalIdentity = "An Arrow IPC/Feather columnar memory-interchange representation.",
            typicalUses = "fast analytics exchange between Python, R and data engines",
            typicalSoftware = listOf("PyArrow", "pandas", "R arrow"),
            relatedFormats = listOf("parquet", "orc"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "avi" to FileFormatKnowledge(
            category = "Video",
            description = "A Microsoft AVI video container.",
            technicalIdentity = "A RIFF-based Audio Video Interleave container.",
            typicalUses = "legacy video and camera recordings",
            typicalSoftware = listOf("VLC", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "avif" to FileFormatKnowledge(
            category = "Image",
            description = "A modern compressed still image using the AV1 image format.",
            technicalIdentity = "An AV1 Image File Format stored in an ISO Base Media File container.",
            typicalUses = "web images, photographs, efficient image delivery",
            typicalSoftware = listOf("modern photo viewers", "ImageMagick", "GIMP"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "azw3" to FileFormatKnowledge(
            category = "Ebook",
            description = "A Kindle ebook using Amazon's AZW3/KF8 format.",
            technicalIdentity = "A Kindle container derived from Palm/Mobipocket structures with HTML/CSS content.",
            typicalUses = "Kindle ebooks",
            typicalSoftware = listOf("Kindle", "KOReader", "Calibre"),
            relatedFormats = listOf("mobi", "epub"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "bam" to FileFormatKnowledge(
            category = "Genomics",
            description = "A compressed binary file containing aligned DNA/RNA sequencing reads.",
            technicalIdentity = "Binary Alignment/Map format, the binary counterpart of SAM.",
            typicalUses = "genomic read alignment and variant workflows",
            typicalSoftware = listOf("samtools", "IGV", "Picard"),
            relatedFormats = listOf("cram", "fasta", "fastq"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "bcf" to FileFormatKnowledge(
            category = "Genomics",
            description = "A binary form of Variant Call Format data.",
            technicalIdentity = "Binary VCF encoding used for genomic variants and genotypes.",
            typicalUses = "variant calling, population genetics",
            typicalSoftware = listOf("bcftools", "IGV"),
            relatedFormats = listOf("vcf"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "binary" to FileFormatKnowledge(
            category = "Binary data",
            description = "A generic binary image whose internal format has not been identified more specifically.",
            technicalIdentity = "Opaque byte-oriented data without a recognised higher-level signature.",
            typicalUses = "firmware, dumps, proprietary data, raw device images",
            typicalSoftware = listOf("hex editor", "Ghidra", "binwalk"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "bmp" to FileFormatKnowledge(
            category = "Image",
            description = "A Windows bitmap image.",
            technicalIdentity = "A raster image stored in the BMP/DIB family of formats.",
            typicalUses = "simple uncompressed or lightly compressed bitmap graphics",
            typicalSoftware = listOf("Windows Photos", "GIMP", "ImageMagick"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + image→PDF conversion"
        ),
        "bzip2" to FileFormatKnowledge(
            category = "Archive",
            description = "A file compressed with the BZip2 algorithm.",
            technicalIdentity = "A single-stream BZip2 compressed payload, not normally a multi-file archive by itself.",
            typicalUses = "compressed text, source releases, Unix archives",
            typicalSoftware = listOf("bzip2", "7-Zip", "PeaZip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "cabrillo" to FileFormatKnowledge(
            category = "Amateur radio",
            description = "A plain-text amateur-radio contest log in Cabrillo format.",
            technicalIdentity = "A line-oriented contest submission format used by many radio competitions.",
            typicalUses = "ham-radio contest logs and adjudication",
            typicalSoftware = listOf("N1MM Logger+", "contest logging software", "text editor"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "cb7" to FileFormatKnowledge(
            category = "Comic book",
            description = "A comic-book archive whose page images are packed in a 7-Zip container.",
            technicalIdentity = "A 7z archive conventionally containing ordered image pages.",
            typicalUses = "digital comics, scanned books and graphic novels",
            typicalSoftware = listOf("KOReader", "CDisplayEx", "Perfect Viewer", "7-Zip"),
            relatedFormats = listOf("cbr", "cbz", "cbt", "7z"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "cbr" to FileFormatKnowledge(
            category = "Comic book",
            description = "A comic-book archive whose page images are packed in a RAR container.",
            technicalIdentity = "A RAR archive conventionally containing ordered image pages.",
            typicalUses = "digital comics, scanned books and graphic novels",
            typicalSoftware = listOf("KOReader", "CDisplayEx", "Perfect Viewer", "RAR"),
            relatedFormats = listOf("cbz", "cb7", "cbt", "rar"),
            caution = "RAR archives may be encrypted or malformed; File Lab does not bypass passwords or DRM.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "cbt" to FileFormatKnowledge(
            category = "Comic book",
            description = "A comic-book archive whose page images are packed in a TAR container.",
            technicalIdentity = "A TAR archive conventionally containing ordered image pages.",
            typicalUses = "digital comics and scanned books",
            typicalSoftware = listOf("KOReader", "comic readers", "tar"),
            relatedFormats = listOf("cbr", "cbz", "cb7", "tar"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "cbz" to FileFormatKnowledge(
            category = "Comic book",
            description = "A comic-book archive whose page images are packed in a ZIP container.",
            technicalIdentity = "A ZIP archive conventionally containing ordered JPEG/PNG/WebP/GIF pages.",
            typicalUses = "digital comics, scanned books and graphic novels",
            typicalSoftware = listOf("KOReader", "CDisplayEx", "Perfect Viewer", "7-Zip"),
            relatedFormats = listOf("cbr", "cb7", "cbt", "zip"),
            caution = "Comic archives are still ZIP archives and can contain malformed or unexpectedly large entries.",
            fileLabSupport = "Deep inspect + CBZ→PDF conversion"
        ),
        "chm" to FileFormatKnowledge(
            category = "Document",
            description = "A Microsoft Compiled HTML Help file.",
            technicalIdentity = "A compressed container of HTML pages, navigation data and related assets.",
            typicalUses = "Windows software help manuals and offline documentation",
            typicalSoftware = listOf("Windows HTML Help", "SumatraPDF", "7-Zip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "cram" to FileFormatKnowledge(
            category = "Genomics",
            description = "A highly compressed genomic alignment file.",
            technicalIdentity = "CRAM stores read alignments using reference-aware compression.",
            typicalUses = "large sequencing alignment archives",
            typicalSoftware = listOf("samtools", "IGV"),
            relatedFormats = listOf("bam", "fasta"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "csv" to FileFormatKnowledge(
            category = "Tabular data",
            description = "A plain-text table whose columns are separated by commas.",
            technicalIdentity = "A delimited text representation of rows and fields.",
            typicalUses = "research data, spreadsheets, exports, line lists",
            typicalSoftware = listOf("Excel", "LibreOffice Calc", "R", "Python/pandas"),
            relatedFormats = listOf("tsv", "xlsx", "parquet"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "czi" to FileFormatKnowledge(
            category = "Microscopy",
            description = "A Zeiss microscopy image/container.",
            technicalIdentity = "Carl Zeiss Image format containing multidimensional microscopy imagery and metadata.",
            typicalUses = "light microscopy, fluorescence and tiled imaging",
            typicalSoftware = listOf("ZEISS ZEN", "Fiji/ImageJ", "Bio-Formats"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "dbf" to FileFormatKnowledge(
            category = "Database / GIS",
            description = "A dBASE table; in GIS it commonly stores the attribute table for a Shapefile.",
            technicalIdentity = "A fixed-record tabular database format from the dBASE family.",
            typicalUses = "legacy databases and ESRI Shapefile attributes",
            typicalSoftware = listOf("QGIS", "LibreOffice Calc", "GDAL"),
            relatedFormats = listOf("shp", "shx"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "dex" to FileFormatKnowledge(
            category = "Android",
            description = "Compiled Android application bytecode.",
            technicalIdentity = "Dalvik Executable bytecode consumed by Android runtimes.",
            typicalUses = "Android apps and libraries",
            typicalSoftware = listOf("jadx", "baksmali", "Android Studio"),
            relatedFormats = listOf("apk", "java-class"),
            caution = "Executable bytecode: treat untrusted files as code.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "dicom" to FileFormatKnowledge(
            category = "Medical imaging",
            description = "A DICOM medical imaging or clinical data file.",
            technicalIdentity = "Digital Imaging and Communications in Medicine object containing pixels and/or structured metadata.",
            typicalUses = "radiology, ultrasound, CT, MRI and medical imaging exchange",
            typicalSoftware = listOf("RadiAnt", "Horos", "3D Slicer", "dcmtk"),
            relatedFormats = emptyList(),
            caution = "May contain sensitive patient identifiers in metadata.",
            fileLabSupport = "Structured inspection"
        ),
        "djvu" to FileFormatKnowledge(
            category = "Document",
            description = "A document format designed for scanned pages and books at compact sizes.",
            technicalIdentity = "A layered image/document compression format optimised for scanned text and illustrations.",
            typicalUses = "digitised books, archives, scanned documents",
            typicalSoftware = listOf("DjView", "SumatraPDF", "KOReader"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "docx" to FileFormatKnowledge(
            category = "Document",
            description = "A Microsoft Word document in the modern Office Open XML format.",
            technicalIdentity = "A ZIP package containing WordprocessingML XML, styles and embedded assets.",
            typicalUses = "word-processing documents",
            typicalSoftware = listOf("Microsoft Word", "LibreOffice Writer", "Google Docs"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Deep/container inspection"
        ),
        "dxf" to FileFormatKnowledge(
            category = "CAD",
            description = "An AutoCAD Drawing Exchange Format file.",
            technicalIdentity = "A text or binary CAD interchange representation of drawing entities.",
            typicalUses = "2D/3D CAD exchange",
            typicalSoftware = listOf("AutoCAD", "LibreCAD", "FreeCAD"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "edf" to FileFormatKnowledge(
            category = "Biosignal",
            description = "A European Data Format biosignal recording.",
            technicalIdentity = "A fixed-header signal format widely used for EEG, sleep and physiological time series.",
            typicalUses = "EEG, polysomnography, physiological monitoring",
            typicalSoftware = listOf("EDFbrowser", "MNE-Python", "EEGLAB"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "elf" to FileFormatKnowledge(
            category = "Executable",
            description = "An ELF executable or shared library used by Linux, Android and many embedded systems. A .so file is usually a shared library loaded by another program rather than a document to open.",
            technicalIdentity = "Executable and Linkable Format containing machine code, sections, symbols and dynamic-linking metadata.",
            typicalUses = "Linux/Android executables, native libraries, firmware components",
            typicalSoftware = listOf("readelf", "objdump", "Ghidra", "IDA"),
            relatedFormats = listOf("pe", "macho", "dex", "wasm"),
            caution = "Executable code: inspect before running or loading into another process.",
            fileLabSupport = "Structured inspection"
        ),
        "eml" to FileFormatKnowledge(
            category = "Email",
            description = "A saved email message including headers, body and possibly attachments.",
            technicalIdentity = "An RFC 5322/MIME internet message stored as a file.",
            typicalUses = "email archiving and interchange",
            typicalSoftware = listOf("Thunderbird", "Outlook", "Apple Mail"),
            relatedFormats = emptyList(),
            caution = "Email files can contain attachments and remote-content references; do not assume they are harmless.",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "epub" to FileFormatKnowledge(
            category = "Ebook",
            description = "A reflowable ebook package.",
            technicalIdentity = "A ZIP container with XHTML/HTML content, metadata, navigation and style resources following the EPUB standard.",
            typicalUses = "ebooks and digital publications",
            typicalSoftware = listOf("KOReader", "Calibre", "Apple Books", "Thorium Reader"),
            relatedFormats = listOf("mobi", "azw3", "pdf"),
            caution = "",
            fileLabSupport = "Deep/container inspection"
        ),
        "excellon" to FileFormatKnowledge(
            category = "PCB",
            description = "A PCB drill file describing hole locations and drill/tool sizes.",
            technicalIdentity = "A machine-oriented NC drill format commonly paired with Gerber board layers.",
            typicalUses = "printed-circuit-board fabrication",
            typicalSoftware = listOf("KiCad", "Gerbv", "CAM software"),
            relatedFormats = listOf("gerber"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "fasta" to FileFormatKnowledge(
            category = "Genomics",
            description = "A text file containing biological sequences such as DNA, RNA or proteins.",
            technicalIdentity = "A header-and-sequence text representation beginning records with >.",
            typicalUses = "reference genomes, amplicons, protein sequences",
            typicalSoftware = listOf("samtools", "seqkit", "Geneious", "text editor"),
            relatedFormats = listOf("fastq", "bam"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "fastq" to FileFormatKnowledge(
            category = "Genomics",
            description = "A sequencing-read file containing bases plus per-base quality scores.",
            technicalIdentity = "A four-line-per-record text format beginning records with @.",
            typicalUses = "raw high-throughput sequencing reads",
            typicalSoftware = listOf("FastQC", "seqkit", "bioinformatics pipelines"),
            relatedFormats = listOf("fasta", "bam"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "fits" to FileFormatKnowledge(
            category = "Astronomy",
            description = "A scientific astronomy file containing images, spectra or tables plus detailed observation metadata.",
            technicalIdentity = "Flexible Image Transport System with 2880-byte blocks and one or more HDUs.",
            typicalUses = "astronomical imaging, telescope products, spectra and catalogues",
            typicalSoftware = listOf("SAOImage DS9", "Astropy", "fv", "FITS Liberator"),
            relatedFormats = listOf("xisf"),
            caution = "",
            fileLabSupport = "Structured inspection"
        ),
        "flac" to FileFormatKnowledge(
            category = "Audio",
            description = "A losslessly compressed audio file.",
            technicalIdentity = "Free Lossless Audio Codec bitstream preserving the original PCM samples.",
            typicalUses = "music archival, field recordings, high-quality audio",
            typicalSoftware = listOf("VLC", "Audacity", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "flv" to FileFormatKnowledge(
            category = "Video",
            description = "An Adobe Flash Video container.",
            technicalIdentity = "Legacy FLV container carrying video/audio streams.",
            typicalUses = "older web video and archived streaming media",
            typicalSoftware = listOf("VLC", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "gcode" to FileFormatKnowledge(
            category = "Fabrication",
            description = "Machine instructions telling a CNC machine or 3D printer how to move and operate.",
            technicalIdentity = "Line-oriented numerical-control commands such as G0/G1 and M-codes.",
            typicalUses = "3D printing, CNC milling, laser cutting",
            typicalSoftware = listOf("PrusaSlicer", "Cura", "CNC controllers", "text editor"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "geojson" to FileFormatKnowledge(
            category = "GIS",
            description = "Geographic features encoded as JSON.",
            technicalIdentity = "A JSON representation of points, lines, polygons and their properties.",
            typicalUses = "web maps, GIS exchange, field data",
            typicalSoftware = listOf("QGIS", "geojson.io", "GDAL"),
            relatedFormats = listOf("gpx", "kml", "geopackage"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "geopackage" to FileFormatKnowledge(
            category = "GIS",
            description = "A portable GIS database containing vector features, rasters or tiles.",
            technicalIdentity = "An OGC GeoPackage stored inside SQLite with standard metadata tables.",
            typicalUses = "field GIS, spatial databases, offline maps",
            typicalSoftware = listOf("QGIS", "ArcGIS Pro", "GDAL"),
            relatedFormats = listOf("shp", "geojson", "mbtiles"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "gerber" to FileFormatKnowledge(
            category = "PCB",
            description = "A Gerber file describing one graphical layer of a printed circuit board.",
            technicalIdentity = "RS-274X/related photoplotter commands for PCB copper, mask, silkscreen and other layers.",
            typicalUses = "PCB manufacturing",
            typicalSoftware = listOf("KiCad", "Gerbv", "CAM software"),
            relatedFormats = listOf("excellon"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "gif" to FileFormatKnowledge(
            category = "Image",
            description = "A GIF image, possibly animated.",
            technicalIdentity = "Palette-based raster graphics using GIF87a/GIF89a structures.",
            typicalUses = "simple graphics, animations, web images",
            typicalSoftware = listOf("web browsers", "GIMP", "ImageMagick"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + image→PDF conversion"
        ),
        "glb" to FileFormatKnowledge(
            category = "3D model",
            description = "A binary glTF 3D scene/model.",
            technicalIdentity = "glTF packaged into a single binary container including geometry and optional textures.",
            typicalUses = "web/AR/3D model exchange",
            typicalSoftware = listOf("Blender", "three.js viewers", "Windows 3D Viewer"),
            relatedFormats = listOf("gltf", "obj"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "gltf" to FileFormatKnowledge(
            category = "3D model",
            description = "A glTF 3D scene/model, usually JSON plus external or embedded assets.",
            technicalIdentity = "Khronos glTF representation of scenes, meshes, materials and animations.",
            typicalUses = "web/AR/3D model exchange",
            typicalSoftware = listOf("Blender", "three.js viewers"),
            relatedFormats = listOf("glb", "obj"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "gpx" to FileFormatKnowledge(
            category = "Navigation / GIS",
            description = "A GPS Exchange Format file containing tracks, routes or waypoints.",
            technicalIdentity = "XML-based geospatial interchange for latitude/longitude observations and routes.",
            typicalUses = "GPS tracks, hiking, fieldwork, geocaching",
            typicalSoftware = listOf("QGIS", "Garmin software", "GPSBabel", "GPX viewers"),
            relatedFormats = listOf("kml", "kmz", "geojson"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "grib" to FileFormatKnowledge(
            category = "Meteorology",
            description = "A compact weather-data file containing gridded forecasts or observations.",
            technicalIdentity = "WMO GRIB/GRIB2 binary encoding for multidimensional meteorological fields.",
            typicalUses = "numerical weather prediction, wind, pressure and rainfall grids",
            typicalSoftware = listOf("Panoply", "wgrib2", "ecCodes", "QGIS"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "gzip" to FileFormatKnowledge(
            category = "Archive",
            description = "A single data stream compressed with GZIP.",
            technicalIdentity = "DEFLATE-compressed payload with a gzip wrapper; commonly used with TAR.",
            typicalUses = "Unix compression, web transfer, .tar.gz bundles",
            typicalSoftware = listOf("gzip", "7-Zip", "PeaZip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "hdf5" to FileFormatKnowledge(
            category = "Scientific data",
            description = "A hierarchical scientific-data container able to store arrays, tables and metadata in one file.",
            technicalIdentity = "HDF5 object hierarchy of groups, datasets, attributes and links.",
            typicalUses = "large scientific datasets, microscopy, simulation and instrument output",
            typicalSoftware = listOf("HDFView", "h5py", "MATLAB", "R"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Structured inspection"
        ),
        "heic" to FileFormatKnowledge(
            category = "Image",
            description = "A high-efficiency image, commonly produced by phones and Apple devices.",
            technicalIdentity = "HEIF container typically carrying HEVC-compressed still images and metadata.",
            typicalUses = "photographs and phone-camera images",
            typicalSoftware = listOf("Apple Photos", "modern photo viewers", "ImageMagick"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "html" to FileFormatKnowledge(
            category = "Web document",
            description = "An HTML web page.",
            technicalIdentity = "Text markup interpreted by web browsers to construct a document.",
            typicalUses = "web pages, reports, offline dashboards",
            typicalSoftware = listOf("Chrome", "Firefox", "Safari", "text editor"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "ico" to FileFormatKnowledge(
            category = "Image",
            description = "A Windows icon file containing one or more small raster images.",
            technicalIdentity = "ICO container for multiple icon sizes and bit depths.",
            typicalUses = "application and website icons",
            typicalSoftware = listOf("Windows icon viewers", "GIMP", "ImageMagick"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "ics" to FileFormatKnowledge(
            category = "Calendar",
            description = "An iCalendar file containing calendar events, tasks or invitations.",
            technicalIdentity = "RFC 5545 text-based calendar interchange data.",
            typicalUses = "meeting invites and calendar export/import",
            typicalSoftware = listOf("Google Calendar", "Outlook", "Apple Calendar"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "ini" to FileFormatKnowledge(
            category = "Configuration",
            description = "A plain-text configuration file organised into sections and key/value pairs.",
            technicalIdentity = "INI-style application configuration text.",
            typicalUses = "software settings and configuration",
            typicalSoftware = listOf("text editor", "VS Code", "Notepad++"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "intel-hex" to FileFormatKnowledge(
            category = "Firmware",
            description = "A text representation of binary memory addresses and bytes used for microcontroller firmware.",
            technicalIdentity = "Intel HEX records with addresses, data bytes and checksums.",
            typicalUses = "microcontroller programming and embedded firmware",
            typicalSoftware = listOf("avrdude", "OpenOCD tools", "hex editors"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "iq" to FileFormatKnowledge(
            category = "Software-defined radio",
            description = "Raw in-phase/quadrature sample data from a radio receiver. The byte layout and sample rate often need to be known separately.",
            technicalIdentity = "Interleaved or planar complex I/Q samples with format determined by capture software or side metadata.",
            typicalUses = "SDR recordings, spectrum analysis and radio research",
            typicalSoftware = listOf("GNU Radio", "Inspectrum", "SDR++"),
            relatedFormats = listOf("sigmf", "sigmf-data"),
            caution = "Raw sample interpretation is impossible without the correct datatype/sample-rate/frequency context.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "iso9660" to FileFormatKnowledge(
            category = "Disk image",
            description = "An optical-disc filesystem image, commonly representing a CD or DVD.",
            technicalIdentity = "ISO 9660 filesystem image with volume descriptors and files.",
            typicalUses = "software installation media and disc archives",
            typicalSoftware = listOf("7-Zip", "disk-image tools", "virtual drive software"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "jar" to FileFormatKnowledge(
            category = "Java",
            description = "A Java archive containing classes and resources.",
            technicalIdentity = "ZIP-family package conventionally containing compiled .class files and META-INF metadata.",
            typicalUses = "Java libraries and applications",
            typicalSoftware = listOf("Java runtime", "7-Zip", "jadx"),
            relatedFormats = listOf("java-class", "zip"),
            caution = "",
            fileLabSupport = "Deep/container inspection"
        ),
        "java-class" to FileFormatKnowledge(
            category = "Java",
            description = "A compiled Java class file.",
            technicalIdentity = "Java Virtual Machine bytecode beginning with the CAFEBABE class signature.",
            typicalUses = "Java applications and libraries",
            typicalSoftware = listOf("javap", "CFR", "IntelliJ IDEA"),
            relatedFormats = listOf("jar", "dex"),
            caution = "Executable bytecode: treat untrusted files as code.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "jpeg" to FileFormatKnowledge(
            category = "Image",
            description = "A JPEG raster image, most commonly used for photographs.",
            technicalIdentity = "Lossy DCT-compressed image in the JPEG family.",
            typicalUses = "photos, camera images, scanned pages",
            typicalSoftware = listOf("photo viewers", "GIMP", "ImageMagick"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + image→PDF conversion"
        ),
        "json" to FileFormatKnowledge(
            category = "Structured data",
            description = "A JSON document containing structured objects, arrays and values.",
            technicalIdentity = "UTF text using JavaScript Object Notation syntax.",
            typicalUses = "APIs, configuration, research exports and interchange",
            typicalSoftware = listOf("text editor", "jq", "VS Code"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "kml" to FileFormatKnowledge(
            category = "GIS",
            description = "A Keyhole Markup Language file describing geographic features and map annotations.",
            technicalIdentity = "XML-based geospatial markup used by Google Earth and GIS tools.",
            typicalUses = "maps, points, tracks and geographic overlays",
            typicalSoftware = listOf("Google Earth", "QGIS", "GDAL"),
            relatedFormats = listOf("kmz", "gpx", "geojson"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "kmz" to FileFormatKnowledge(
            category = "GIS",
            description = "A compressed KML map package.",
            technicalIdentity = "A ZIP archive containing a KML document and optional images/resources.",
            typicalUses = "portable Google Earth/GIS map bundles",
            typicalSoftware = listOf("Google Earth", "QGIS", "7-Zip"),
            relatedFormats = listOf("kml", "gpx"),
            caution = "",
            fileLabSupport = "Deep/container inspection"
        ),
        "las" to FileFormatKnowledge(
            category = "Lidar",
            description = "A lidar point-cloud file containing 3D coordinates and attributes.",
            technicalIdentity = "ASPRS LAS binary point-cloud format.",
            typicalUses = "surveying, terrain mapping and airborne/terrestrial lidar",
            typicalSoftware = listOf("CloudCompare", "PDAL", "QGIS"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "laz" to FileFormatKnowledge(
            category = "Lidar",
            description = "A losslessly compressed lidar point-cloud file.",
            technicalIdentity = "LAS point-cloud data compressed with LASzip-compatible encoding.",
            typicalUses = "large lidar datasets",
            typicalSoftware = listOf("CloudCompare", "PDAL", "QGIS"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "log" to FileFormatKnowledge(
            category = "Text",
            description = "A plain-text log recording events or diagnostic output from software or devices.",
            technicalIdentity = "Line-oriented text with application-specific structure.",
            typicalUses = "debugging, audit trails and device logs",
            typicalSoftware = listOf("text editor", "less", "VS Code"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "m4a" to FileFormatKnowledge(
            category = "Audio",
            description = "An MPEG-4 audio container, commonly carrying AAC or ALAC audio.",
            technicalIdentity = "ISO Base Media container specialised for audio tracks.",
            typicalUses = "music, voice recordings and podcasts",
            typicalSoftware = listOf("VLC", "Apple Music", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "macho" to FileFormatKnowledge(
            category = "Executable",
            description = "A Mach-O executable or library used by macOS, iOS and related Apple platforms.",
            technicalIdentity = "Mach Object binary containing executable code, load commands and linked-library metadata.",
            typicalUses = "Apple applications, frameworks and native libraries",
            typicalSoftware = listOf("otool", "Hopper", "Ghidra", "IDA"),
            relatedFormats = listOf("elf", "pe"),
            caution = "Executable code: inspect before running.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "markdown" to FileFormatKnowledge(
            category = "Document",
            description = "A plain-text Markdown document using lightweight markup for headings, lists, links and code.",
            technicalIdentity = "Human-readable text transformed by Markdown/Quarto/R Markdown processors.",
            typicalUses = "documentation, reproducible reports and websites",
            typicalSoftware = listOf("VS Code", "Obsidian", "Quarto", "RStudio"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "mat" to FileFormatKnowledge(
            category = "Scientific data",
            description = "A MATLAB data file containing arrays, variables or workspace objects.",
            technicalIdentity = "MAT-file, with layout depending on MATLAB file version; newer v7.3 files are HDF5-based.",
            typicalUses = "scientific computing and MATLAB workflows",
            typicalSoftware = listOf("MATLAB", "Python scipy/h5py", "GNU Octave"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "mbtiles" to FileFormatKnowledge(
            category = "GIS",
            description = "An offline map-tile database in the MBTiles format.",
            technicalIdentity = "SQLite database holding map tiles plus metadata.",
            typicalUses = "offline basemaps and vector/raster tile distribution",
            typicalSoftware = listOf("QGIS", "MapLibre tools", "mb-util"),
            relatedFormats = listOf("geopackage"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "midi" to FileFormatKnowledge(
            category = "Music",
            description = "A MIDI sequence containing musical events rather than recorded sound.",
            technicalIdentity = "Standard MIDI File event stream encoding notes, timing, controllers and instruments.",
            typicalUses = "music composition, playback and instrument control",
            typicalSoftware = listOf("DAWs", "MuseScore", "VLC"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "mkv" to FileFormatKnowledge(
            category = "Video",
            description = "A Matroska multimedia container.",
            technicalIdentity = "Flexible container for multiple video, audio, subtitle and metadata streams.",
            typicalUses = "movies, archival video and multi-track media",
            typicalSoftware = listOf("VLC", "mpv", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "mobi" to FileFormatKnowledge(
            category = "Ebook",
            description = "A Mobipocket/older Kindle ebook.",
            technicalIdentity = "Palm database-derived Mobipocket container with compressed ebook content.",
            typicalUses = "older Kindle and Mobipocket ebooks",
            typicalSoftware = listOf("KOReader", "Calibre", "Kindle"),
            relatedFormats = listOf("azw3", "epub"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "mov" to FileFormatKnowledge(
            category = "Video",
            description = "A QuickTime movie container.",
            technicalIdentity = "QuickTime/ISO Base Media container for video, audio and timed metadata.",
            typicalUses = "camera/video editing workflows",
            typicalSoftware = listOf("QuickTime Player", "VLC", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "mp3" to FileFormatKnowledge(
            category = "Audio",
            description = "A compressed MP3 audio file.",
            technicalIdentity = "MPEG-1/2 Audio Layer III frames with optional metadata tags.",
            typicalUses = "music, speech and podcasts",
            typicalSoftware = listOf("VLC", "Audacity", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "mp4" to FileFormatKnowledge(
            category = "Video",
            description = "An MPEG-4/ISO Base Media container that may contain video, audio, subtitles and metadata.",
            technicalIdentity = "ISO Base Media File Format container.",
            typicalUses = "video delivery, camera recordings and streaming",
            typicalSoftware = listOf("VLC", "mpv", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "mrc" to FileFormatKnowledge(
            category = "Microscopy",
            description = "A volumetric microscopy file, especially common in electron microscopy and cryo-EM.",
            technicalIdentity = "MRC/CCP4 array format representing 2D or 3D density data.",
            typicalUses = "electron microscopy and structural biology",
            typicalSoftware = listOf("IMOD", "ChimeraX", "RELION tools"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "ndjson" to FileFormatKnowledge(
            category = "Structured data",
            description = "A stream of JSON records with one JSON value per line.",
            technicalIdentity = "Newline-delimited JSON designed for incremental processing.",
            typicalUses = "logs, data pipelines and large exports",
            typicalSoftware = listOf("jq", "Python", "text editor"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "netcdf" to FileFormatKnowledge(
            category = "Scientific data",
            description = "A self-describing scientific dataset commonly used for gridded multidimensional data.",
            technicalIdentity = "NetCDF container for named dimensions, variables and attributes.",
            typicalUses = "climate, oceanography, atmospheric and model data",
            typicalSoftware = listOf("Panoply", "xarray", "ncdump", "R"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Structured inspection"
        ),
        "nifti" to FileFormatKnowledge(
            category = "Medical / neuroimaging",
            description = "A NIfTI neuroimaging volume.",
            technicalIdentity = "Neuroimaging Informatics Technology Initiative format for 3D/4D imaging plus spatial metadata.",
            typicalUses = "MRI, fMRI and neuroimaging research",
            typicalSoftware = listOf("FSL", "3D Slicer", "MRIcroGL"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "nmea" to FileFormatKnowledge(
            category = "Navigation",
            description = "A text log of NMEA navigation sentences from GPS/GNSS or marine instruments.",
            technicalIdentity = "Line-oriented NMEA 0183-style messages beginning with talker/message identifiers.",
            typicalUses = "GPS fixes, marine navigation and receiver diagnostics",
            typicalSoftware = listOf("GPSBabel", "GPS tools", "text editor"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "npy" to FileFormatKnowledge(
            category = "Data science",
            description = "A NumPy binary array file.",
            technicalIdentity = "NumPy .npy header plus typed n-dimensional array data.",
            typicalUses = "Python numerical arrays and machine-learning data",
            typicalSoftware = listOf("NumPy", "Python", "Julia/R import tools"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Structured inspection"
        ),
        "npz" to FileFormatKnowledge(
            category = "Data science",
            description = "A NumPy archive containing one or more NumPy arrays.",
            technicalIdentity = "ZIP container whose members are typically .npy arrays.",
            typicalUses = "bundled numerical datasets and model data",
            typicalSoftware = listOf("NumPy", "Python", "7-Zip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Deep/container inspection"
        ),
        "obj" to FileFormatKnowledge(
            category = "3D model",
            description = "A Wavefront OBJ 3D model.",
            technicalIdentity = "Text-based mesh format listing vertices, texture coordinates, normals and faces.",
            typicalUses = "3D interchange and modelling",
            typicalSoftware = listOf("Blender", "MeshLab", "FreeCAD"),
            relatedFormats = listOf("stl", "ply", "gltf"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "ogg" to FileFormatKnowledge(
            category = "Audio / media",
            description = "An Ogg media container, commonly carrying Vorbis or Opus audio.",
            technicalIdentity = "Ogg page/packet container for one or more encoded streams.",
            typicalUses = "music, speech and open web media",
            typicalSoftware = listOf("VLC", "Audacity", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "ole" to FileFormatKnowledge(
            category = "Legacy Office",
            description = "A Microsoft Compound File document, often an older Word, Excel or PowerPoint file.",
            technicalIdentity = "OLE Compound Binary File containing multiple internal streams and storages.",
            typicalUses = "legacy Microsoft Office and Windows documents",
            typicalSoftware = listOf("Microsoft Office", "LibreOffice", "7-Zip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "orc" to FileFormatKnowledge(
            category = "Data analytics",
            description = "An Apache ORC columnar data file.",
            technicalIdentity = "Optimized Row Columnar storage with typed stripes, indexes and compression.",
            typicalUses = "big-data analytics and Hive/Spark ecosystems",
            typicalSoftware = listOf("Apache Spark", "Hive", "Python/R ORC libraries"),
            relatedFormats = listOf("parquet", "arrow"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "parquet" to FileFormatKnowledge(
            category = "Data analytics",
            description = "An Apache Parquet columnar data file.",
            technicalIdentity = "Typed, compressed columnar storage organised into row groups and metadata.",
            typicalUses = "analytics, data lakes, Python/R exchange",
            typicalSoftware = listOf("PyArrow", "pandas", "DuckDB", "R arrow"),
            relatedFormats = listOf("arrow", "orc", "csv"),
            caution = "",
            fileLabSupport = "Structured inspection"
        ),
        "pcap" to FileFormatKnowledge(
            category = "Network capture",
            description = "A packet-capture file containing raw network packets.",
            technicalIdentity = "Classic libpcap capture format with link-layer frames and timestamps.",
            typicalUses = "network troubleshooting, protocol analysis and security research",
            typicalSoftware = listOf("Wireshark", "tcpdump", "tshark"),
            relatedFormats = listOf("pcapng"),
            caution = "Packet captures can contain sensitive network payloads and credentials.",
            fileLabSupport = "Structured inspection"
        ),
        "pcapng" to FileFormatKnowledge(
            category = "Network capture",
            description = "A modern packet-capture file that can store packets plus richer interface and metadata information.",
            technicalIdentity = "PCAP Next Generation block-based capture format.",
            typicalUses = "network troubleshooting, protocol analysis and security research",
            typicalSoftware = listOf("Wireshark", "tshark"),
            relatedFormats = listOf("pcap"),
            caution = "Packet captures can contain sensitive network payloads and credentials.",
            fileLabSupport = "Structured inspection"
        ),
        "pdf" to FileFormatKnowledge(
            category = "Document",
            description = "A Portable Document Format file designed to preserve page layout across devices.",
            technicalIdentity = "A page-description document containing objects, fonts, images, streams and optional interactive structures.",
            typicalUses = "reports, papers, forms, scanned documents and publications",
            typicalSoftware = listOf("Adobe Acrobat", "Chrome", "KOReader", "SumatraPDF"),
            relatedFormats = listOf("epub", "cbz"),
            caution = "PDFs can contain scripts, links, forms or embedded files. Opening untrusted PDFs in a full reader has a larger attack surface than File Lab inspection.",
            fileLabSupport = "Basic inspect + PDF→CBZ conversion"
        ),
        "pe" to FileFormatKnowledge(
            category = "Executable",
            description = "A Windows executable or dynamic-link library.",
            technicalIdentity = "Portable Executable format containing machine code, imports, resources and sections.",
            typicalUses = "Windows .exe/.dll software and firmware components",
            typicalSoftware = listOf("dumpbin", "Ghidra", "IDA", "PE-bear"),
            relatedFormats = listOf("elf", "macho"),
            caution = "Executable code: inspect before running.",
            fileLabSupport = "Structured inspection"
        ),
        "ply" to FileFormatKnowledge(
            category = "3D model",
            description = "A Polygon File Format model or point cloud.",
            technicalIdentity = "Text or binary representation of vertices plus optional faces and properties.",
            typicalUses = "3D scanning, meshes and point clouds",
            typicalSoftware = listOf("MeshLab", "CloudCompare", "Blender"),
            relatedFormats = listOf("stl", "obj"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "png" to FileFormatKnowledge(
            category = "Image",
            description = "A losslessly compressed PNG raster image.",
            technicalIdentity = "Chunk-based Portable Network Graphics with DEFLATE-compressed pixels.",
            typicalUses = "screenshots, diagrams, graphics and images needing transparency",
            typicalSoftware = listOf("photo viewers", "GIMP", "ImageMagick"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + image→PDF conversion"
        ),
        "pptx" to FileFormatKnowledge(
            category = "Presentation",
            description = "A Microsoft PowerPoint presentation in Office Open XML format.",
            technicalIdentity = "ZIP package containing PresentationML slides, themes and embedded assets.",
            typicalUses = "slide decks and presentations",
            typicalSoftware = listOf("Microsoft PowerPoint", "LibreOffice Impress", "Google Slides"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Deep/container inspection"
        ),
        "prj" to FileFormatKnowledge(
            category = "GIS",
            description = "A coordinate-reference-system description, often accompanying a Shapefile.",
            technicalIdentity = "Usually Well-Known Text describing a map projection and datum.",
            typicalUses = "GIS coordinate-system metadata",
            typicalSoftware = listOf("QGIS", "ArcGIS", "GDAL"),
            relatedFormats = listOf("shp"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "psd" to FileFormatKnowledge(
            category = "Image",
            description = "An Adobe Photoshop working document with layers and editing information.",
            technicalIdentity = "Photoshop native raster document with layered image structures.",
            typicalUses = "photo editing, design and graphics workflows",
            typicalSoftware = listOf("Adobe Photoshop", "Photopea", "GIMP"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "rar" to FileFormatKnowledge(
            category = "Archive",
            description = "A RAR compressed archive containing one or more files.",
            technicalIdentity = "RAR container supporting compression, solid archives and optional encryption.",
            typicalUses = "software/data bundles and backups",
            typicalSoftware = listOf("RAR", "WinRAR", "7-Zip", "PeaZip"),
            relatedFormats = emptyList(),
            caution = "RAR archives may be encrypted; File Lab does not bypass passwords.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "rdata" to FileFormatKnowledge(
            category = "Statistics",
            description = "An R workspace file containing one or more serialized R objects.",
            technicalIdentity = "RData/R workspace serialization.",
            typicalUses = "R analysis workspaces and saved object collections",
            typicalSoftware = listOf("R", "RStudio"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "rds" to FileFormatKnowledge(
            category = "Statistics",
            description = "A serialized single R object.",
            technicalIdentity = "R native object serialization written by saveRDS/readRDS.",
            typicalUses = "R datasets, models and analysis objects",
            typicalSoftware = listOf("R", "RStudio"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "rtf" to FileFormatKnowledge(
            category = "Document",
            description = "A Rich Text Format document containing formatted text.",
            technicalIdentity = "Microsoft RTF control-word text representation of styles, fonts and embedded elements.",
            typicalUses = "portable formatted documents and legacy word processing",
            typicalSoftware = listOf("Microsoft Word", "LibreOffice Writer", "TextEdit"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "sas7bdat" to FileFormatKnowledge(
            category = "Statistics",
            description = "A SAS dataset.",
            technicalIdentity = "SAS proprietary binary table format containing variables, labels and observations.",
            typicalUses = "statistical analysis and clinical/research datasets",
            typicalSoftware = listOf("SAS", "Python pyreadstat", "R haven"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "shp" to FileFormatKnowledge(
            category = "GIS",
            description = "The geometry component of an ESRI Shapefile dataset. It normally travels with .shx, .dbf and often .prj sidecar files.",
            technicalIdentity = "Binary vector geometry records for points, lines or polygons.",
            typicalUses = "GIS vector data exchange",
            typicalSoftware = listOf("QGIS", "ArcGIS", "GDAL"),
            relatedFormats = listOf("shx", "dbf", "prj"),
            caution = "A Shapefile is a multi-file dataset; .shp alone may be incomplete.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "shx" to FileFormatKnowledge(
            category = "GIS",
            description = "The index component of an ESRI Shapefile dataset.",
            technicalIdentity = "Positional index linking Shapefile geometry records.",
            typicalUses = "supporting .shp geometry access",
            typicalSoftware = listOf("QGIS", "ArcGIS", "GDAL"),
            relatedFormats = listOf("shp", "dbf"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "sigmf" to FileFormatKnowledge(
            category = "Software-defined radio",
            description = "SigMF metadata describing a radio-frequency I/Q recording. It usually accompanies a .sigmf-data sample file.",
            technicalIdentity = "JSON metadata following the Signal Metadata Format standard.",
            typicalUses = "SDR capture exchange, RF experiments and spectrum research",
            typicalSoftware = listOf("GNU Radio", "Inspectrum", "SigMF tools"),
            relatedFormats = listOf("sigmf-data", "iq"),
            caution = "",
            fileLabSupport = "Structured inspection"
        ),
        "sigmf-data" to FileFormatKnowledge(
            category = "Software-defined radio",
            description = "The binary sample payload of a SigMF radio recording. Its matching .sigmf-meta file explains sample type, rate and frequency.",
            technicalIdentity = "Raw I/Q or real samples whose datatype and capture metadata are defined by SigMF JSON metadata.",
            typicalUses = "SDR recordings and RF analysis",
            typicalSoftware = listOf("GNU Radio", "Inspectrum", "SigMF tools"),
            relatedFormats = listOf("sigmf", "iq"),
            caution = "Interpret together with the matching .sigmf-meta file when available.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "source" to FileFormatKnowledge(
            category = "Source code",
            description = "A source-code text file. File Lab has recognised the extension as a programming-language source file rather than a generic text document.",
            technicalIdentity = "Human-readable program source consumed by a compiler or interpreter.",
            typicalUses = "software development and scripting",
            typicalSoftware = listOf("VS Code", "Android Studio", "Vim", "language-specific IDEs"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "spss" to FileFormatKnowledge(
            category = "Statistics",
            description = "An IBM SPSS dataset.",
            technicalIdentity = "SPSS system-file representation of variables, labels, missing-value rules and cases.",
            typicalUses = "survey, social-science and health research datasets",
            typicalSoftware = listOf("IBM SPSS Statistics", "R haven", "Python pyreadstat"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "sql" to FileFormatKnowledge(
            category = "Database",
            description = "A text file containing SQL statements.",
            technicalIdentity = "Structured Query Language commands for creating, querying or modifying databases.",
            typicalUses = "database schema, migrations and data queries",
            typicalSoftware = listOf("database clients", "sqlite3", "VS Code"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "sqlite" to FileFormatKnowledge(
            category = "Database",
            description = "A self-contained SQLite database stored in a single file.",
            technicalIdentity = "SQLite 3 B-tree database with schema, tables, indexes and pages.",
            typicalUses = "mobile apps, local databases, caches and embedded systems",
            typicalSoftware = listOf("DB Browser for SQLite", "sqlite3", "DBeaver"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "stata" to FileFormatKnowledge(
            category = "Statistics",
            description = "A Stata dataset (.dta).",
            technicalIdentity = "Stata binary table format including variables, labels and observations.",
            typicalUses = "epidemiology, economics and statistical research",
            typicalSoftware = listOf("Stata", "R haven", "Python pyreadstat"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "stl" to FileFormatKnowledge(
            category = "3D model",
            description = "An STL file describing a 3D surface as triangles.",
            technicalIdentity = "ASCII or binary stereolithography mesh without rich material/scene semantics.",
            typicalUses = "3D printing and CAD mesh interchange",
            typicalSoftware = listOf("PrusaSlicer", "Cura", "MeshLab", "FreeCAD"),
            relatedFormats = listOf("obj", "ply", "gltf", "glb"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "svg" to FileFormatKnowledge(
            category = "Image",
            description = "A scalable vector graphic described with XML.",
            technicalIdentity = "SVG XML elements define vector paths, shapes, text and styling.",
            typicalUses = "icons, diagrams, maps and web graphics",
            typicalSoftware = listOf("web browsers", "Inkscape", "Illustrator"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "tar" to FileFormatKnowledge(
            category = "Archive",
            description = "A TAR archive combining many files into one sequential package. Compression, if any, is usually provided by an outer .gz/.xz/.bz2 layer.",
            technicalIdentity = "Tape Archive stream of file headers and data blocks.",
            typicalUses = "Unix backups, source releases and containers",
            typicalSoftware = listOf("tar", "7-Zip", "PeaZip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "tiff" to FileFormatKnowledge(
            category = "Image",
            description = "A TIFF raster image; some TIFF-family files are scientific imagery, GeoTIFF maps or camera DNG negatives.",
            technicalIdentity = "Tagged Image File Format with flexible image tags and metadata.",
            typicalUses = "scans, publishing, remote sensing, microscopy and photography",
            typicalSoftware = listOf("ImageMagick", "GIMP", "Photoshop", "QGIS for GeoTIFF"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "tle" to FileFormatKnowledge(
            category = "Space / astronomy",
            description = "A Two-Line Element orbital data set describing an Earth-orbiting object.",
            technicalIdentity = "Fixed-column text elements used with SGP4/SDP4 orbital propagation.",
            typicalUses = "satellite tracking and amateur astronomy",
            typicalSoftware = listOf("GPredict", "Skyfield", "CelesTrak-compatible tools"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "toml" to FileFormatKnowledge(
            category = "Configuration",
            description = "A human-readable TOML configuration document.",
            technicalIdentity = "Typed key/value configuration syntax organised into tables.",
            typicalUses = "software configuration and project metadata",
            typicalSoftware = listOf("text editor", "VS Code"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "tsv" to FileFormatKnowledge(
            category = "Tabular data",
            description = "A plain-text table whose columns are separated by tab characters.",
            technicalIdentity = "Tab-delimited rows and fields.",
            typicalUses = "research data, bioinformatics and spreadsheet exchange",
            typicalSoftware = listOf("Excel", "LibreOffice Calc", "R", "Python/pandas"),
            relatedFormats = listOf("csv", "xlsx"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "txt" to FileFormatKnowledge(
            category = "Text",
            description = "A plain-text file with no embedded formatting.",
            technicalIdentity = "Character data, commonly UTF-8 or another text encoding.",
            typicalUses = "notes, logs, exports and simple documents",
            typicalSoftware = listOf("text editor", "VS Code", "Notepad"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "uf2" to FileFormatKnowledge(
            category = "Firmware",
            description = "A UF2 firmware image designed for easy microcontroller flashing by copying a file to a bootloader drive.",
            technicalIdentity = "Block-based firmware container with target addresses and payload chunks.",
            typicalUses = "RP2040 and other microcontroller firmware deployment",
            typicalSoftware = listOf("UF2 bootloader drives", "microcontroller toolchains"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "unknown" to FileFormatKnowledge(
            category = "Unknown",
            description = "File Lab could not identify this file from its current content signatures or filename extension.",
            technicalIdentity = "Unclassified bytes. The file may use a proprietary, encrypted, truncated or simply unsupported format.",
            typicalUses = "unknown or proprietary data",
            typicalSoftware = listOf("hex editor", "file/TrID-style identification tools"),
            relatedFormats = emptyList(),
            caution = "Do not trust a filename extension alone when the content is unrecognised.",
            fileLabSupport = "Generic inspection only"
        ),
        "vcard" to FileFormatKnowledge(
            category = "Contacts",
            description = "A vCard contact file containing names, phone numbers, addresses and related contact fields.",
            technicalIdentity = "Text-based contact interchange using BEGIN:VCARD / END:VCARD records.",
            typicalUses = "contact import/export",
            typicalSoftware = listOf("Contacts apps", "Outlook", "Apple Contacts"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "vcf" to FileFormatKnowledge(
            category = "Genomics",
            description = "A Variant Call Format file describing genetic variants and genotypes.",
            technicalIdentity = "Tabular genomics format with ## metadata headers and #CHROM columns.",
            typicalUses = "variant calling, population genetics and sequencing analysis",
            typicalSoftware = listOf("bcftools", "IGV", "vcftools"),
            relatedFormats = listOf("bcf", "bam", "cram"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "wasm" to FileFormatKnowledge(
            category = "Executable",
            description = "A WebAssembly binary module.",
            technicalIdentity = "Portable stack-machine bytecode beginning with the WebAssembly magic header.",
            typicalUses = "web applications, sandboxed plugins and portable runtimes",
            typicalSoftware = listOf("wasmtime", "wasmer", "browser developer tools"),
            relatedFormats = emptyList(),
            caution = "Executable bytecode: treat untrusted files as code.",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "wav" to FileFormatKnowledge(
            category = "Audio",
            description = "A WAVE audio file containing sampled sound.",
            technicalIdentity = "RIFF/WAVE container, often carrying uncompressed PCM but also other codecs.",
            typicalUses = "recordings, sound effects, research audio and laboratory signals",
            typicalSoftware = listOf("VLC", "Audacity", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Structured inspection"
        ),
        "webm" to FileFormatKnowledge(
            category = "Video",
            description = "A WebM multimedia container designed for web delivery.",
            technicalIdentity = "Matroska-derived container commonly carrying VP8/VP9/AV1 video and Opus/Vorbis audio.",
            typicalUses = "web video and streaming",
            typicalSoftware = listOf("VLC", "web browsers", "ffmpeg"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "webp" to FileFormatKnowledge(
            category = "Image",
            description = "A WebP still or animated image.",
            technicalIdentity = "RIFF-based image container using VP8/VP8L/related coding.",
            typicalUses = "web images and efficient graphics",
            typicalSoftware = listOf("web browsers", "GIMP", "ImageMagick"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + image→PDF conversion"
        ),
        "xform" to FileFormatKnowledge(
            category = "Research / ODK",
            description = "An XML XForm, commonly used as the compiled machine-readable form definition behind ODK/OpenRosa tools.",
            technicalIdentity = "XML form model containing binds, instances and controls.",
            typicalUses = "mobile data-collection form deployment",
            typicalSoftware = listOf("ODK Collect", "JavaRosa/OpenRosa tooling", "XML editor"),
            relatedFormats = listOf("xlsform"),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "xisf" to FileFormatKnowledge(
            category = "Astronomy",
            description = "An XISF astronomical image used especially by PixInsight workflows.",
            technicalIdentity = "Extensible Image Serialization Format storing scientific image data and metadata.",
            typicalUses = "astrophotography calibration and processing",
            typicalSoftware = listOf("PixInsight"),
            relatedFormats = listOf("fits"),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "xlsform" to FileFormatKnowledge(
            category = "Research / ODK",
            description = "An XLSForm: an Excel workbook whose survey/choices/settings sheets define a form for ODK, KoboToolbox and related tools.",
            technicalIdentity = "Office Open XML workbook following the XLSForm authoring convention.",
            typicalUses = "mobile data collection, surveys and research instruments",
            typicalSoftware = listOf("ODK XLSForm Online", "ODK Central", "KoboToolbox", "Excel/LibreOffice"),
            relatedFormats = listOf("xlsx", "xform"),
            caution = "Opening is safe as a document, but deployment semantics should still be validated before field use.",
            fileLabSupport = "Deep/container inspection"
        ),
        "xlsx" to FileFormatKnowledge(
            category = "Spreadsheet",
            description = "A Microsoft Excel workbook in Office Open XML format.",
            technicalIdentity = "ZIP package containing SpreadsheetML worksheets, shared strings, styles and other parts.",
            typicalUses = "spreadsheets, data tables and calculations",
            typicalSoftware = listOf("Microsoft Excel", "LibreOffice Calc", "Google Sheets"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Deep/container inspection"
        ),
        "xml" to FileFormatKnowledge(
            category = "Structured data",
            description = "An XML document containing nested tagged data.",
            technicalIdentity = "Extensible Markup Language text with elements, attributes and namespaces.",
            typicalUses = "configuration, standards-based interchange and documents",
            typicalSoftware = listOf("web browsers", "VS Code", "XML editors"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "xz" to FileFormatKnowledge(
            category = "Archive",
            description = "A file compressed with the XZ/LZMA2 format.",
            technicalIdentity = "Single-stream XZ container, often wrapped around TAR archives.",
            typicalUses = "Unix package/source compression",
            typicalSoftware = listOf("xz", "7-Zip", "PeaZip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        ),
        "yaml" to FileFormatKnowledge(
            category = "Configuration",
            description = "A YAML document containing human-readable structured data.",
            technicalIdentity = "Indentation-based YAML mapping/sequence/scalar syntax.",
            typicalUses = "configuration, automation and metadata",
            typicalSoftware = listOf("text editor", "VS Code"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Inspect + text→PDF conversion"
        ),
        "zip" to FileFormatKnowledge(
            category = "Archive",
            description = "A ZIP archive containing one or more files. File Lab also checks ZIP internals for more specific formats such as EPUB, APK, Office documents, XLSForms and CBZ.",
            technicalIdentity = "PKZIP-compatible container with per-entry compression and central directory metadata.",
            typicalUses = "file bundles, document containers, software packages and backups",
            typicalSoftware = listOf("7-Zip", "PeaZip", "system archive tools"),
            relatedFormats = emptyList(),
            caution = "Archives can contain unsafe paths or decompression bombs; File Lab keeps bounded inspection limits.",
            fileLabSupport = "Deep/container inspection"
        ),
        "zstd" to FileFormatKnowledge(
            category = "Archive",
            description = "A data stream compressed with the Zstandard algorithm.",
            technicalIdentity = "Zstandard framed compressed data, usually a single stream.",
            typicalUses = "fast backups, package compression and data pipelines",
            typicalSoftware = listOf("zstd", "7-Zip", "PeaZip"),
            relatedFormats = emptyList(),
            caution = "",
            fileLabSupport = "Recognition/basic inspection"
        )
    )

    fun lookup(formatId: String, formatName: String): FileFormatKnowledge = (entries[formatId] ?: FileFormatKnowledge(
        category = "Other",
        description = "$formatName is recognised by File Lab, but this build does not yet have a dedicated explanatory catalogue entry.",
        technicalIdentity = "Detected format ID: $formatId.",
        typicalUses = "Format-specific use varies.",
        caution = "Detection metadata is available, but the knowledge catalogue is incomplete for this format.",
        fileLabSupport = "Recognition/basic inspection"
    )).let { base -> if (base.commonProducers.isNotEmpty()) base else base.copy(commonProducers = producerHints(formatId)) }

    private fun producerHints(formatId: String): List<String> = when (formatId) {
        "pdf" -> listOf("office suites", "browsers", "publishing/export tools", "scanners")
        "docx", "xlsx", "pptx" -> listOf("Microsoft Office", "LibreOffice", "Google Workspace exports")
        "xlsform" -> listOf("Excel/LibreOffice-authored XLSForms", "ODK/Kobo form-design workflows")
        "epub" -> listOf("ebook publishing tools", "Calibre", "document-to-EPUB exporters")
        "mobi", "azw3" -> listOf("Kindle publishing toolchains", "Calibre")
        "cbr", "cbz", "cb7", "cbt" -> listOf("comic/scanning workflows", "archive tools", "comic collection managers")
        "jpeg", "png", "gif", "webp", "heic", "avif", "tiff" -> listOf("cameras", "phones", "image editors", "scanners")
        "wav", "flac", "mp3", "aac", "m4a", "ogg" -> listOf("audio recorders", "DAWs", "media encoders")
        "mp4", "mov", "mkv", "webm", "avi" -> listOf("cameras", "phones", "video editors", "media encoders")
        "fits", "xisf" -> listOf("astronomical cameras", "observatory pipelines", "astrophotography software")
        "dicom" -> listOf("medical imaging modalities", "PACS/export systems")
        "hdf5", "netcdf", "grib" -> listOf("scientific instruments", "simulation/model pipelines", "research software")
        "csv", "tsv" -> listOf("spreadsheets", "databases", "research software", "data-export pipelines")
        "parquet", "arrow", "orc" -> listOf("analytics engines", "Python/R data stacks", "data-lake pipelines")
        "stata" -> listOf("Stata")
        "spss" -> listOf("IBM SPSS Statistics")
        "sas7bdat" -> listOf("SAS")
        "rds", "rdata" -> listOf("R / RStudio")
        "fasta", "fastq", "bam", "cram", "vcf", "bcf" -> listOf("sequencers", "bioinformatics pipelines", "genomics tools")
        "adif", "adx", "cabrillo" -> listOf("amateur-radio logging software", "contest loggers", "digital-mode applications")
        "sigmf", "sigmf-data", "iq" -> listOf("SDR receivers", "GNU Radio", "RF capture tools")
        "gpx", "nmea" -> listOf("GPS/GNSS receivers", "phones", "navigation/field apps")
        "kml", "kmz", "geojson", "geopackage", "shp" -> listOf("GIS software", "mapping/field-data systems")
        "pcap", "pcapng" -> listOf("Wireshark", "tcpdump", "network capture appliances")
        "elf" -> listOf("Linux/Android toolchains", "native compilers/linkers")
        "pe" -> listOf("Windows compilers/linkers", "software build systems")
        "macho" -> listOf("Apple/Xcode toolchains")
        "apk", "dex" -> listOf("Android build toolchains", "Android Studio/Gradle")
        "gcode" -> listOf("3D-printer slicers", "CAM software")
        "gerber", "excellon" -> listOf("PCB CAD/CAM software")
        "stl", "obj", "ply", "gltf", "glb" -> listOf("CAD/3D modelling software", "3D scanners")
        else -> emptyList()
    }

    fun contains(formatId: String): Boolean = entries.containsKey(formatId)
    fun ids(): Set<String> = entries.keys
}
