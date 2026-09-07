package com.example.methodmesh.modules.clinicalinstruments

/** Built-in definitions are immutable. Users may duplicate them into the local library. */
object ClinicalInstrumentCoreDefinitions {
    const val QSOFA = """
schema: methodmesh.clinical-instrument.v1
id: qsofa
name: qSOFA
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [sepsis, deterioration, bedside]
summary: Three bedside criteria used in adults with suspected infection to prompt assessment for organ dysfunction.
source_url: https://jamanetwork.com/journals/jama/fullarticle/2492881
citation: "Singer M, Deutschman CS, Seymour CW, et al. The Third International Consensus Definitions for Sepsis and Septic Shock (Sepsis-3). JAMA. 2016;315(8):801-810."
rights_status: factual_definition
rights_note: Thresholds and scoring are implemented from the cited clinical definition; MethodMesh does not reproduce journal prose beyond short labels.
questions:
  - id: respiratory_rate
    label: Respiratory rate
    hint: Count breaths per minute.
    type: integer
    unit: breaths/min
    required: true
    min: 0
    max: 100
  - id: systolic_bp
    label: Systolic blood pressure
    type: integer
    unit: mmHg
    required: true
    min: 20
    max: 300
  - id: altered_mentation
    label: Altered mentation?
    hint: Sepsis-3 operationalised this as Glasgow Coma Scale less than 15.
    type: boolean
    required: true
derived:
  - id: rr_criterion
    expression: respiratory_rate >= 22
  - id: sbp_criterion
    expression: systolic_bp <= 100
  - id: mentation_criterion
    expression: altered_mentation == true
scores:
  - id: qsofa
    label: qSOFA
    expression: rr_criterion + sbp_criterion + mentation_criterion
classifications:
  - when: qsofa >= 2
    value: two_or_more_criteria
    label: 2 or more qSOFA criteria
  - when: qsofa < 2
    value: fewer_than_two_criteria
    label: Fewer than 2 qSOFA criteria
tests:
  - name: all_negative
    input_json: '{"respiratory_rate":18,"systolic_bp":120,"altered_mentation":false}'
    expect_json: '{"qsofa":0,"classification":"fewer_than_two_criteria"}'
  - name: exact_thresholds
    input_json: '{"respiratory_rate":22,"systolic_bp":100,"altered_mentation":true}'
    expect_json: '{"qsofa":3,"classification":"two_or_more_criteria"}'
"""

    const val CRB65 = """
schema: methodmesh.clinical-instrument.v1
id: crb65
name: CRB-65
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [pneumonia, respiratory, mortality_risk, primary_care]
summary: Mortality-risk score for adults with community-acquired pneumonia in primary care.
source_url: https://www.nice.org.uk/guidance/NG250/chapter/recommendations
citation: "NICE NG250 Pneumonia: diagnosis and management, recommendation 1.2 and CRB65 box. Updated 2025."
rights_status: factual_definition
rights_note: MethodMesh implements the numerical criteria and cites NICE; clinical use requires judgement alongside the score.
questions:
  - id: confusion
    label: New confusion or disorientation?
    hint: NICE includes abbreviated Mental Test score 8 or less, or new disorientation in person, place or time.
    type: boolean
  - id: respiratory_rate
    label: Respiratory rate
    type: integer
    unit: breaths/min
    min: 0
    max: 100
  - id: systolic_bp
    label: Systolic blood pressure
    type: integer
    unit: mmHg
    min: 20
    max: 300
  - id: diastolic_bp
    label: Diastolic blood pressure
    type: integer
    unit: mmHg
    min: 10
    max: 200
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 18
    max: 130
derived:
  - id: confusion_criterion
    expression: confusion == true
  - id: rr_criterion
    expression: respiratory_rate >= 30
  - id: bp_criterion
    expression: systolic_bp < 90 or diastolic_bp <= 60
  - id: age_criterion
    expression: age_years >= 65
scores:
  - id: crb65
    label: CRB-65
    expression: confusion_criterion + rr_criterion + bp_criterion + age_criterion
classifications:
  - when: crb65 == 0
    value: low_risk
    label: Low mortality risk
  - when: crb65 >= 1 and crb65 <= 2
    value: intermediate_risk
    label: Intermediate mortality risk
  - when: crb65 >= 3
    value: high_risk
    label: High mortality risk
tests:
  - name: zero
    input_json: '{"confusion":false,"respiratory_rate":20,"systolic_bp":120,"diastolic_bp":80,"age_years":50}'
    expect_json: '{"crb65":0,"classification":"low_risk"}'
  - name: four
    input_json: '{"confusion":true,"respiratory_rate":30,"systolic_bp":89,"diastolic_bp":60,"age_years":65}'
    expect_json: '{"crb65":4,"classification":"high_risk"}'
"""

    const val AVPU = """
schema: methodmesh.clinical-instrument.v1
id: avpu
name: AVPU consciousness scale
version: 1.0.0
status: core
type: structured_assessment
category: acute_bedside
tags: [neurology, consciousness, triage]
summary: Rapid categorical assessment of responsiveness.
source_url: https://www.resus.org.uk/library/abcde-approach
citation: "Resuscitation Council UK. The ABCDE Approach."
rights_status: factual_definition
rights_note: Implements the conventional AVPU categories without reproducing extended copyrighted teaching material.
questions:
  - id: avpu
    label: Level of responsiveness
    type: select_one
    choices: [alert|Alert, voice|Responds to voice, pain|Responds to pain, unresponsive|Unresponsive]
classifications:
  - when: avpu == 'alert'
    value: alert
    label: Alert
  - when: avpu == 'voice'
    value: voice
    label: Responds to voice
  - when: avpu == 'pain'
    value: pain
    label: Responds to pain
  - when: avpu == 'unresponsive'
    value: unresponsive
    label: Unresponsive
tests:
  - name: unresponsive
    input_json: '{"avpu":"unresponsive"}'
    expect_json: '{"classification":"unresponsive"}'
"""

    const val ADULT_BMI = """
schema: methodmesh.clinical-instrument.v1
id: adult_bmi
name: Adult BMI classification
version: 1.0.0
status: core
type: clinical_score
category: nutrition
tags: [nutrition, anthropometry, bmi]
summary: Calculates adult body-mass index from measured weight and height and returns standard WHO category thresholds.
source_url: https://www.who.int/data/gho/data/themes/topics/indicator-groups/indicator-group-details/GHO/bmi-among-adults
citation: "World Health Organization. Body mass index (BMI) classification for adults."
rights_status: factual_definition
rights_note: Formula and numerical thresholds are factual clinical definitions; cite WHO.
questions:
  - id: weight_kg
    label: Weight
    type: decimal
    unit: kg
    min: 1
    max: 500
  - id: height_cm
    label: Height
    type: decimal
    unit: cm
    min: 50
    max: 250
derived:
  - id: height_m
    expression: height_cm / 100
scores:
  - id: bmi
    label: BMI
    expression: weight_kg / (height_m * height_m)
classifications:
  - when: bmi < 18.5
    value: underweight
    label: Underweight
  - when: bmi >= 18.5 and bmi < 25
    value: normal_range
    label: Normal range
  - when: bmi >= 25 and bmi < 30
    value: overweight
    label: Overweight
  - when: bmi >= 30
    value: obesity
    label: Obesity
tests:
  - name: normal
    input_json: '{"weight_kg":70,"height_cm":175}'
    expect_json: '{"classification":"normal_range"}'
"""


