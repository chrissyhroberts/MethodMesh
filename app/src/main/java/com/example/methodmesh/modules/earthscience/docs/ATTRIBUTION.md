# Attribution and external design references

## Data incorporated into the module

### International Chronostratigraphic Chart

`geotime.lookup` includes a small derived table of numerical interval boundaries from:

**International Commission on Stratigraphy, International Chronostratigraphic Chart, version 2026/06.**

- Chart information: https://stratigraphy.org/supplementary
- Authoritative chart-data repository: https://github.com/i-c-stratigraphy/chart
- Copyright: © International Commission on Stratigraphy, 2026
- Data licence: Creative Commons Attribution 4.0 (CC BY 4.0)
- Licence: https://creativecommons.org/licenses/by/4.0/

The method returns the source/version with every lookup. The module does not bundle the full ICS RDF knowledge graph.

## Standards/reference material used for independently implemented calculations

No source code from the projects below is copied into this module.

### USDA soil texture

The textural-class rule set is independently implemented from the standard USDA/NRCS sand-silt-clay texture classes.

- https://www.nrcs.usda.gov/resources/education-and-teaching-materials/soil-texture-calculator

### Wentworth grain-size scale

The grain-size boundaries and broad gravel/sand/silt/clay terminology follow the standard Wentworth scale as used by USGS.

- https://pubs.usgs.gov/of/2003/of03-001/htmldocs/nomenclature.htm

### WGS84 / UTM and geodesics

The module implements WGS84 ellipsoidal calculations directly in Kotlin. No GeographicLib or Proj4J code is vendored in v0.1. The following projects were reviewed as strong future dependency/reference candidates:

- GeographicLib-Java — high-quality geodesic library, MIT/X11: https://github.com/geographiclib/geographiclib-java
- LocationTech Proj4J — coordinate reference system transforms, Apache-2.0; EPSG data have separate licensing considerations: https://github.com/locationtech/proj4j

## Applications reviewed to identify field-science primitives

These projects/apps informed feature selection, not implementation copying:

- QField — field GIS, GNSS positioning/averaging/external receiver workflows: https://github.com/opengisch/QField
- StraboField / StraboSpot — structural-geology field observations and orientation measurements: https://github.com/StraboSpot/StraboField
- BasicAirData GPSLogger — lightweight track/GNSS logging and GPX/KML export: https://github.com/BasicAirData/GPSLogger
- mplstereonet — structural-geology stereonet mathematics/plotting reference for a future MethodMesh stereonet capability: https://github.com/joferkington/mplstereonet

These remain external references. Their licences must be checked before any future direct code reuse or dependency inclusion.
