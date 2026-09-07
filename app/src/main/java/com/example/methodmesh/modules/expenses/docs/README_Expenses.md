# Expenses

**Status:** Development  
**Version:** 0.4.0  
**Module ID:** `expenses`

## Capabilities

The module exposes one public MethodMesh capability:

- `expenses.manage` — create, edit, switch and delete ledgers; record expenses; inspect the complete transaction list and FX calculations; void incorrect entries; attach receipts/invoices; export, share and save copies; and return the selected ledger summary to the caller.

All ledger operations remain internal repository operations. They are not exposed as separate MethodMesh capabilities.

## Android intent

Use the standard MethodMesh execution intent:

`com.example.methodmesh.EXECUTE_METHOD`

with:

`method_id=expenses.manage`

When the user presses **Done**, the capability returns the currently selected ledger summary.

## Inputs

`expenses.manage` has no required capability-specific inputs.

Ledger creation, settings, currencies and expense entry are managed interactively inside the capability.

## Outputs

Returned on **Done**:

- `expenses_manage_status`
- `expenses_manage_ledger_id`
- `expenses_manage_ledger_name`
- `expenses_manage_total_home`
- `expenses_manage_home_currency`
- `expenses_manage_expense_count`
- `expenses_manage_error`

The persistent ledger remains the authoritative record.

## ODK example

`docs/example_odk_Expenses.xlsx` invokes only `expenses.manage`.

The user completes expense work inside MethodMesh and the selected ledger summary is returned when **Done** is pressed.

## Ledger management

The manager supports:

- new ledger;
- switch ledger;
- edit ledger name;
- edit home currency;
- add/remove foreign currencies by editing the rate list;
- change exchange rates;
- delete ledger with explicit confirmation.

Deletion removes the ledger, all transactions, its audit file and attachments. The confirmation dialog states that deletion is irreversible.

## Exchange-rate behaviour

Rates use:

`1 home-currency unit = X foreign-currency units`

Example:

`USD=1.32`

in a GBP ledger means:

`1 GBP = 1.32 USD`

and:

`USD 24.50 / 1.32 = GBP 18.560606...`

### Live recalculation

Ledger exchange rates are editable settings.

When the home currency or an exchange rate changes, every stored transaction is recalculated from its preserved:

- original amount;
- original currency.

The transaction's applied rate, home currency and exact home amount are updated. Running totals and category totals therefore refresh immediately from the recalculated ledger.

The audit event `ledger_settings_updated` records the old/new ledger settings and old/new converted value for each transaction.

If an existing transaction uses a currency that is not the new home currency, the edited settings must include a rate for that currency.

## Attachments

An expense can contain zero, one or multiple attached files.

Supported acquisition paths in the native UI:

- **Camera** — capture a single image.
- **Scan document** — open the existing ML Kit document scanner and produce a PDF, suitable for multi-page invoices and paper receipts.
- **Pick file(s)** — Android document picker accepting image files and PDFs, including multiple selections.
- **No receipt** — explicit evidence status when no attachment exists.

The module copies selected/scanned files into app-private ledger storage when the expense is recorded.

## Attachment filenames

Stored files are named from the ledger row, category and converted home-currency amount.

One attachment:

```text
003_subsistence_18.56GBP.pdf
```

Multiple attachments on the same expense:

```text
003_subsistence_18.56GBP_01.pdf
003_subsistence_18.56GBP_02.jpg
```

The real source extension is retained (`pdf`, `jpg`, `png`, `webp`, etc.).

If an exchange-rate/home-currency change recalculates the converted amount, existing attachment files are renamed in the same operation so their filenames remain consistent with the ledger.

## Persistence

```text
filesDir/
└── methodmesh/
    └── expenses/
        └── <ledger UUID>/
            ├── ledger.json
            ├── events.jsonl
            └── receipts/
```

`ledger.json` is authoritative current state.

`events.jsonl` is append-only audit history.

Expense additions, voids and ledger-setting changes are persisted immediately.

## Calculations

All monetary calculations use `BigDecimal`.

No UI component maintains a second total.

The single total path is:

`ExpenseLedgerCalculator.summarize(ledger)`

and it derives totals only from active entries.

## Export, share and save

### Save / download

Uses Android's document picker.

Formats:

- CSV
- JSON
- summary text
- complete ZIP

### Share

Uses Android's share sheet for:

- summary text
- CSV
- complete ZIP

### Complete ZIP

```text
expenses.csv
ledger.json
summary.txt
events.jsonl
receipts/
    ...
```

Voided entries remain in exports with `status=void` but are excluded from totals.

## Offline behaviour

Core expense recording, calculations, attachment copying, ML Kit document-scanner handoff, persistence and export preparation do not depend on an internet FX service.

No OCR is performed by the Expenses capability.

## Limitations

- Development status.
- No automatic internet FX lookup.
- One public capability only: `expenses.manage`.
- Expense voiding is exposed; editing an existing expense is still internal repository functionality rather than a native UI action.
- No automatic migration from the old v0.1 split `trip.json` / `expenses.json` layout.