    const val CURB65 = """
schema: methodmesh.clinical-instrument.v1
id: curb65
name: CURB-65
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [pneumonia, respiratory, mortality_risk, hospital]
summary: Five-item mortality-risk score for adults with community-acquired pneumonia assessed in hospital.
source_url: https://www.nice.org.uk/guidance/NG250/chapter/recommendations
citation: "NICE NG250. Pneumonia: diagnosis and management. CURB65 score, recommendation 1.2.8."
rights_status: factual_definition
rights_note: Numerical criteria and score bands are implemented from NICE; use clinical judgement alongside the score.
questions:
  - id: confusion
    label: New confusion or disorientation?
    type: boolean
  - id: urea_mmol_l
    label: Blood urea
    type: decimal
    unit: mmol/L
    min: 0
    max: 100
  - id: respiratory_rate
    label: Respiratory rate
    type: integer
    unit: breaths/min
    min: 0
    max: 100
  - id: systolic_bp
    label: Systolic blood pressure
    type: integer
    unit: mmHg
    min: 20
    max: 300
  - id: diastolic_bp
    label: Diastolic blood pressure
    type: integer
    unit: mmHg
    min: 10
    max: 200
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 18
    max: 130
derived:
  - id: confusion_criterion
    expression: confusion == true
  - id: urea_criterion
    expression: urea_mmol_l > 7
  - id: rr_criterion
    expression: respiratory_rate >= 30
  - id: bp_criterion
    expression: systolic_bp < 90 or diastolic_bp <= 60
  - id: age_criterion
    expression: age_years >= 65
scores:
  - id: curb65
    label: CURB-65
    expression: confusion_criterion + urea_criterion + rr_criterion + bp_criterion + age_criterion
classifications:
  - when: curb65 <= 1
    value: low_risk
    label: Low mortality risk
  - when: curb65 == 2
    value: intermediate_risk
    label: Intermediate mortality risk
  - when: curb65 >= 3
    value: high_risk
    label: High mortality risk
tests:
  - name: zero
    input_json: '{"confusion":false,"urea_mmol_l":5,"respiratory_rate":20,"systolic_bp":120,"diastolic_bp":80,"age_years":50}'
    expect_json: '{"curb65":0,"classification":"low_risk"}'
  - name: five
    input_json: '{"confusion":true,"urea_mmol_l":8,"respiratory_rate":30,"systolic_bp":89,"diastolic_bp":60,"age_years":65}'
    expect_json: '{"curb65":5,"classification":"high_risk"}'
"""

    const val FOUR_AT = """
schema: methodmesh.clinical-instrument.v1
id: four_at
name: 4AT delirium assessment
version: 1.2.0
status: core
type: clinical_score
category: acute_bedside
tags: [delirium, cognition, geriatrics, bedside]
summary: Rapid four-item assessment for possible delirium and cognitive impairment.
source_url: https://www.the4at.com/
citation: "4AT version 1.2. MacLullich A, Ryan T, Cash H. © 2011-2014."
rights_status: CC_BY_4_0
rights_note: "4AT is licensed CC BY 4.0. This definition preserves the official item scoring and identifies MethodMesh as an electronic implementation."
questions:
  - id: alertness
    label: Alertness
    hint: Rate current alertness by observation.
    type: select_one
    choices: [0|Normal or brief mild sleepiness after waking, 4|Clearly abnormal alertness]
  - id: amt4
    label: AMT4 orientation
    hint: Age, date of birth, place, current year.
    type: select_one
    choices: [0|No mistakes, 1|One mistake, 2|Two or more mistakes or untestable]
  - id: attention
    label: Attention - months backwards
    hint: Ask for months of the year backwards starting at December.
    type: select_one
    choices: [0|Seven or more months correct, 1|Starts but fewer than seven or refuses, 2|Untestable]
  - id: acute_change
    label: Acute change or fluctuating course
    hint: Significant change or fluctuation arising within the last 2 weeks and still evident in the last 24 hours.
    type: select_one
    choices: [0|No, 4|Yes]
scores:
  - id: four_at
    label: 4AT
    expression: alertness + amt4 + attention + acute_change
classifications:
  - when: four_at == 0
    value: unlikely
    label: Delirium or severe cognitive impairment unlikely; clinical context still applies
  - when: four_at >= 1 and four_at <= 3
    value: possible_cognitive_impairment
    label: Possible cognitive impairment
  - when: four_at >= 4
    value: possible_delirium
    label: Possible delirium +/- cognitive impairment
tests:
  - name: zero
    input_json: '{"alertness":"0","amt4":"0","attention":"0","acute_change":"0"}'
    expect_json: '{"four_at":0,"classification":"unlikely"}'
  - name: positive
    input_json: '{"alertness":"0","amt4":"1","attention":"1","acute_change":"4"}'
    expect_json: '{"four_at":6,"classification":"possible_delirium"}'
"""

    const val PERC = """
schema: methodmesh.clinical-instrument.v1
id: perc
name: PERC rule
version: 1.0.0
status: core
type: decision_rule
category: acute_bedside
tags: [pulmonary_embolism, vte, emergency]
summary: Eight-item pulmonary embolism rule-out criteria for patients already assessed as low pre-test probability.
source_url: https://pubmed.ncbi.nlm.nih.gov/18318689/
citation: "Kline JA, Courtney DM, Kabrhel C, et al. Prospective multicenter evaluation of the pulmonary embolism rule-out criteria. J Thromb Haemost. 2008."
rights_status: factual_definition
rights_note: Implements the published factual criteria. PERC is only intended after a clinician has established low pre-test probability.
questions:
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 18
    max: 130
  - id: pulse_bpm
    label: Pulse
    type: integer
    unit: beats/min
    min: 20
    max: 250
  - id: oxygen_saturation_percent
    label: Oxygen saturation
    type: decimal
    unit: percent
    min: 0
    max: 100
  - id: hemoptysis
    label: Hemoptysis?
    type: boolean
  - id: estrogen_use
    label: Estrogen use?
    type: boolean
  - id: recent_surgery_trauma
    label: Surgery or trauma requiring hospitalisation within 4 weeks?
    type: boolean
  - id: prior_vte
    label: Previous DVT or pulmonary embolism?
    type: boolean
  - id: unilateral_leg_swelling
    label: Unilateral leg swelling?
    type: boolean
derived:
  - id: age_positive
    expression: age_years >= 50
  - id: pulse_positive
    expression: pulse_bpm >= 100
  - id: saturation_positive
    expression: oxygen_saturation_percent < 95
scores:
  - id: perc_positive_criteria
    label: PERC positive criteria
    expression: age_positive + pulse_positive + saturation_positive + hemoptysis + estrogen_use + recent_surgery_trauma + prior_vte + unilateral_leg_swelling
classifications:
  - when: perc_positive_criteria == 0
    value: perc_negative
    label: PERC negative - all eight criteria absent; valid only in low-risk patients
  - when: perc_positive_criteria >= 1
    value: perc_positive
    label: One or more PERC criteria present
tests:
  - name: all_negative
    input_json: '{"age_years":40,"pulse_bpm":80,"oxygen_saturation_percent":98,"hemoptysis":false,"estrogen_use":false,"recent_surgery_trauma":false,"prior_vte":false,"unilateral_leg_swelling":false}'
    expect_json: '{"perc_positive_criteria":0,"classification":"perc_negative"}'
"""

