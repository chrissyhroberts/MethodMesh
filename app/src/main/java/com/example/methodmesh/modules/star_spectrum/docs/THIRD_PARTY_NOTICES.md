# Third-party notices — Star spectrum analyser

The module does not bundle an additional third-party runtime library or send images to an external service.

## Algorithmic references

The extraction design follows established astronomical optimal-extraction practice rather than a proprietary or project-specific formula.

- **Horne, K. (1986), "An optimal extraction algorithm for CCD spectroscopy", PASP 98, 609.** The MethodMesh implementation follows the standard variance-weighted spatial-profile extraction concept and iterative deviant-pixel rejection described by Horne.
- **Astropy `specreduce`** was used as an open-source implementation reference for modern trace fitting, two-sided background estimation, boxcar extraction and Horne/optimal extraction. `specreduce` is distributed under the Astropy 3-clause BSD-style licence. No Python/Astropy runtime is bundled in MethodMesh.
- **STScI `slitlessutils`** was reviewed for slitless-spectroscopy contamination handling and Horne-style extraction architecture. No STScI runtime code or instrument-specific HST calibration model is bundled.
- **`jrthorstensen/opextract`** was reviewed as a compact stellar-spectrum implementation of Horne extraction and iterative bad-pixel rejection.

The Kotlin implementation in this module is an independent implementation of the published algorithmic method and repository architecture patterns; it is not a line-for-line port of those projects.

The built-in Hα/Hβ/Hγ/Hδ wavelengths are standard physical reference values represented as small factual constants, not a copied third-party database.

No image or derived spectrum is sent to a remote provider.

### Automatic wavelength-calibration design

Version 0.2.22 adds an independent Kotlin implementation of a multi-hypothesis, RANSAC-like line-pattern calibration strategy. The design was informed conceptually by open astronomical wavelength-calibration work including **RASCAL (RANSAC Assisted Spectral CALibration)** (`https://github.com/jveitchmichaelis/rascal`). MethodMesh does not vendor, translate or execute RASCAL/Python source code; no new third-party runtime dependency is introduced. The current implementation searches the module's existing four factual Balmer reference wavelengths and uses the module's own chromatic bootstrap only as a weak prior.
