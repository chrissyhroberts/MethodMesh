# File Lab format catalogue

Offline descriptive catalogue bundled with File Lab v0.6. Detection remains content-first; this catalogue explains a detected format and never determines the detection itself.

| Category | Format ID | Plain-English description | Often opened with | File Lab support |
|---|---|---|---|---|
| 3D model | `glb` | A binary glTF 3D scene/model. | Blender, three.js viewers, Windows 3D Viewer | Recognition/basic inspection |
| 3D model | `gltf` | A glTF 3D scene/model, usually JSON plus external or embedded assets. | Blender, three.js viewers | Recognition/basic inspection |
| 3D model | `obj` | A Wavefront OBJ 3D model. | Blender, MeshLab, FreeCAD | Recognition/basic inspection |
| 3D model | `ply` | A Polygon File Format model or point cloud. | MeshLab, CloudCompare, Blender | Recognition/basic inspection |
| 3D model | `stl` | An STL file describing a 3D surface as triangles. | PrusaSlicer, Cura, MeshLab, FreeCAD | Recognition/basic inspection |
| Amateur radio | `adif` | An amateur-radio contact log stored in the ADIF interchange format. | Log4OM, N1MM Logger+, WSJT-X, fldigi | Inspect + text→PDF conversion |
| Amateur radio | `adx` | An amateur-radio contact log using the XML form of ADIF. | Log4OM, ADIF-compatible logging software | Inspect + text→PDF conversion |
| Amateur radio | `cabrillo` | A plain-text amateur-radio contest log in Cabrillo format. | N1MM Logger+, contest logging software, text editor | Inspect + text→PDF conversion |
| Android | `apk` | An Android application package. It contains an app manifest, compiled code, resources and signatures. | Android package installer, jadx, APKTool | Deep/container inspection |
| Android | `dex` | Compiled Android application bytecode. | jadx, baksmali, Android Studio | Recognition/basic inspection |
| Archive | `7z` | A compressed archive created with the 7-Zip format. It can contain many files and folders in one package. | 7-Zip, PeaZip | Recognition/basic inspection |
| Archive | `bzip2` | A file compressed with the BZip2 algorithm. | bzip2, 7-Zip, PeaZip | Recognition/basic inspection |
| Archive | `gzip` | A single data stream compressed with GZIP. | gzip, 7-Zip, PeaZip | Recognition/basic inspection |
| Archive | `rar` | A RAR compressed archive containing one or more files. | RAR, WinRAR, 7-Zip, PeaZip | Recognition/basic inspection |
| Archive | `tar` | A TAR archive combining many files into one sequential package. Compression, if any, is usually provided by an outer .gz/.xz/.bz2 layer. | tar, 7-Zip, PeaZip | Recognition/basic inspection |
| Archive | `xz` | A file compressed with the XZ/LZMA2 format. | xz, 7-Zip, PeaZip | Recognition/basic inspection |
| Archive | `zip` | A ZIP archive containing one or more files. File Lab also checks ZIP internals for more specific formats such as EPUB, APK, Office documents, XLSForms and CBZ. | 7-Zip, PeaZip, system archive tools | Deep/container inspection |
| Archive | `zstd` | A data stream compressed with the Zstandard algorithm. | zstd, 7-Zip, PeaZip | Recognition/basic inspection |
| Astronomy | `fits` | A scientific astronomy file containing images, spectra or tables plus detailed observation metadata. | SAOImage DS9, Astropy, fv, FITS Liberator | Structured inspection |
| Astronomy | `xisf` | An XISF astronomical image used especially by PixInsight workflows. | PixInsight | Recognition/basic inspection |
| Audio | `aac` | A compressed digital audio file using Advanced Audio Coding. | VLC, ffmpeg, Audacity | Recognition/basic inspection |
| Audio | `flac` | A losslessly compressed audio file. | VLC, Audacity, ffmpeg | Recognition/basic inspection |
| Audio | `m4a` | An MPEG-4 audio container, commonly carrying AAC or ALAC audio. | VLC, Apple Music, ffmpeg | Recognition/basic inspection |
| Audio | `mp3` | A compressed MP3 audio file. | VLC, Audacity, ffmpeg | Recognition/basic inspection |
| Audio | `wav` | A WAVE audio file containing sampled sound. | VLC, Audacity, ffmpeg | Structured inspection |
| Audio / media | `ogg` | An Ogg media container, commonly carrying Vorbis or Opus audio. | VLC, Audacity, ffmpeg | Recognition/basic inspection |
| Binary data | `binary` | A generic binary image whose internal format has not been identified more specifically. | hex editor, Ghidra, binwalk | Recognition/basic inspection |
| Biosignal | `edf` | A European Data Format biosignal recording. | EDFbrowser, MNE-Python, EEGLAB | Recognition/basic inspection |
| CAD | `dxf` | An AutoCAD Drawing Exchange Format file. | AutoCAD, LibreCAD, FreeCAD | Recognition/basic inspection |
| Calendar | `ics` | An iCalendar file containing calendar events, tasks or invitations. | Google Calendar, Outlook, Apple Calendar | Inspect + text→PDF conversion |
| Comic book | `cb7` | A comic-book archive whose page images are packed in a 7-Zip container. | KOReader, CDisplayEx, Perfect Viewer, 7-Zip | Recognition/basic inspection |
| Comic book | `cbr` | A comic-book archive whose page images are packed in a RAR container. | KOReader, CDisplayEx, Perfect Viewer, RAR | Recognition/basic inspection |
| Comic book | `cbt` | A comic-book archive whose page images are packed in a TAR container. | KOReader, comic readers, tar | Recognition/basic inspection |
| Comic book | `cbz` | A comic-book archive whose page images are packed in a ZIP container. | KOReader, CDisplayEx, Perfect Viewer, 7-Zip | Deep inspect + CBZ→PDF conversion |
| Configuration | `ini` | A plain-text configuration file organised into sections and key/value pairs. | text editor, VS Code, Notepad++ | Inspect + text→PDF conversion |
| Configuration | `toml` | A human-readable TOML configuration document. | text editor, VS Code | Inspect + text→PDF conversion |
| Configuration | `yaml` | A YAML document containing human-readable structured data. | text editor, VS Code | Inspect + text→PDF conversion |
| Contacts | `vcard` | A vCard contact file containing names, phone numbers, addresses and related contact fields. | Contacts apps, Outlook, Apple Contacts | Inspect + text→PDF conversion |
| Data analytics | `orc` | An Apache ORC columnar data file. | Apache Spark, Hive, Python/R ORC libraries | Recognition/basic inspection |
| Data analytics | `parquet` | An Apache Parquet columnar data file. | PyArrow, pandas, DuckDB, R arrow | Structured inspection |
| Data science | `arrow` | A columnar data file using Apache Arrow or Feather. | PyArrow, pandas, R arrow | Recognition/basic inspection |
| Data science | `npy` | A NumPy binary array file. | NumPy, Python, Julia/R import tools | Structured inspection |
| Data science | `npz` | A NumPy archive containing one or more NumPy arrays. | NumPy, Python, 7-Zip | Deep/container inspection |
| Database | `sql` | A text file containing SQL statements. | database clients, sqlite3, VS Code | Inspect + text→PDF conversion |
| Database | `sqlite` | A self-contained SQLite database stored in a single file. | DB Browser for SQLite, sqlite3, DBeaver | Recognition/basic inspection |
| Database / GIS | `dbf` | A dBASE table; in GIS it commonly stores the attribute table for a Shapefile. | QGIS, LibreOffice Calc, GDAL | Recognition/basic inspection |
| Disk image | `iso9660` | An optical-disc filesystem image, commonly representing a CD or DVD. | 7-Zip, disk-image tools, virtual drive software | Recognition/basic inspection |
| Document | `chm` | A Microsoft Compiled HTML Help file. | Windows HTML Help, SumatraPDF, 7-Zip | Recognition/basic inspection |
| Document | `djvu` | A document format designed for scanned pages and books at compact sizes. | DjView, SumatraPDF, KOReader | Recognition/basic inspection |
| Document | `docx` | A Microsoft Word document in the modern Office Open XML format. | Microsoft Word, LibreOffice Writer, Google Docs | Deep/container inspection |
| Document | `markdown` | A plain-text Markdown document using lightweight markup for headings, lists, links and code. | VS Code, Obsidian, Quarto, RStudio | Inspect + text→PDF conversion |
| Document | `pdf` | A Portable Document Format file designed to preserve page layout across devices. | Adobe Acrobat, Chrome, KOReader, SumatraPDF | Basic inspect + PDF→CBZ conversion |
| Document | `rtf` | A Rich Text Format document containing formatted text. | Microsoft Word, LibreOffice Writer, TextEdit | Recognition/basic inspection |
| Ebook | `azw3` | A Kindle ebook using Amazon's AZW3/KF8 format. | Kindle, KOReader, Calibre | Recognition/basic inspection |
| Ebook | `epub` | A reflowable ebook package. | KOReader, Calibre, Apple Books, Thorium Reader | Deep/container inspection |
| Ebook | `mobi` | A Mobipocket/older Kindle ebook. | KOReader, Calibre, Kindle | Recognition/basic inspection |
| Email | `eml` | A saved email message including headers, body and possibly attachments. | Thunderbird, Outlook, Apple Mail | Inspect + text→PDF conversion |
| Executable | `elf` | An ELF executable or shared library used by Linux, Android and many embedded systems. A .so file is usually a shared library loaded by another program rather than a document to open. | readelf, objdump, Ghidra, IDA | Structured inspection |
| Executable | `macho` | A Mach-O executable or library used by macOS, iOS and related Apple platforms. | otool, Hopper, Ghidra, IDA | Recognition/basic inspection |
| Executable | `pe` | A Windows executable or dynamic-link library. | dumpbin, Ghidra, IDA, PE-bear | Structured inspection |
| Executable | `wasm` | A WebAssembly binary module. | wasmtime, wasmer, browser developer tools | Recognition/basic inspection |
| Fabrication | `gcode` | Machine instructions telling a CNC machine or 3D printer how to move and operate. | PrusaSlicer, Cura, CNC controllers, text editor | Inspect + text→PDF conversion |
| Firmware | `intel-hex` | A text representation of binary memory addresses and bytes used for microcontroller firmware. | avrdude, OpenOCD tools, hex editors | Inspect + text→PDF conversion |
| Firmware | `uf2` | A UF2 firmware image designed for easy microcontroller flashing by copying a file to a bootloader drive. | UF2 bootloader drives, microcontroller toolchains | Recognition/basic inspection |
| Genomics | `bam` | A compressed binary file containing aligned DNA/RNA sequencing reads. | samtools, IGV, Picard | Recognition/basic inspection |
| Genomics | `bcf` | A binary form of Variant Call Format data. | bcftools, IGV | Recognition/basic inspection |
| Genomics | `cram` | A highly compressed genomic alignment file. | samtools, IGV | Recognition/basic inspection |
| Genomics | `fasta` | A text file containing biological sequences such as DNA, RNA or proteins. | samtools, seqkit, Geneious, text editor | Inspect + text→PDF conversion |
| Genomics | `fastq` | A sequencing-read file containing bases plus per-base quality scores. | FastQC, seqkit, bioinformatics pipelines | Inspect + text→PDF conversion |
| Genomics | `vcf` | A Variant Call Format file describing genetic variants and genotypes. | bcftools, IGV, vcftools | Inspect + text→PDF conversion |
| GIS | `geojson` | Geographic features encoded as JSON. | QGIS, geojson.io, GDAL | Inspect + text→PDF conversion |
| GIS | `geopackage` | A portable GIS database containing vector features, rasters or tiles. | QGIS, ArcGIS Pro, GDAL | Recognition/basic inspection |
| GIS | `kml` | A Keyhole Markup Language file describing geographic features and map annotations. | Google Earth, QGIS, GDAL | Inspect + text→PDF conversion |
| GIS | `kmz` | A compressed KML map package. | Google Earth, QGIS, 7-Zip | Deep/container inspection |
| GIS | `mbtiles` | An offline map-tile database in the MBTiles format. | QGIS, MapLibre tools, mb-util | Recognition/basic inspection |
| GIS | `prj` | A coordinate-reference-system description, often accompanying a Shapefile. | QGIS, ArcGIS, GDAL | Recognition/basic inspection |
| GIS | `shp` | The geometry component of an ESRI Shapefile dataset. It normally travels with .shx, .dbf and often .prj sidecar files. | QGIS, ArcGIS, GDAL | Recognition/basic inspection |
| GIS | `shx` | The index component of an ESRI Shapefile dataset. | QGIS, ArcGIS, GDAL | Recognition/basic inspection |
| Image | `avif` | A modern compressed still image using the AV1 image format. | modern photo viewers, ImageMagick, GIMP | Recognition/basic inspection |
| Image | `bmp` | A Windows bitmap image. | Windows Photos, GIMP, ImageMagick | Inspect + image→PDF conversion |
| Image | `gif` | A GIF image, possibly animated. | web browsers, GIMP, ImageMagick | Inspect + image→PDF conversion |
| Image | `heic` | A high-efficiency image, commonly produced by phones and Apple devices. | Apple Photos, modern photo viewers, ImageMagick | Recognition/basic inspection |
| Image | `ico` | A Windows icon file containing one or more small raster images. | Windows icon viewers, GIMP, ImageMagick | Recognition/basic inspection |
| Image | `jpeg` | A JPEG raster image, most commonly used for photographs. | photo viewers, GIMP, ImageMagick | Inspect + image→PDF conversion |
| Image | `png` | A losslessly compressed PNG raster image. | photo viewers, GIMP, ImageMagick | Inspect + image→PDF conversion |
| Image | `psd` | An Adobe Photoshop working document with layers and editing information. | Adobe Photoshop, Photopea, GIMP | Recognition/basic inspection |
| Image | `svg` | A scalable vector graphic described with XML. | web browsers, Inkscape, Illustrator | Recognition/basic inspection |
| Image | `tiff` | A TIFF raster image; some TIFF-family files are scientific imagery, GeoTIFF maps or camera DNG negatives. | ImageMagick, GIMP, Photoshop, QGIS for GeoTIFF | Recognition/basic inspection |
| Image | `webp` | A WebP still or animated image. | web browsers, GIMP, ImageMagick | Inspect + image→PDF conversion |
| Java | `jar` | A Java archive containing classes and resources. | Java runtime, 7-Zip, jadx | Deep/container inspection |
| Java | `java-class` | A compiled Java class file. | javap, CFR, IntelliJ IDEA | Recognition/basic inspection |
| Legacy Office | `ole` | A Microsoft Compound File document, often an older Word, Excel or PowerPoint file. | Microsoft Office, LibreOffice, 7-Zip | Recognition/basic inspection |
| Lidar | `las` | A lidar point-cloud file containing 3D coordinates and attributes. | CloudCompare, PDAL, QGIS | Recognition/basic inspection |
| Lidar | `laz` | A losslessly compressed lidar point-cloud file. | CloudCompare, PDAL, QGIS | Recognition/basic inspection |
| Medical / neuroimaging | `nifti` | A NIfTI neuroimaging volume. | FSL, 3D Slicer, MRIcroGL | Recognition/basic inspection |
| Medical imaging | `dicom` | A DICOM medical imaging or clinical data file. | RadiAnt, Horos, 3D Slicer, dcmtk | Structured inspection |
| Meteorology | `grib` | A compact weather-data file containing gridded forecasts or observations. | Panoply, wgrib2, ecCodes, QGIS | Recognition/basic inspection |
| Microscopy | `czi` | A Zeiss microscopy image/container. | ZEISS ZEN, Fiji/ImageJ, Bio-Formats | Recognition/basic inspection |
| Microscopy | `mrc` | A volumetric microscopy file, especially common in electron microscopy and cryo-EM. | IMOD, ChimeraX, RELION tools | Recognition/basic inspection |
| Music | `midi` | A MIDI sequence containing musical events rather than recorded sound. | DAWs, MuseScore, VLC | Recognition/basic inspection |
| Navigation | `nmea` | A text log of NMEA navigation sentences from GPS/GNSS or marine instruments. | GPSBabel, GPS tools, text editor | Inspect + text→PDF conversion |
| Navigation / GIS | `gpx` | A GPS Exchange Format file containing tracks, routes or waypoints. | QGIS, Garmin software, GPSBabel, GPX viewers | Inspect + text→PDF conversion |
| Network capture | `pcap` | A packet-capture file containing raw network packets. | Wireshark, tcpdump, tshark | Structured inspection |
| Network capture | `pcapng` | A modern packet-capture file that can store packets plus richer interface and metadata information. | Wireshark, tshark | Structured inspection |
| PCB | `excellon` | A PCB drill file describing hole locations and drill/tool sizes. | KiCad, Gerbv, CAM software | Recognition/basic inspection |
| PCB | `gerber` | A Gerber file describing one graphical layer of a printed circuit board. | KiCad, Gerbv, CAM software | Inspect + text→PDF conversion |
| Presentation | `pptx` | A Microsoft PowerPoint presentation in Office Open XML format. | Microsoft PowerPoint, LibreOffice Impress, Google Slides | Deep/container inspection |
| Research / ODK | `xform` | An XML XForm, commonly used as the compiled machine-readable form definition behind ODK/OpenRosa tools. | ODK Collect, JavaRosa/OpenRosa tooling, XML editor | Inspect + text→PDF conversion |
| Research / ODK | `xlsform` | An XLSForm: an Excel workbook whose survey/choices/settings sheets define a form for ODK, KoboToolbox and related tools. | ODK XLSForm Online, ODK Central, KoboToolbox, Excel/LibreOffice | Deep/container inspection |
| Scientific data | `hdf5` | A hierarchical scientific-data container able to store arrays, tables and metadata in one file. | HDFView, h5py, MATLAB, R | Structured inspection |
| Scientific data | `mat` | A MATLAB data file containing arrays, variables or workspace objects. | MATLAB, Python scipy/h5py, GNU Octave | Recognition/basic inspection |
| Scientific data | `netcdf` | A self-describing scientific dataset commonly used for gridded multidimensional data. | Panoply, xarray, ncdump, R | Structured inspection |
| Software-defined radio | `iq` | Raw in-phase/quadrature sample data from a radio receiver. The byte layout and sample rate often need to be known separately. | GNU Radio, Inspectrum, SDR++ | Recognition/basic inspection |
| Software-defined radio | `sigmf` | SigMF metadata describing a radio-frequency I/Q recording. It usually accompanies a .sigmf-data sample file. | GNU Radio, Inspectrum, SigMF tools | Structured inspection |
| Software-defined radio | `sigmf-data` | The binary sample payload of a SigMF radio recording. Its matching .sigmf-meta file explains sample type, rate and frequency. | GNU Radio, Inspectrum, SigMF tools | Recognition/basic inspection |
| Source code | `source` | A source-code text file. File Lab has recognised the extension as a programming-language source file rather than a generic text document. | VS Code, Android Studio, Vim, language-specific IDEs | Inspect + text→PDF conversion |
| Space / astronomy | `tle` | A Two-Line Element orbital data set describing an Earth-orbiting object. | GPredict, Skyfield, CelesTrak-compatible tools | Inspect + text→PDF conversion |
| Spreadsheet | `xlsx` | A Microsoft Excel workbook in Office Open XML format. | Microsoft Excel, LibreOffice Calc, Google Sheets | Deep/container inspection |
| Statistics | `rdata` | An R workspace file containing one or more serialized R objects. | R, RStudio | Recognition/basic inspection |
| Statistics | `rds` | A serialized single R object. | R, RStudio | Recognition/basic inspection |
| Statistics | `sas7bdat` | A SAS dataset. | SAS, Python pyreadstat, R haven | Recognition/basic inspection |
| Statistics | `spss` | An IBM SPSS dataset. | IBM SPSS Statistics, R haven, Python pyreadstat | Recognition/basic inspection |
| Statistics | `stata` | A Stata dataset (.dta). | Stata, R haven, Python pyreadstat | Recognition/basic inspection |
| Structured data | `json` | A JSON document containing structured objects, arrays and values. | text editor, jq, VS Code | Inspect + text→PDF conversion |
| Structured data | `ndjson` | A stream of JSON records with one JSON value per line. | jq, Python, text editor | Recognition/basic inspection |
| Structured data | `xml` | An XML document containing nested tagged data. | web browsers, VS Code, XML editors | Inspect + text→PDF conversion |
| Tabular data | `csv` | A plain-text table whose columns are separated by commas. | Excel, LibreOffice Calc, R, Python/pandas | Inspect + text→PDF conversion |
| Tabular data | `tsv` | A plain-text table whose columns are separated by tab characters. | Excel, LibreOffice Calc, R, Python/pandas | Inspect + text→PDF conversion |
| Text | `log` | A plain-text log recording events or diagnostic output from software or devices. | text editor, less, VS Code | Inspect + text→PDF conversion |
| Text | `txt` | A plain-text file with no embedded formatting. | text editor, VS Code, Notepad | Inspect + text→PDF conversion |
| Unknown | `unknown` | File Lab could not identify this file from its current content signatures or filename extension. | hex editor, file/TrID-style identification tools | Generic inspection only |
| Video | `avi` | A Microsoft AVI video container. | VLC, ffmpeg | Recognition/basic inspection |
| Video | `flv` | An Adobe Flash Video container. | VLC, ffmpeg | Recognition/basic inspection |
| Video | `mkv` | A Matroska multimedia container. | VLC, mpv, ffmpeg | Recognition/basic inspection |
| Video | `mov` | A QuickTime movie container. | QuickTime Player, VLC, ffmpeg | Recognition/basic inspection |
| Video | `mp4` | An MPEG-4/ISO Base Media container that may contain video, audio, subtitles and metadata. | VLC, mpv, ffmpeg | Recognition/basic inspection |
| Video | `webm` | A WebM multimedia container designed for web delivery. | VLC, web browsers, ffmpeg | Recognition/basic inspection |
| Web document | `html` | An HTML web page. | Chrome, Firefox, Safari, text editor | Recognition/basic inspection |