    const val WELLS_PE = """
schema: methodmesh.clinical-instrument.v1
id: wells_pe_2level
name: Wells score - pulmonary embolism (2-level)
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [pulmonary_embolism, vte, emergency]
summary: Two-level Wells clinical probability score for suspected pulmonary embolism.
source_url: https://www.nice.org.uk/guidance/ng158/chapter/Recommendations
citation: "NICE NG158. Venous thromboembolic diseases: diagnosis, management and thrombophilia testing. Two-level PE Wells score."
rights_status: factual_definition
rights_note: Numerical criteria and weights are implemented from the cited clinical prediction rule.
questions:
  - id: clinical_signs_dvt
    label: Clinical signs and symptoms of DVT?
    type: boolean
  - id: pe_more_likely
    label: Is pulmonary embolism judged more likely than an alternative diagnosis?
    type: boolean
  - id: heart_rate
    label: Heart rate
    type: integer
    unit: beats/min
    min: 20
    max: 250
  - id: immobilisation_or_surgery_4w
    label: Immobilisation for at least 3 days or surgery in previous 4 weeks?
    type: boolean
  - id: previous_dvt_pe
    label: Previous DVT or pulmonary embolism?
    type: boolean
  - id: hemoptysis
    label: Hemoptysis?
    type: boolean
  - id: malignancy
    label: Malignancy with treatment within 6 months or palliative treatment?
    type: boolean
derived:
  - id: tachycardia_points
    expression: (heart_rate > 100) * 1.5
scores:
  - id: wells_pe
    label: Wells PE score
    expression: clinical_signs_dvt * 3 + pe_more_likely * 3 + tachycardia_points + immobilisation_or_surgery_4w * 1.5 + previous_dvt_pe * 1.5 + hemoptysis + malignancy
classifications:
  - when: wells_pe <= 4
    value: pe_unlikely
    label: PE unlikely
  - when: wells_pe > 4
    value: pe_likely
    label: PE likely
tests:
  - name: zero
    input_json: '{"clinical_signs_dvt":false,"pe_more_likely":false,"heart_rate":80,"immobilisation_or_surgery_4w":false,"previous_dvt_pe":false,"hemoptysis":false,"malignancy":false}'
    expect_json: '{"wells_pe":0,"classification":"pe_unlikely"}'
"""

    const val WELLS_DVT = """
schema: methodmesh.clinical-instrument.v1
id: wells_dvt_2level
name: Wells score - DVT (2-level)
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [dvt, vte, emergency]
summary: Two-level Wells clinical probability score for suspected deep-vein thrombosis.
source_url: https://www.nice.org.uk/guidance/ng158/chapter/Recommendations
citation: "NICE NG158. Venous thromboembolic diseases: diagnosis, management and thrombophilia testing. Two-level DVT Wells score."
rights_status: factual_definition
rights_note: Numerical criteria and weights are implemented from NICE's published two-level Wells table.
questions:
  - id: active_cancer
    label: Active cancer - treatment ongoing, within 6 months, or palliative?
    type: boolean
  - id: paralysis_immobilisation
    label: Paralysis, paresis or recent plaster immobilisation of the lower extremity?
    type: boolean
  - id: bedridden_or_major_surgery
    label: Bedridden at least 3 days or major surgery within 12 weeks under general/regional anaesthesia?
    type: boolean
  - id: deep_venous_tenderness
    label: Localised tenderness along the deep venous system?
    type: boolean
  - id: entire_leg_swollen
    label: Entire leg swollen?
    type: boolean
  - id: calf_swelling_3cm
    label: Calf swelling at least 3 cm greater than the asymptomatic side?
    type: boolean
  - id: pitting_edema
    label: Pitting oedema confined to symptomatic leg?
    type: boolean
  - id: collateral_veins
    label: Collateral superficial non-varicose veins?
    type: boolean
  - id: previous_dvt
    label: Previously documented DVT?
    type: boolean
  - id: alternative_diagnosis
    label: Alternative diagnosis at least as likely as DVT?
    type: boolean
scores:
  - id: wells_dvt
    label: Wells DVT score
    expression: active_cancer + paralysis_immobilisation + bedridden_or_major_surgery + deep_venous_tenderness + entire_leg_swollen + calf_swelling_3cm + pitting_edema + collateral_veins + previous_dvt - alternative_diagnosis * 2
classifications:
  - when: wells_dvt >= 2
    value: dvt_likely
    label: DVT likely
  - when: wells_dvt <= 1
    value: dvt_unlikely
    label: DVT unlikely
tests:
  - name: likely
    input_json: '{"active_cancer":true,"paralysis_immobilisation":false,"bedridden_or_major_surgery":true,"deep_venous_tenderness":false,"entire_leg_swollen":false,"calf_swelling_3cm":false,"pitting_edema":false,"collateral_veins":false,"previous_dvt":false,"alternative_diagnosis":false}'
    expect_json: '{"wells_dvt":2,"classification":"dvt_likely"}'
"""

    const val CHA2DS2_VASC = """
schema: methodmesh.clinical-instrument.v1
id: cha2ds2_vasc
name: CHA2DS2-VASc
version: 1.0.0
status: core
type: clinical_score
category: cardiovascular
tags: [atrial_fibrillation, stroke_risk, cardiovascular]
summary: Point score for thromboembolic risk factors in atrial fibrillation.
source_url: https://www.escardio.org/static-file/Escardio/Guidelines/ehw128_Addenda.pdf
citation: "European Society of Cardiology. CHA2DS2-VASc stroke-risk factors for atrial fibrillation."
rights_status: factual_definition
rights_note: MethodMesh returns the score and component values; anticoagulation decisions require the current applicable guideline and clinical context.
questions:
  - id: heart_failure
    label: Congestive heart failure or left ventricular dysfunction?
    type: boolean
  - id: hypertension
    label: Hypertension?
    type: boolean
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 18
    max: 130
  - id: diabetes
    label: Diabetes mellitus?
    type: boolean
  - id: stroke_tia_thromboembolism
    label: Previous stroke, TIA or systemic thromboembolism?
    type: boolean
  - id: vascular_disease
    label: Vascular disease - prior MI, peripheral arterial disease, or aortic plaque?
    type: boolean
  - id: sex_category
    label: Sex category
    type: select_one
    choices: [female|Female, male|Male, other|Other / not represented by score]
derived:
  - id: age_75_points
    expression: (age_years >= 75) * 2
  - id: age_65_74_points
    expression: (age_years >= 65 and age_years < 75)
  - id: stroke_points
    expression: stroke_tia_thromboembolism * 2
  - id: sex_points
    expression: sex_category == 'female'
scores:
  - id: cha2ds2_vasc
    label: CHA2DS2-VASc
    expression: heart_failure + hypertension + age_75_points + diabetes + stroke_points + vascular_disease + age_65_74_points + sex_points
tests:
  - name: example
    input_json: '{"heart_failure":true,"hypertension":true,"age_years":76,"diabetes":true,"stroke_tia_thromboembolism":false,"vascular_disease":false,"sex_category":"female"}'
    expect_json: '{"cha2ds2_vasc":6}'
"""

    const val HAS_BLED = """
schema: methodmesh.clinical-instrument.v1
id: has_bled
name: HAS-BLED
version: 1.0.0
status: core
type: clinical_score
category: cardiovascular
tags: [atrial_fibrillation, bleeding_risk, anticoagulation]
summary: Bleeding-risk factor score used in atrial fibrillation.
source_url: https://www.escardio.org/static-file/Escardio/Guidelines/Documents/ehaa612.pdf
citation: "European Society of Cardiology. 2020 AF guideline, HAS-BLED clinical risk factors."
rights_status: factual_definition
rights_note: Score is for identifying bleeding-risk factors requiring review and follow-up; it is not a standalone instruction to withhold anticoagulation.
questions:
  - id: systolic_bp
    label: Systolic blood pressure
    type: integer
    unit: mmHg
    min: 20
    max: 300
  - id: abnormal_renal
    label: Abnormal renal function?
    type: boolean
  - id: abnormal_liver
    label: Abnormal liver function?
    type: boolean
  - id: prior_stroke
    label: Previous stroke?
    type: boolean
  - id: bleeding_history
    label: Major bleeding history or bleeding predisposition?
    type: boolean
  - id: labile_inr
    label: Labile INR while receiving a vitamin K antagonist?
    type: boolean
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 18
    max: 130
  - id: drugs_predispose_bleeding
    label: Concomitant antiplatelet or NSAID use?
    type: boolean
  - id: excessive_alcohol
    label: Excessive alcohol use?
    type: boolean
derived:
  - id: hypertension_point
    expression: systolic_bp > 160
  - id: elderly_point
    expression: age_years > 65
scores:
  - id: has_bled
    label: HAS-BLED
    expression: hypertension_point + abnormal_renal + abnormal_liver + prior_stroke + bleeding_history + labile_inr + elderly_point + drugs_predispose_bleeding + excessive_alcohol
classifications:
  - when: has_bled >= 3
    value: high_risk_flag
    label: Score 3 or more - increased bleeding risk; review modifiable factors and follow closely
  - when: has_bled < 3
    value: below_high_risk_threshold
    label: Score below 3
tests:
  - name: high
    input_json: '{"systolic_bp":170,"abnormal_renal":true,"abnormal_liver":false,"prior_stroke":false,"bleeding_history":false,"labile_inr":false,"age_years":70,"drugs_predispose_bleeding":false,"excessive_alcohol":false}'
    expect_json: '{"has_bled":3,"classification":"high_risk_flag"}'
"""

