# File Lab roadmap

This file separates intended scope from implemented scope. Nothing here is an advertised conversion route unless it is present in `ConversionGraph`.

## Implemented in v0.5

- broad content-signature plus extension-fallback recognition, including RAR/CBR and 7-Zip/CB7;
- bounded ZIP-family/nested-container inspection;
- structural XLSForm and OpenRosa XForm recognition;
- specialist metadata for selected astronomy, radio, tabular, genomic, executable, packet-capture and scientific formats;
- KOReader-preferred reader hand-off;
- CBZ → PDF;
- PDF → CBZ;
- common text-like formats → PDF;
- common Android-decodable raster images → PDF;
- post-conversion signature verification;
- explicit Save/Share of converted output with the original source removed from the conversion return attachment set.

## Next deep inspectors

Priority is depth rather than merely adding another extension label:

1. FITS HDU inventory, image preview, histogram/stretch and richer WCS summary;
2. SigMF paired metadata/data inspection with bounded spectrum/waterfall preview;
3. ADIF/ADX/Cabrillo log statistics and validation;
4. GPX/KML/GeoJSON/GeoPackage track/layer summaries and map preview;
5. XLSForm validation/schema inspection plus Stata/SPSS/SAS/Parquet/Arrow schema browsing;
6. NetCDF/HDF5/GRIB metadata trees;
7. DICOM/EDF/FASTQ richer biomedical/genomic summaries;
8. STL/OBJ/Gerber/G-code previews.

## Conversion candidates

- CBR/RAR and CB7/7-Zip comic conversion, only after a suitable offline archive engine/dependency is reviewed;
- EPUB ↔ PDF and other DRM-free ebook conversions;
- PDF merge/split/reorder and PDF/image extraction;
- audio conversion among WAV/FLAC/MP3/AAC/Opus/OGG/M4A through a reviewed media engine;
- GPX/KML/GeoJSON conversions.

## Explicitly not in scope

DRM circumvention/decryption is not a File Lab route. Protected-resource detection may improve, but access-control bypass is not ordinary format conversion.
