# Field Statistics v0.1.0 — reference test vectors

These are deterministic outputs from `FieldStatsEngine.kt` used during authoring. Small differences in the last decimal after future numerical-library changes are acceptable; material differences require review.

| Calculation | Input | Expected / observed reference |
|---|---|---|
| Wilson proportion | `x=7, n=83, confidence=0.95` | estimate `0.08433735`; CI `0.04145334–0.16399529` |
| Sample size, proportion | `p=0.5, d=0.05, confidence=0.95` | base/final `385` |
| Sample size + FPC/inflation | previous + `N=1000`, design effect `1.5`, non-response `0.10` | base `385`; FPC `278`; design `417`; final `464` |
| Detect ≥1 | `p=0.02, C=0.95` | required `149`; achieved probability `0.95071835` |
| Zero events | `0/79`, confidence `0.95` | exact upper `0.03721068`; rule-of-three `0.03797468` |
| Diagnostic 2×2 | `TP=82, FP=10, FN=8, TN=100` | sensitivity `0.91111111`; specificity `0.90909091`; PPV `0.89130435`; NPV `0.92592593`; LR+ `10.02222222`; LR− `0.09777778`; DOR `102.5` |
| Predictive values at 2% prevalence | same diagnostic table, override `0.02` | PPV `0.16980422`; NPV `0.99800851` |
| Diagnostic undefined PPV edge | `TP=0, FP=0, FN=9, TN=91` | sensitivity `0`; specificity `1`; PPV undefined/blank; NPV `0.91`; method still succeeds |
| Compare binary groups | A `7/100`, B `13/100` | RD `−0.06`; RR `0.53846154`; OR `0.50372208`; NNT point `16.6667`, CI includes infinity |
| Descriptive statistics | `12,14,17,11,29,13` | mean `16`; sample SD `6.69328021`; median `13.5`; Q1 `12.25`; Q3 `16.25`; IQR `4` |
| Two-proportion sample size | `pA=.10, pB=.15, 95%, 80% power, 1:1` | `686` per group; total `1372` |
| Diagnostic sample size | sens `.90±.05`, spec `.95±.03`, prevalence `.10`, 95%, 5% unusable | positive `139`; negative `203`; final recruitment `1464` |
| ROC perfect separation | positive scores `.9,.8`; negative `.4,.1`; threshold `.5` | AUC `1`; sensitivity/spec specificity `1`; optimal threshold `.8`; Youden J `1` |
| Binomial | `n=20, p=.08, P(X≥1)` | `0.81130667` |
| Poisson | `λ=3.2, P(X≤2)` | `0.37990374` |
| Normal | standard normal `P(X≤1.96)` | `0.97500217` |