    const val MODIFIED_CENTOR = """
schema: methodmesh.clinical-instrument.v1
id: modified_centor
name: Modified Centor (McIsaac) score
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [pharyngitis, strep, primary_care]
summary: Modified Centor point score for clinical features associated with group A streptococcal pharyngitis.
source_url: https://www.aafp.org/pubs/afp/issues/2022/1200/antibiotics-upper-respiratory-tract-infections.pdf
citation: "Modified Centor (McIsaac) criteria as summarized by American Family Physician."
rights_status: factual_definition
rights_note: Returns the score only; testing and treatment decisions should follow the current local guideline.
questions:
  - id: fever_38
    label: Fever at least 38 C?
    type: boolean
  - id: tonsillar_exudate_swelling
    label: Tonsillar exudate or swelling?
    type: boolean
  - id: tender_anterior_cervical_nodes
    label: Tender anterior cervical lymphadenopathy?
    type: boolean
  - id: cough_absent
    label: Cough absent?
    type: boolean
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 3
    max: 130
derived:
  - id: age_points_young
    expression: age_years >= 3 and age_years <= 14
  - id: age_points_old
    expression: (age_years > 45) * -1
scores:
  - id: modified_centor
    label: Modified Centor score
    expression: fever_38 + tonsillar_exudate_swelling + tender_anterior_cervical_nodes + cough_absent + age_points_young + age_points_old
tests:
  - name: all_adult
    input_json: '{"fever_38":true,"tonsillar_exudate_swelling":true,"tender_anterior_cervical_nodes":true,"cough_absent":true,"age_years":30}'
    expect_json: '{"modified_centor":4}'
"""

    const val SIRS = """
schema: methodmesh.clinical-instrument.v1
id: sirs
name: SIRS criteria
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [systemic_inflammation, infection, acute]
summary: Count of the four classic systemic inflammatory response syndrome criteria.
source_url: https://pubmed.ncbi.nlm.nih.gov/1303622/
citation: "Bone RC, Balk RA, Cerra FB, et al. ACCP/SCCM Consensus Conference definitions. Chest. 1992."
rights_status: factual_definition
rights_note: Implements physiological thresholds from the consensus definition. SIRS is not synonymous with sepsis.
questions:
  - id: temperature_c
    label: Temperature
    type: decimal
    unit: C
    min: 20
    max: 45
  - id: heart_rate
    label: Heart rate
    type: integer
    unit: beats/min
    min: 20
    max: 250
  - id: respiratory_rate
    label: Respiratory rate
    type: integer
    unit: breaths/min
    min: 0
    max: 100
  - id: paco2_mmhg
    label: PaCO2 if available
    type: decimal
    unit: mmHg
    required: false
    min: 5
    max: 100
  - id: wbc_10e9_l
    label: White blood cell count
    type: decimal
    unit: x10^9/L
    min: 0
    max: 100
  - id: bands_percent
    label: Immature band forms if available
    type: decimal
    unit: percent
    required: false
    min: 0
    max: 100
derived:
  - id: temperature_criterion
    expression: temperature_c > 38 or temperature_c < 36
  - id: heart_rate_criterion
    expression: heart_rate > 90
  - id: respiratory_criterion
    expression: respiratory_rate > 20 or paco2_mmhg < 32
  - id: wbc_criterion
    expression: wbc_10e9_l > 12 or wbc_10e9_l < 4 or bands_percent > 10
scores:
  - id: sirs
    label: SIRS criteria present
    expression: temperature_criterion + heart_rate_criterion + respiratory_criterion + wbc_criterion
classifications:
  - when: sirs >= 2
    value: two_or_more
    label: Two or more SIRS criteria
  - when: sirs < 2
    value: fewer_than_two
    label: Fewer than two SIRS criteria
tests:
  - name: two
    input_json: '{"temperature_c":39,"heart_rate":100,"respiratory_rate":18,"wbc_10e9_l":8}'
    expect_json: '{"sirs":2,"classification":"two_or_more"}'
"""

    const val SHOCK_INDEX = """
schema: methodmesh.clinical-instrument.v1
id: shock_index
name: Shock Index
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [hemodynamics, shock, vital_signs]
summary: Calculates heart rate divided by systolic blood pressure.
source_url: https://pubmed.ncbi.nlm.nih.gov/?term=shock+index+heart+rate+systolic+blood+pressure
citation: "Shock Index: heart rate divided by systolic arterial pressure."
rights_status: factual_formula
rights_note: MethodMesh returns the calculated index without imposing a universal clinical threshold.
questions:
  - id: heart_rate
    label: Heart rate
    type: integer
    unit: beats/min
    min: 20
    max: 250
  - id: systolic_bp
    label: Systolic blood pressure
    type: integer
    unit: mmHg
    min: 20
    max: 300
scores:
  - id: shock_index
    label: Shock Index
    expression: heart_rate / systolic_bp
tests:
  - name: example
    input_json: '{"heart_rate":100,"systolic_bp":100}'
    expect_json: '{"shock_index":1}'
"""

    const val MODIFIED_SHOCK_INDEX = """
schema: methodmesh.clinical-instrument.v1
id: modified_shock_index
name: Modified Shock Index
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [hemodynamics, shock, vital_signs]
summary: Calculates heart rate divided by mean arterial pressure.
source_url: https://pubmed.ncbi.nlm.nih.gov/?term=modified+shock+index+heart+rate+mean+arterial+pressure
citation: "Modified Shock Index: heart rate divided by mean arterial pressure."
rights_status: factual_formula
rights_note: MethodMesh returns the calculated index without imposing a universal clinical threshold.
questions:
  - id: heart_rate
    label: Heart rate
    type: integer
    unit: beats/min
    min: 20
    max: 250
  - id: systolic_bp
    label: Systolic blood pressure
    type: integer
    unit: mmHg
    min: 20
    max: 300
  - id: diastolic_bp
    label: Diastolic blood pressure
    type: integer
    unit: mmHg
    min: 10
    max: 200
derived:
  - id: map
    label: Mean arterial pressure
    expression: (systolic_bp + diastolic_bp * 2) / 3
scores:
  - id: modified_shock_index
    label: Modified Shock Index
    expression: heart_rate / map
tests:
  - name: example
    input_json: '{"heart_rate":100,"systolic_bp":120,"diastolic_bp":60}'
    expect_json: '{"modified_shock_index":1.25}'
"""

    const val PHQ2 = """
schema: methodmesh.clinical-instrument.v1
id: phq2
name: PHQ-2
version: 1.0.0
status: core
type: screening
category: mental_health
tags: [depression, screening, phq]
summary: Two-item depression screener covering the previous two weeks.
source_url: https://cde.nlm.nih.gov/formView?tinyId=XkZqYRlqKf
citation: "Kroenke K, Spitzer RL, Williams JBW. The Patient Health Questionnaire-2. Med Care. 2003;41:1284-1292."
rights_status: no_permission_required
rights_note: NIH CDE records state no permission is required to reproduce, translate, display or distribute the PHQ instruments.
questions:
  - id: little_interest
    label: Little interest or pleasure in doing things
    hint: Over the last 2 weeks, how often have you been bothered by this problem?
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: depressed_mood
    label: Feeling down, depressed, or hopeless
    hint: Over the last 2 weeks, how often have you been bothered by this problem?
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
scores:
  - id: phq2
    label: PHQ-2
    expression: little_interest + depressed_mood
classifications:
  - when: phq2 >= 3
    value: positive_screen
    label: Positive screening threshold - assess further
  - when: phq2 < 3
    value: below_screening_threshold
    label: Below usual PHQ-2 screening threshold
tests:
  - name: threshold
    input_json: '{"little_interest":"1","depressed_mood":"2"}'
    expect_json: '{"phq2":3,"classification":"positive_screen"}'
"""

