# hledger-invoice

A [Babashka](https://babashka.org/)-based CLI tool for generating professional HTML/PDF invoices with optional [hledger](https://hledger.org/) journal entry integration.

## Features

- Professional HTML invoice generation with print-friendly styling
- PDF export via headless Chrome/Chromium
- hledger double-entry journal output (file or stdout)
- Multiple client profiles
- Configurable currency, dates, and invoice numbering
- Selmer-based HTML templating

## Requirements

- [Babashka](https://babashka.org/) (`bb`)
- Chrome or Chromium (optional, for PDF generation) — the script searches for `chromium`, `chromium-browser`, `google-chrome`, or `google-chrome-stable`
- [Just](https://github.com/casey/just) (optional, for task automation)

## Quick Start

```bash
./invoice.clj --services services.edn --client ./clients/streamily.edn --pdf
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

### `services.edn` — Line Items

A list of services/products to invoice. Each item requires `:description`, `:quantity`, and `:amount` (unit price).

```edn
[{:description "Web Development" :quantity 10 :amount 150.00}
 {:description "Consulting" :quantity 5 :amount 200.00}]
```

This file can also be JSON format.

### `clients/*.edn` — Client Profiles

Client files live in the `clients/` directory and use the same format as `me.edn`:

```edn
{:name "Alex Smith"
 :company "Streamily"
 :address {:street "456 Oak Ave" :city "Portland" :state "OR" :zip "97201"}
 :email "alex@streamily.com"}
```

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
| `--services` | Services file (required) | — |
| `--invoice-number` | Invoice number | `INV-YYYY-MMDD` |
| `--date` | Invoice date | today |
| `--due-date` | Payment due date | 30 days from date |
| `--output` | Output HTML filename | `invoice-<number>.html` |
| `--output-dir` | Output directory | `./output` |
| `--pdf` | Generate PDF via headless Chrome | `false` |
| `--hledger` | Generate hledger entry (file path or `-` for stdout) | — |
| `--currency` | Currency symbol | `$` |
| `--help` | Show help | — |

## Usage Examples

**Generate an HTML invoice with a client file:**

```bash
./invoice.clj --services services.edn --client ./clients/streamily.edn
```

**Generate HTML + PDF:**

```bash
./invoice.clj --services services.edn --client ./clients/streamily.edn --pdf
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

**Append hledger journal entry to a file:**

```bash
./invoice.clj --services services.edn --client ./clients/alex.edn --hledger journal.txt
```

**Print hledger entry to stdout:**

```bash
./invoice.clj --services services.edn --client-name "Client X" --hledger -
```

The hledger entry format:

```
2026-02-24 * Streamily | Invoice #INV-2026-0224
    assets:receivable    $2500.00
    revenue:services
```

**Using the Justfile:**

```bash
just client streamily    # uses ./clients/streamily.edn
```

## Output

Generated files are placed in `./output/` by default:

- `invoice-INV-2026-0224.html` — the HTML invoice
- `invoice-INV-2026-0224.pdf` — the PDF (when `--pdf` is used)

## License

This project is licensed under the [MIT License](LICENSE).
