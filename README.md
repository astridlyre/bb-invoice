# hledger-invoice

A [Babashka](https://babashka.org/)-based CLI tool for generating professional HTML/PDF invoices with optional [hledger](https://hledger.org/) journal entry integration. Designed for freelancers and consultants who track time in hledger and need polished invoices.

## Features

- Professional two-page HTML invoice with print-optimized styling and custom fonts
- CSV timesheet import — parses hledger CSV exports, groups entries by date and account, and computes hours automatically
- PDF export via headless Chrome/Chromium
- hledger double-entry journal output (file or stdout)
- Multiple client profiles with per-client hourly rates and currency
- Configurable dates, invoice numbering, and currency
- Selmer-based HTML templating with logo support

## Requirements

- [Babashka](https://babashka.org/) (`bb`)
- Chrome or Chromium (optional, for PDF generation) — the script searches for `chromium`, `chromium-browser`, `google-chrome`, or `google-chrome-stable`
- [Just](https://github.com/casey/just) (optional, for task automation)

## Quick Start

```bash
# From a CSV timesheet (hledger export)
./invoice.clj --services input.csv --client ./clients/acme.edn --pdf

# From an EDN services file
./invoice.clj --services services.edn --client ./clients/acme.edn --pdf
```

## Configuration

### `me.edn` — Sender Information

Your personal/business details used on every invoice.

```edn
{:name "Jane Doe"
 :company "Acme Corp"
 :address {:street "123 Main St"
           :city "Springfield"
           :state "IL"
           :zip "62701"
           :country "US"}
 :email "jane@example.com"
 :phone "+1-555-0100"
 :bank {:name "First Bank"
        :routing "123456789"
        :account "987654321"}}
```

All fields are optional except `:name`. Bank details (`:iban`, `:swift` also supported) are displayed in a payment details section on the invoice when present.

### Services — Line Items

Services can be provided in three formats: **CSV**, **EDN**, or **JSON**.

**CSV (hledger timesheet export):**

The CSV format expects columns `date`, `description`, `account`, and `amount` (with `h` suffix for hours). Entries are grouped by date and account, and hours are multiplied by the client's hourly rate.

```csv
"txnidx","date","code","description","account","amount","total"
"1","2026-03-02","","all hands meeting","(acme:meetings)","0.50h","0.50h"
"2","2026-03-02","","tech requests","(acme:dev)","1.00h","1.00h"
```

When using CSV input, a client hourly rate is required (via `:rate` in the client file or `--client-rate`).

**EDN:**

```edn
[{:description "Web Development" :quantity 10 :amount 150.00}
 {:description "Consulting" :quantity 5 :amount 200.00}]
```

**JSON:**

```json
[{"description": "Web Development", "quantity": 10, "amount": 150.00}]
```

### `clients/*.edn` — Client Profiles

Client files live in the `clients/` directory. They can include an hourly `:rate` and `:currency` for CSV-based invoicing:

```edn
{:company "Acme"
 :rate 56
 :currency "$"}
```

Standard contact fields (`:name`, `:address`, `:email`, etc.) are also supported, using the same format as `me.edn`.

### `templates/invoice.html` — Invoice Template

The HTML template uses [Selmer](https://github.com/yogthos/Selmer) syntax. Available variables: `{{invoice-number}}`, `{{date}}`, `{{due-date}}`, `{{currency}}`, `{{total}}`, `{{sender.*}}`, `{{client.*}}`, and `{{services}}` (iterable).

## CLI Options

| Option | Description | Default |
|---|---|---|
| `--me` | Sender info file path | `me.edn` |
| `--client` | Client profile file path | — |
| `--client-name` | Client name (inline) | — |
| `--client-company` | Client company (inline) | — |
| `--client-email` | Client email (inline) | — |
| `--client-address` | Client address (inline) | — |
| `--services` | Services file (required; `.csv`, `.edn`, or `.json`) | — |
| `--client-rate` | Hourly rate (overrides client file `:rate`) | — |
| `--client-currency` | Currency symbol (overrides client file `:currency`) | — |
| `--invoice-number` | Invoice number | `INV-YYYY-MMDD` |
| `--date` | Invoice date | today |
| `--due-date` | Payment due date | 15 days from date |
| `--output` | Output HTML filename | `invoice-<number>.html` |
| `--output-dir` | Output directory | `./output` |
| `--pdf` | Generate PDF via headless Chrome | `false` |
| `--hledger` | Generate hledger entry (file path or `-` for stdout) | — |
| `--currency` | Currency symbol | `$` |
| `--help` | Show help | — |

## Usage Examples

**Generate an invoice from a CSV timesheet:**

```bash
./invoice.clj --services input.csv --client ./clients/acme.edn --pdf
```

**Generate an HTML invoice from EDN services:**

```bash
./invoice.clj --services services.edn --client ./clients/acme.edn
```

**Inline client details with custom dates:**

```bash
./invoice.clj \
  --services services.edn \
  --client-name "John Smith" \
  --client-company "Smith Inc" \
  --date 2026-01-15 \
  --due-date 2026-02-15
```

**Override hourly rate on the command line:**

```bash
./invoice.clj --services input.csv --client ./clients/acme.edn --client-rate 75
```

**Append hledger journal entry to a file:**

```bash
./invoice.clj --services input.csv --client ./clients/acme.edn --hledger journal.txt
```

**Print hledger entry to stdout:**

```bash
./invoice.clj --services services.edn --client-name "Client X" --hledger -
```

The hledger entry format:

```
2026-03-04 * Acme | Invoice #INV-2026-0304
    assets:receivable    $3640.00
    revenue:services
```

**Using the Justfile:**

```bash
just client acme input.csv
```

## Output

Generated files are placed in `./output/` by default:

- `invoice-INV-2026-0304.html` — the HTML invoice
- `invoice-INV-2026-0304.pdf` — the PDF (when `--pdf` is used)

## License

This project is licensed under the [MIT License](LICENSE).