    const val PHQ9 = """
schema: methodmesh.clinical-instrument.v1
id: phq9
name: PHQ-9
version: 1.0.0
status: core
type: screening
category: mental_health
tags: [depression, severity, phq]
summary: Nine-item depression symptom severity measure covering the previous two weeks.
source_url: https://www.nih.gov/node/19946
citation: "Kroenke K, Spitzer RL, Williams JBW. The PHQ-9. J Gen Intern Med. 2001;16:606-613."
rights_status: no_permission_required
rights_note: NIH lists PHQ-9 copyright as No; PHQ materials state no permission is required to reproduce, translate, display or distribute.
questions:
  - id: q1_interest
    label: Little interest or pleasure in doing things
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q2_depressed
    label: Feeling down, depressed, or hopeless
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q3_sleep
    label: Trouble falling or staying asleep, or sleeping too much
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q4_energy
    label: Feeling tired or having little energy
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q5_appetite
    label: Poor appetite or overeating
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q6_self_worth
    label: Feeling bad about yourself - or that you are a failure or have let yourself or your family down
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q7_concentration
    label: Trouble concentrating on things, such as reading the newspaper or watching television
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q8_movement
    label: Moving or speaking unusually slowly, or being unusually fidgety or restless
    hint: Over the last 2 weeks, to a degree other people could have noticed.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q9_self_harm
    label: Thoughts that you would be better off dead or of hurting yourself in some way
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
scores:
  - id: phq9
    label: PHQ-9
    expression: q1_interest + q2_depressed + q3_sleep + q4_energy + q5_appetite + q6_self_worth + q7_concentration + q8_movement + q9_self_harm
classifications:
  - when: phq9 <= 4
    value: minimal
    label: Minimal symptoms
  - when: phq9 >= 5 and phq9 <= 9
    value: mild
    label: Mild symptoms
  - when: phq9 >= 10 and phq9 <= 14
    value: moderate
    label: Moderate symptoms
  - when: phq9 >= 15 and phq9 <= 19
    value: moderately_severe
    label: Moderately severe symptoms
  - when: phq9 >= 20
    value: severe
    label: Severe symptoms
tests:
  - name: zero
    input_json: '{"q1_interest":"0","q2_depressed":"0","q3_sleep":"0","q4_energy":"0","q5_appetite":"0","q6_self_worth":"0","q7_concentration":"0","q8_movement":"0","q9_self_harm":"0"}'
    expect_json: '{"phq9":0,"classification":"minimal"}'
"""

    const val GAD2 = """
schema: methodmesh.clinical-instrument.v1
id: gad2
name: GAD-2
version: 1.0.0
status: core
type: screening
category: mental_health
tags: [anxiety, screening, gad]
summary: Two-item anxiety screener using the first two GAD-7 items.
source_url: https://www.ncbi.nlm.nih.gov/books/NBK92248/
citation: "Kroenke K, Spitzer RL, Williams JBW, Monahan PO, Lowe B. Anxiety disorders in primary care. Ann Intern Med. 2007."
rights_status: no_permission_required
rights_note: GAD-2/GAD-7 are public-domain instruments; no permission is required to reproduce, translate, display or distribute.
questions:
  - id: nervous
    label: Feeling nervous, anxious, or on edge
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: control_worry
    label: Not being able to stop or control worrying
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
scores:
  - id: gad2
    label: GAD-2
    expression: nervous + control_worry
classifications:
  - when: gad2 >= 3
    value: positive_screen
    label: Positive screening threshold - assess further
  - when: gad2 < 3
    value: below_screening_threshold
    label: Below usual GAD-2 screening threshold
tests:
  - name: threshold
    input_json: '{"nervous":"1","control_worry":"2"}'
    expect_json: '{"gad2":3,"classification":"positive_screen"}'
"""

    const val GAD7 = """
schema: methodmesh.clinical-instrument.v1
id: gad7
name: GAD-7
version: 1.0.0
status: core
type: screening
category: mental_health
tags: [anxiety, severity, gad]
summary: Seven-item anxiety symptom severity measure covering the previous two weeks.
source_url: https://www.nih.gov/node/19876
citation: "Spitzer RL, Kroenke K, Williams JBW, Lowe B. A brief measure for assessing generalized anxiety disorder. Arch Intern Med. 2006;166:1092-1097."
rights_status: public_domain
rights_note: NIH lists GAD-7 copyright as No; published reviews describe GAD-7/GAD-2 as public-domain instruments.
questions:
  - id: q1_nervous
    label: Feeling nervous, anxious, or on edge
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q2_control_worry
    label: Not being able to stop or control worrying
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q3_worrying
    label: Worrying too much about different things
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q4_relaxing
    label: Trouble relaxing
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q5_restless
    label: Being so restless that it is hard to sit still
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q6_irritable
    label: Becoming easily annoyed or irritable
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
  - id: q7_afraid
    label: Feeling afraid as if something awful might happen
    hint: Over the last 2 weeks.
    type: select_one
    choices: [0|Not at all, 1|Several days, 2|More than half the days, 3|Nearly every day]
scores:
  - id: gad7
    label: GAD-7
    expression: q1_nervous + q2_control_worry + q3_worrying + q4_relaxing + q5_restless + q6_irritable + q7_afraid
classifications:
  - when: gad7 <= 4
    value: minimal
    label: Minimal anxiety
  - when: gad7 >= 5 and gad7 <= 9
    value: mild
    label: Mild anxiety
  - when: gad7 >= 10 and gad7 <= 14
    value: moderate
    label: Moderate anxiety
  - when: gad7 >= 15
    value: severe
    label: Severe anxiety
tests:
  - name: moderate
    input_json: '{"q1_nervous":"2","q2_control_worry":"2","q3_worrying":"2","q4_relaxing":"1","q5_restless":"1","q6_irritable":"1","q7_afraid":"1"}'
    expect_json: '{"gad7":10,"classification":"moderate"}'
"""

