# File Lab

File Lab is MethodMesh's offline file laboratory: identify unfamiliar files by content, inspect bounded metadata, fingerprint complete files, convert only through executable routes, and hand readable documents/comics to an installed reader without modifying the source.

**Module ID:** `filelab`
**Version:** `0.7.0`
**Maturity:** `DEVELOPMENT`
**Connectivity:** `OFFLINE`

## Canonical capabilities

| Capability | Method ID | Native UI | Presets | Protocols | ODK/XLSForm |
|---|---|---:|---:|---:|---:|
| Inspect file | `file.inspect` | yes | yes | yes | yes |
| Convert file | `file.convert` | yes | yes | yes | yes |

The dashboard is an aggregation surface only. Both methods remain independently discoverable through `FileLabModule.as100Methods()`, `capabilityScreens()` and `capabilitySettings()`.


## Offline format knowledge catalogue

Detection now resolves into an offline explanatory catalogue as well as a technical format ID. The catalogue covers **138 detected format identities** across consumer, scientific, research, engineering, radio, GIS, genomics, executable and archival families.

For a recognised file the inspector can show:

- a plain-English description of what the format is and why somebody would have one;
- the technical identity/container model;
- typical uses;
- common producer/toolchain hints where useful;
- **Often opened with** software suggestions from the bundled catalogue;
- **Open with on this phone** handlers queried from Android for the actual selected file;
- related formats;
- format-specific cautions;
- the exact level of support File Lab currently provides.

The catalogue is descriptive only. It never overrides content detection, and an unknown/proprietary format gets an explicit unknown-format explanation rather than invented semantics. Installed-app discovery is also kept separate from catalogue suggestions so File Lab does not imply that a suggested application is present on the device.

The complete bundled list is documented in `FORMAT_CATALOGUE.md`.

## Inspection coverage

File Lab now combines high-confidence content signatures with lower-confidence extension fallbacks so unfamiliar specialist files are named rather than collapsing into `application/octet-stream`.

Current recognition families include:

- archives/comics: ZIP, CBZ, RAR, CBR, 7-Zip, CB7, TAR, CBT, GZIP, BZip2, XZ, Zstandard and Java archives;
- documents/books: PDF, EPUB, MOBI/AZW/AZW3, DjVu, CHM, RTF, OOXML/legacy Office containers and plain/Markdown/HTML/XML/YAML/TOML/configuration text;
- images/media: PNG, JPEG, GIF, BMP, WebP, TIFF/DNG, HEIF/HEIC, AVIF, PSD, SVG, WAVE, FLAC, Ogg/Opus, MP3, AAC, MIDI, MP4/M4A/MOV, Matroska/WebM, AVI and FLV;
- scientific/research: FITS, XISF, HDF5, NetCDF, GRIB, Parquet, Arrow/Feather, ORC, NumPy NPY/NPZ, DICOM, EDF/BDF, NIfTI, MRC, CZI, MATLAB, Stata, SPSS, SAS and R serialized/workspace files;
- field/geospatial: GPX, KML/KMZ, GeoJSON, GeoPackage, MBTiles, Shapefile components, LAS/LAZ and coordinate-reference text;
- genomics: FASTA, FASTQ, VCF, BAM, CRAM and BCF;
- ham/SDR/navigation: ADIF/ADX, Cabrillo logs, SigMF metadata/data, raw I/Q extensions, TLE and NMEA logs;
- engineering/fabrication: G-code, Gerber, Excellon, STL, OBJ, PLY, glTF/GLB and DXF;
- software/network/firmware: ELF/shared objects, PE/DLL, Mach-O, DEX, WebAssembly, Java class files, PCAP/PCAPNG, Intel HEX, UF2, ISO images and generic binary firmware.

ZIP-family deep inspection additionally identifies EPUB, APK, DOCX, XLSX, structural **XLSForm**, PPTX, KMZ and image-dominant CBZ archives. ODK/OpenRosa XForms are recognised from XML namespaces.

Recognition does not imply a deep parser exists. Where File Lab only recognises a container (for example RAR/CBR or 7-Zip/CB7), the UI says so rather than pretending member enumeration/extraction has occurred.

## Conversion graph

Current executable routes are:

- CBZ → PDF;
- PDF → CBZ;
- common text-like formats → PDF, including TXT, Markdown, CSV/TSV, JSON/GeoJSON, XML/YAML/TOML, logs/source/SQL and several field/scientific text formats;
- common Android-decodable images → PDF: PNG, JPEG, WebP, BMP and first-frame GIF.

Every generated output is reopened and content-verified against the requested target format before it becomes a working result.

CBR is now correctly recognised, but CBR → PDF is **not** advertised because this standalone module does not bundle a RAR extraction engine.

## Output handling

v0.04 returned the selected source URI as a conversion output as well as the converted output URI. The shared result exporter could therefore offer/export the original input alongside the conversion. v0.5 removes the source URI from the `file.convert` return contract. The conversion surface also exposes explicit **Open**, **Save file**, **Share** and **Commit** actions for the generated artefact.

## Reader hand-off

Readable formats can be opened from the inspector. File Lab prefers KOReader (`org.koreader.launcher`) when installed and otherwise uses Android `ACTION_VIEW`. KOReader is not bundled or embedded.

## DRM

File Lab does not remove DRM, defeat access controls or decrypt protected books. EPUB encryption metadata is reported as a warning. Protected-resource handling is outside this module's contract.

## Privacy and persistence

Core work is local and offline. Source files are never modified. Working conversion files live under Android cache and are exposed through the existing MethodMesh `FileProvider` as `content://` URIs. Caller-owned systems such as ODK own returned study attachments.
