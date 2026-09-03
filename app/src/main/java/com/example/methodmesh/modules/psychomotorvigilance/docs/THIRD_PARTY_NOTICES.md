# Third-party review and attribution

The MethodMesh PVT implementation in this module is independently written. No third-party source file has been copied into the module.

## AndroidPvt — Mel Arthurs

Repository: https://github.com/arthursmel/AndroidPvt  
Copyright: © 2021 Mel Arthurs  
Licence: MIT

AndroidPvt was reviewed as an Android-specific implementation precedent. Its MIT licence permits reuse subject to preservation of the copyright and licence notice. MethodMesh does **not** currently copy its source code; it therefore records attribution here as design provenance rather than incorporating the project as a dependency.

Concepts considered from the project include a state-driven PVT flow, random stimulus intervals, false-response handling and per-trial result capture. MethodMesh deliberately differs in important ways:

- it implements the published 10-minute PVT and 3-minute PVT-B configurations rather than AndroidPvt's demonstration defaults;
- it uses a monotonic Android timing base rather than `System.currentTimeMillis()` for reaction intervals;
- it retains MethodMesh audit/provenance outputs and ODK integration;
- it records an explicit device-calibration warning.

MIT licence text for the reviewed project:

> MIT License
>
> Copyright (c) 2021 Mel Arthurs
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

If MethodMesh later copies or adapts substantial AndroidPvt source, this notice must remain with the distributed software and the adapted files should say so explicitly.

## AuReTim — Straßer et al.

Software: https://github.com/strator1/AuReTim  
Article: https://doi.org/10.3389/frsle.2023.1168209  
Software licence: GPLv3

AuReTim was reviewed as evidence that an inexpensive/open PVT can be externally timing-characterised and as a useful calibration-method precedent. No AuReTim code has been copied or adapted into this module. This avoids accidentally introducing GPL-derived code without an explicit MethodMesh licensing decision.

## Scientific protocol sources

The task parameters and scoring conventions are derived from the cited PVT literature, not from AndroidPvt or AuReTim source code. Full references are in `README_PsychomotorVigilance.md`.