    const val GLASGOW_BLATCHFORD = """
schema: methodmesh.clinical-instrument.v1
id: glasgow_blatchford
name: Glasgow-Blatchford Bleeding Score
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [gastrointestinal, bleeding, upper_gi, emergency]
summary: Pre-endoscopy risk score for adults presenting with acute upper gastrointestinal bleeding.
source_url: https://handbook.ggcmedicines.org.uk/guidelines/gastrointestinal-system/glasgow-blatchford-score/
citation: "Blatchford O, Murray WR, Blatchford M. A risk score to predict need for treatment for upper-gastrointestinal haemorrhage. Lancet. 2000;356:1318-1321. Criteria cross-checked against NHS Greater Glasgow and Clyde guidance."
rights_status: factual_definition
rights_note: Numerical thresholds and scoring criteria are factual clinical rules; MethodMesh uses concise field labels and cites the original study and NHS implementation guidance.
questions:
  - id: sex
    label: Sex used for haemoglobin threshold
    type: select_one
    choices: [male|Male, female|Female]
  - id: urea_mmol_l
    label: Blood urea
    type: decimal
    unit: mmol/L
    min: 0
    max: 100
  - id: haemoglobin_g_l
    label: Haemoglobin
    type: decimal
    unit: g/L
    min: 20
    max: 250
  - id: systolic_bp
    label: Systolic blood pressure
    type: integer
    unit: mmHg
    min: 20
    max: 300
  - id: pulse
    label: Pulse
    type: integer
    unit: beats/min
    min: 0
    max: 300
  - id: melaena
    label: Presentation with melaena?
    type: boolean
  - id: syncope
    label: Presentation with syncope?
    type: boolean
  - id: hepatic_disease
    label: Hepatic disease?
    type: boolean
  - id: cardiac_failure
    label: Cardiac failure?
    type: boolean
derived:
  - id: urea_points
    expression: (urea_mmol_l >= 6.5 and urea_mmol_l < 8) * 2 + (urea_mmol_l >= 8 and urea_mmol_l < 10) * 3 + (urea_mmol_l >= 10 and urea_mmol_l < 25) * 4 + (urea_mmol_l >= 25) * 6
  - id: haemoglobin_points
    expression: (sex == 'male' and haemoglobin_g_l >= 120 and haemoglobin_g_l < 130) + (sex == 'male' and haemoglobin_g_l >= 100 and haemoglobin_g_l < 120) * 3 + (sex == 'male' and haemoglobin_g_l < 100) * 6 + (sex == 'female' and haemoglobin_g_l >= 100 and haemoglobin_g_l < 120) + (sex == 'female' and haemoglobin_g_l < 100) * 6
  - id: bp_points
    expression: (systolic_bp >= 100 and systolic_bp < 110) + (systolic_bp >= 90 and systolic_bp < 100) * 2 + (systolic_bp < 90) * 3
  - id: other_points
    expression: (pulse >= 100) + melaena + syncope * 2 + hepatic_disease * 2 + cardiac_failure * 2
scores:
  - id: glasgow_blatchford
    label: Glasgow-Blatchford score
    expression: urea_points + haemoglobin_points + bp_points + other_points
classifications:
  - when: glasgow_blatchford == 0
    value: score_zero
    label: Glasgow-Blatchford score 0
  - when: glasgow_blatchford > 0
    value: score_above_zero
    label: Glasgow-Blatchford score above 0
tests:
  - name: all_zero
    input_json: '{"sex":"male","urea_mmol_l":5,"haemoglobin_g_l":140,"systolic_bp":120,"pulse":80,"melaena":false,"syncope":false,"hepatic_disease":false,"cardiac_failure":false}'
    expect_json: '{"glasgow_blatchford":0,"classification":"score_zero"}'
  - name: high_components
    input_json: '{"sex":"male","urea_mmol_l":26,"haemoglobin_g_l":90,"systolic_bp":85,"pulse":110,"melaena":true,"syncope":true,"hepatic_disease":true,"cardiac_failure":true}'
    expect_json: '{"glasgow_blatchford":23,"classification":"score_above_zero"}'
"""

    const val PRE_ENDOSCOPY_ROCKALL = """
schema: methodmesh.clinical-instrument.v1
id: pre_endoscopy_rockall
name: Pre-endoscopy Rockall score
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [gastrointestinal, bleeding, upper_gi, emergency]
summary: Clinical Rockall score using age, haemodynamic shock and major comorbidity before endoscopy.
source_url: https://portal.e-lfh.org.uk/AICC_Content/ACU_06_011/d/ELFH_Session/_/tab_670.html
citation: "Rockall TA, Logan RFA, Devlin HB, Northfield TC. Risk assessment after acute upper gastrointestinal haemorrhage. Gut. 1996;38:316-321. Clinical score cross-checked against NHS e-Learning for Healthcare."
rights_status: factual_definition
rights_note: Implements the numerical clinical Rockall criteria without reproducing explanatory source material.
questions:
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 16
    max: 130
  - id: pulse
    label: Pulse
    type: integer
    unit: beats/min
    min: 0
    max: 300
  - id: systolic_bp
    label: Systolic blood pressure
    type: integer
    unit: mmHg
    min: 20
    max: 300
  - id: comorbidity
    label: Major comorbidity category
    type: select_one
    choices: [0|No major comorbidity, 2|Heart failure / ischaemic heart disease / other major comorbidity, 3|Renal failure / liver failure / disseminated malignancy]
derived:
  - id: age_points
    expression: (age_years >= 60 and age_years < 80) + (age_years >= 80) * 2
  - id: shock_points
    expression: (systolic_bp < 100) * 2 + (systolic_bp >= 100 and pulse >= 100)
scores:
  - id: pre_endoscopy_rockall
    label: Pre-endoscopy Rockall
    expression: age_points + shock_points + comorbidity
classifications:
  - when: pre_endoscopy_rockall == 0
    value: score_zero
    label: Clinical Rockall score 0
  - when: pre_endoscopy_rockall > 0
    value: score_above_zero
    label: Clinical Rockall score above 0
tests:
  - name: zero
    input_json: '{"age_years":45,"pulse":80,"systolic_bp":120,"comorbidity":"0"}'
    expect_json: '{"pre_endoscopy_rockall":0,"classification":"score_zero"}'
  - name: maximum_clinical
    input_json: '{"age_years":85,"pulse":120,"systolic_bp":90,"comorbidity":"3"}'
    expect_json: '{"pre_endoscopy_rockall":7,"classification":"score_above_zero"}'
"""

    const val ROSIER = """
schema: methodmesh.clinical-instrument.v1
id: rosier
name: ROSIER stroke recognition scale
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [stroke, neurology, emergency, recognition]
summary: Seven-item Recognition of Stroke in the Emergency Room scale for patients with suspected acute stroke.
source_url: https://pubmed.ncbi.nlm.nih.gov/16239179/
citation: "Nor AM, Davis J, Sen B, et al. The Recognition of Stroke in the Emergency Room (ROSIER) scale. Lancet Neurol. 2005;4:727-734."
rights_status: factual_definition
rights_note: Implements the seven factual scoring criteria from the published ROSIER rule using concise labels.
questions:
  - id: loss_of_consciousness_or_syncope
    label: Loss of consciousness or syncope?
    type: boolean
  - id: seizure_activity
    label: Seizure activity?
    type: boolean
  - id: asymmetric_facial_weakness
    label: New asymmetric facial weakness?
    type: boolean
  - id: asymmetric_arm_weakness
    label: New asymmetric arm weakness?
    type: boolean
  - id: asymmetric_leg_weakness
    label: New asymmetric leg weakness?
    type: boolean
  - id: speech_disturbance
    label: New speech disturbance?
    type: boolean
  - id: visual_field_defect
    label: New visual field defect?
    type: boolean
scores:
  - id: rosier
    label: ROSIER
    expression: asymmetric_facial_weakness + asymmetric_arm_weakness + asymmetric_leg_weakness + speech_disturbance + visual_field_defect - loss_of_consciousness_or_syncope - seizure_activity
classifications:
  - when: rosier > 0
    value: stroke_likely
    label: Stroke likely by ROSIER threshold
  - when: rosier <= 0
    value: stroke_less_likely_not_excluded
    label: Stroke less likely by ROSIER threshold; not excluded
tests:
  - name: positive
    input_json: '{"loss_of_consciousness_or_syncope":false,"seizure_activity":false,"asymmetric_facial_weakness":true,"asymmetric_arm_weakness":true,"asymmetric_leg_weakness":false,"speech_disturbance":true,"visual_field_defect":false}'
    expect_json: '{"rosier":3,"classification":"stroke_likely"}'
  - name: mimic_pattern
    input_json: '{"loss_of_consciousness_or_syncope":true,"seizure_activity":true,"asymmetric_facial_weakness":false,"asymmetric_arm_weakness":false,"asymmetric_leg_weakness":false,"speech_disturbance":false,"visual_field_defect":false}'
    expect_json: '{"rosier":-2,"classification":"stroke_less_likely_not_excluded"}'
"""

    const val NEXUS_CSPINE = """
schema: methodmesh.clinical-instrument.v1
id: nexus_cspine
name: NEXUS C-spine low-risk criteria
version: 1.0.0
status: core
type: decision_rule
category: acute_bedside
tags: [trauma, cervical_spine, imaging, emergency]
summary: Five NEXUS low-risk criteria used in blunt trauma assessment for cervical-spine injury.
source_url: https://pubmed.ncbi.nlm.nih.gov/10891516/
citation: "Hoffman JR, Mower WR, Wolfson AB, Todd KH, Zucker MI. Validity of a set of clinical criteria to rule out injury to the cervical spine in patients with blunt trauma. N Engl J Med. 2000;343:94-99."
rights_status: factual_definition
rights_note: Implements the five factual NEXUS criteria from the validation study; does not reproduce source prose.
questions:
  - id: midline_cervical_tenderness
    label: Posterior midline cervical tenderness?
    type: boolean
  - id: focal_neurologic_deficit
    label: Focal neurologic deficit?
    type: boolean
  - id: altered_alertness
    label: Altered level of alertness?
    type: boolean
  - id: intoxication
    label: Evidence of intoxication?
    type: boolean
  - id: painful_distracting_injury
    label: Painful distracting injury?
    type: boolean
scores:
  - id: nexus_positive_criteria
    label: NEXUS positive criteria
    expression: midline_cervical_tenderness + focal_neurologic_deficit + altered_alertness + intoxication + painful_distracting_injury
classifications:
  - when: nexus_positive_criteria == 0
    value: all_low_risk_criteria_met
    label: All five NEXUS low-risk criteria met
  - when: nexus_positive_criteria > 0
    value: low_risk_criteria_not_met
    label: NEXUS low-risk criteria not all met
tests:
  - name: all_low_risk
    input_json: '{"midline_cervical_tenderness":false,"focal_neurologic_deficit":false,"altered_alertness":false,"intoxication":false,"painful_distracting_injury":false}'
    expect_json: '{"nexus_positive_criteria":0,"classification":"all_low_risk_criteria_met"}'
  - name: one_positive
    input_json: '{"midline_cervical_tenderness":true,"focal_neurologic_deficit":false,"altered_alertness":false,"intoxication":false,"painful_distracting_injury":false}'
    expect_json: '{"nexus_positive_criteria":1,"classification":"low_risk_criteria_not_met"}'
"""

    const val OTTAWA_ANKLE = """
schema: methodmesh.clinical-instrument.v1
id: ottawa_ankle
name: Ottawa ankle radiography rule
version: 1.0.0
status: core
type: decision_rule
category: acute_bedside
tags: [trauma, ankle, imaging, emergency]
summary: Ottawa criteria for deciding whether ankle radiography is indicated after acute ankle trauma.
source_url: https://aci.health.nsw.gov.au/ecat/appendices/ottawa-ankle-adult
citation: "Stiell IG, McKnight RD, Greenberg GH, et al. Implementation of the Ottawa Ankle Rules. JAMA. 1994;271:827-832. Criteria cross-checked against NSW Agency for Clinical Innovation."
rights_status: factual_definition
rights_note: Implements factual examination criteria; concise labels are MethodMesh wording.
questions:
  - id: malleolar_zone_pain
    label: Pain in the malleolar zone?
    type: boolean
  - id: lateral_malleolus_tenderness
    label: Bone tenderness at posterior edge or tip of lateral malleolus?
    hint: Distal 6 cm of posterior fibula or tip.
    type: boolean
  - id: medial_malleolus_tenderness
    label: Bone tenderness at posterior edge or tip of medial malleolus?
    hint: Distal 6 cm of posterior tibia or tip.
    type: boolean
  - id: unable_four_steps
    label: Unable to bear weight for four steps both immediately after injury and in the department?
    type: boolean
derived:
  - id: ankle_radiography_rule_positive
    expression: malleolar_zone_pain and (lateral_malleolus_tenderness or medial_malleolus_tenderness or unable_four_steps)
classifications:
  - when: ankle_radiography_rule_positive == true
    value: radiography_criteria_met
    label: Ottawa ankle radiography criteria met
  - when: ankle_radiography_rule_positive == false
    value: radiography_criteria_not_met
    label: Ottawa ankle radiography criteria not met
tests:
  - name: positive
    input_json: '{"malleolar_zone_pain":true,"lateral_malleolus_tenderness":true,"medial_malleolus_tenderness":false,"unable_four_steps":false}'
    expect_json: '{"ankle_radiography_rule_positive":true,"classification":"radiography_criteria_met"}'
  - name: no_zone_pain
    input_json: '{"malleolar_zone_pain":false,"lateral_malleolus_tenderness":true,"medial_malleolus_tenderness":false,"unable_four_steps":false}'
    expect_json: '{"ankle_radiography_rule_positive":false,"classification":"radiography_criteria_not_met"}'
"""

    const val OTTAWA_FOOT = """
schema: methodmesh.clinical-instrument.v1
id: ottawa_foot
name: Ottawa foot radiography rule
version: 1.0.0
status: core
type: decision_rule
category: acute_bedside
tags: [trauma, foot, imaging, emergency]
summary: Ottawa criteria for deciding whether foot radiography is indicated after acute midfoot trauma.
source_url: https://aci.health.nsw.gov.au/ecat/appendices/ottawa-ankle-adult
citation: "Stiell IG, McKnight RD, Greenberg GH, et al. Implementation of the Ottawa Ankle Rules. JAMA. 1994;271:827-832. Criteria cross-checked against NSW Agency for Clinical Innovation."
rights_status: factual_definition
rights_note: Implements factual examination criteria; concise labels are MethodMesh wording.
questions:
  - id: midfoot_zone_pain
    label: Pain in the midfoot zone?
    type: boolean
  - id: fifth_metatarsal_tenderness
    label: Bone tenderness at base of fifth metatarsal?
    type: boolean
  - id: navicular_tenderness
    label: Bone tenderness at navicular?
    type: boolean
  - id: unable_four_steps
    label: Unable to bear weight for four steps both immediately after injury and in the department?
    type: boolean
derived:
  - id: foot_radiography_rule_positive
    expression: midfoot_zone_pain and (fifth_metatarsal_tenderness or navicular_tenderness or unable_four_steps)
classifications:
  - when: foot_radiography_rule_positive == true
    value: radiography_criteria_met
    label: Ottawa foot radiography criteria met
  - when: foot_radiography_rule_positive == false
    value: radiography_criteria_not_met
    label: Ottawa foot radiography criteria not met
tests:
  - name: positive
    input_json: '{"midfoot_zone_pain":true,"fifth_metatarsal_tenderness":false,"navicular_tenderness":true,"unable_four_steps":false}'
    expect_json: '{"foot_radiography_rule_positive":true,"classification":"radiography_criteria_met"}'
  - name: negative
    input_json: '{"midfoot_zone_pain":true,"fifth_metatarsal_tenderness":false,"navicular_tenderness":false,"unable_four_steps":false}'
    expect_json: '{"foot_radiography_rule_positive":false,"classification":"radiography_criteria_not_met"}'
"""

    const val HEART = """
schema: methodmesh.clinical-instrument.v1
id: heart_score
name: HEART score
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [chest_pain, acute_coronary_syndrome, emergency, cardiac]
summary: Five-component HEART score for risk stratification of emergency-department patients with suspected acute coronary syndrome.
source_url: https://pubmed.ncbi.nlm.nih.gov/20802272/
citation: "Backus BE, Six AJ, Kelder JC, et al. Chest pain in the emergency room: a multicenter validation of the HEART Score. Crit Pathw Cardiol. 2010;9:164-169."
rights_status: factual_definition
rights_note: Implements the factual HEART scoring categories. History, ECG, risk-factor and troponin categories are entered explicitly so local clinical interpretation and assay reference limits remain visible.
questions:
  - id: history_points
    label: History category
    type: select_one
    choices: [0|Slightly suspicious, 1|Moderately suspicious, 2|Highly suspicious]
  - id: ecg_points
    label: ECG category
    type: select_one
    choices: [0|Normal, 1|Non-specific repolarisation disturbance, 2|Significant ST-segment deviation]
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 18
    max: 130
  - id: risk_factor_points
    label: Cardiovascular risk-factor category
    type: select_one
    choices: [0|No known risk factors, 1|One or two risk factors, 2|Three or more risk factors or known atherosclerotic disease]
  - id: troponin_points
    label: Initial troponin relative to local upper reference limit
    type: select_one
    choices: [0|At or below upper reference limit, 1|Above 1 to 3 times upper reference limit, 2|Above 3 times upper reference limit]
derived:
  - id: age_points
    expression: (age_years >= 45 and age_years < 65) + (age_years >= 65) * 2
scores:
  - id: heart
    label: HEART
    expression: history_points + ecg_points + age_points + risk_factor_points + troponin_points
classifications:
  - when: heart <= 3
    value: low_score_group
    label: HEART 0-3
  - when: heart >= 4 and heart <= 6
    value: intermediate_score_group
    label: HEART 4-6
  - when: heart >= 7
    value: high_score_group
    label: HEART 7-10
tests:
  - name: low
    input_json: '{"history_points":"0","ecg_points":"0","age_years":40,"risk_factor_points":"0","troponin_points":"0"}'
    expect_json: '{"heart":0,"classification":"low_score_group"}'
  - name: high
    input_json: '{"history_points":"2","ecg_points":"2","age_years":70,"risk_factor_points":"2","troponin_points":"2"}'
    expect_json: '{"heart":10,"classification":"high_score_group"}'
"""

    const val BISAP = """
schema: methodmesh.clinical-instrument.v1
id: bisap
name: BISAP acute pancreatitis score
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [pancreatitis, gastrointestinal, severity, emergency]
summary: Five-point Bedside Index for Severity in Acute Pancreatitis using findings from the first 24 hours.
source_url: https://pmc.ncbi.nlm.nih.gov/articles/PMC10167805/
citation: "Wu BU, Johannes RS, Sun X, et al. The early prediction of mortality in acute pancreatitis: a large population-based study. Gut. 2008;57:1698-1703. BISAP criteria cross-checked against contemporary reviews."
rights_status: factual_definition
rights_note: Implements the five factual BISAP criteria; SIRS is calculated from its four standard criterion groups.
questions:
  - id: bun_mg_dl
    label: Blood urea nitrogen
    type: decimal
    unit: mg/dL
    min: 0
    max: 200
  - id: impaired_mental_status
    label: Impaired mental status?
    hint: Commonly operationalised as Glasgow Coma Scale below 15.
    type: boolean
  - id: age_years
    label: Age
    type: integer
    unit: years
    min: 16
    max: 130
  - id: pleural_effusion
    label: Pleural effusion present on imaging?
    type: boolean
  - id: sirs_temperature
    label: SIRS temperature criterion present?
    hint: Temperature below 36 C or above 38 C.
    type: boolean
  - id: sirs_heart_rate
    label: SIRS heart-rate criterion present?
    hint: Heart rate above 90 beats/min.
    type: boolean
  - id: sirs_respiratory
    label: SIRS respiratory criterion present?
    hint: Respiratory rate above 20 breaths/min or PaCO2 below 32 mmHg.
    type: boolean
  - id: sirs_wbc
    label: SIRS white-cell criterion present?
    hint: WBC above 12,000/mm3, below 4,000/mm3, or more than 10% immature forms.
    type: boolean
derived:
  - id: sirs_count
    expression: sirs_temperature + sirs_heart_rate + sirs_respiratory + sirs_wbc
  - id: sirs_present
    expression: sirs_count >= 2
  - id: bun_criterion
    expression: bun_mg_dl > 25
  - id: age_criterion
    expression: age_years > 60
scores:
  - id: bisap
    label: BISAP
    expression: bun_criterion + impaired_mental_status + sirs_present + age_criterion + pleural_effusion
classifications:
  - when: bisap <= 2
    value: score_0_to_2
    label: BISAP 0-2
  - when: bisap >= 3
    value: score_3_to_5
    label: BISAP 3-5
tests:
  - name: zero
    input_json: '{"bun_mg_dl":20,"impaired_mental_status":false,"age_years":50,"pleural_effusion":false,"sirs_temperature":false,"sirs_heart_rate":false,"sirs_respiratory":false,"sirs_wbc":false}'
    expect_json: '{"bisap":0,"classification":"score_0_to_2"}'
  - name: five
    input_json: '{"bun_mg_dl":30,"impaired_mental_status":true,"age_years":70,"pleural_effusion":true,"sirs_temperature":true,"sirs_heart_rate":true,"sirs_respiratory":false,"sirs_wbc":false}'
    expect_json: '{"bisap":5,"classification":"score_3_to_5"}'
"""



    const val GCS = """
schema: methodmesh.clinical-instrument.v1
id: gcs
name: Glasgow Coma Scale
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [neurology, consciousness, trauma, emergency]
summary: Structured assessment of eye, verbal and motor responses. Report the component responses as well as the total when all components are testable.
source_url: https://www.glasgowcomascale.org/
citation: "Teasdale G, Jennett B. Assessment of coma and impaired consciousness: a practical scale. Lancet. 1974;2(7872):81-84. Modern terminology and NT handling cross-checked against glasgowcomascale.org."
rights_status: clinical_use_permitted
rights_note: "The official Glasgow Coma Scale permissions page states that GCS may be used for clinical care and clinical research at no cost with no licence required; copyright of Glasgow University should be acknowledged."
questions:
  - id: eye_response
    label: Eye opening
    hint: Record the highest response observed. Use NT only when a local factor prevents testing.
    type: select_one
    choices: [4|Spontaneous, 3|To sound, 2|To pressure, 1|None, NT|Not testable]
    not_testable_value: NT
  - id: verbal_response
    label: Verbal response
    hint: Record the highest response observed. Use NT when a factor interferes with communication, including an artificial airway where verbal response cannot be assessed.
    type: select_one
    choices: [5|Orientated, 4|Confused, 3|Words, 2|Sounds, 1|None, NT|Not testable]
    not_testable_value: NT
  - id: motor_response
    label: Best motor response
    hint: Record the highest response observed. Use NT when paralysis or another limiting factor prevents testing.
    type: select_one
    choices: [6|Obeys commands, 5|Localising, 4|Normal flexion, 3|Abnormal flexion, 2|Extension, 1|None, NT|Not testable]
    not_testable_value: NT
derived:
  - id: any_component_nt
    expression: eye_response == 'NT' or verbal_response == 'NT' or motor_response == 'NT'
scores:
  - id: gcs_total
    label: GCS total
    expression: eye_response + verbal_response + motor_response
    requires_testable: [eye_response, verbal_response, motor_response]
classifications:
  - when: any_component_nt == true
    value: total_not_reported
    label: Total not reported; one or more components are not testable
  - when: any_component_nt == false
    value: complete_gcs
    label: All three components testable
tests:
  - name: fully_alert
    input_json: '{"eye_response":"4","verbal_response":"5","motor_response":"6"}'
    expect_json: '{"gcs_total":15,"classification":"complete_gcs"}'
  - name: verbal_not_testable
    input_json: '{"eye_response":"4","verbal_response":"NT","motor_response":"6"}'
    expect_json: '{"gcs_total":"","classification":"total_not_reported"}'
  - name: lowest_testable
    input_json: '{"eye_response":"1","verbal_response":"1","motor_response":"1"}'
    expect_json: '{"gcs_total":3,"classification":"complete_gcs"}'
"""

    val yamlDefinitions: List<String> by lazy { listOf(
        QSOFA, CRB65, CURB65, AVPU, GCS, FOUR_AT, ADULT_BMI,
        PERC, WELLS_PE, WELLS_DVT, CHA2DS2_VASC, HAS_BLED, MODIFIED_CENTOR,
        SIRS, SHOCK_INDEX, MODIFIED_SHOCK_INDEX, PHQ2, PHQ9, GAD2, GAD7,
        GLASGOW_BLATCHFORD, PRE_ENDOSCOPY_ROCKALL, ROSIER, NEXUS_CSPINE,
        OTTAWA_ANKLE, OTTAWA_FOOT, HEART, BISAP
    ) }

    val definitions: List<ClinicalInstrumentDefinition> by lazy {
        yamlDefinitions.map { ClinicalInstrumentYaml.parse(it, InstrumentStatus.CORE) }
    }

}
