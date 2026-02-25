#!/usr/bin/env bb

(require '[babashka.cli :as cli]
         '[selmer.parser :as selmer]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[babashka.process :as proc]
         '[babashka.fs :as fs]
         '[clojure.string :as str])

;; --- CLI spec ---

(def cli-spec
  {:me          {:desc "Sender info file (default: me.edn)"
                 :default "me.edn"}
   :client      {:desc "Client info from file (same format as me.edn)"}
   :client-name    {:desc "Client name (inline)"}
   :client-company {:desc "Client company (inline)"}
   :client-email   {:desc "Client email (inline)"}
   :client-address {:desc "Client address (single string, inline)"}
   :services    {:desc "Services file (required, edn or json)"}
   :invoice-number {:desc "Invoice number (default: INV-YYYY-MMDD)"}
   :date        {:desc "Invoice date (default: today)"}
   :due-date    {:desc "Due date (default: 30 days from date)"}
   :output      {:desc "Output HTML filename (default: invoice-<number>.html)"}
   :output-dir  {:desc "Output directory (default: ./output)"
                 :default "./output"}
   :pdf         {:desc "Also generate PDF via headless Chrome"
                 :coerce :boolean}
   :hledger     {:desc "Output hledger journal entry to file (or - for stdout)"}
   :currency    {:desc "Currency symbol (default: $)"
                 :default "$"}
   :help        {:desc "Show help"
                 :coerce :boolean}})

;; --- Help ---

(defn print-help []
  (println "Usage: bb invoice.clj [options]")
  (println)
  (println "Options:")
  (doseq [[k {:keys [desc default]}] (sort-by key cli-spec)]
    (let [flag (str "  --" (name k))
          pad  (apply str (repeat (max 1 (- 26 (count flag))) " "))]
      (print flag pad desc)
      (when default (print (str " (default: " default ")")))
      (println))))

;; --- File loading ---

(defn load-data-file [path]
  (let [content (slurp path)
        ext     (str/lower-case (str (fs/extension path)))]
    (case ext
      "json" (json/parse-string content true)
      "edn"  (edn/read-string content)
      (edn/read-string content))))

;; --- Contact resolution ---

(defn resolve-client [opts]
  (cond
    (:client opts)
    (load-data-file (:client opts))

    (:client-name opts)
    (cond-> {:name (:client-name opts)}
      (:client-company opts) (assoc :company (:client-company opts))
      (:client-email opts)   (assoc :email (:client-email opts))
      (:client-address opts) (assoc :address (:client-address opts)))

    :else
    (do (println "Error: must provide --client or --client-name")
        (System/exit 1))))

;; --- Date helpers ---

(defn today []
  (str (java.time.LocalDate/now)))

(defn plus-days [date-str days]
  (str (.plusDays (java.time.LocalDate/parse date-str) days)))

(defn default-invoice-number [date-str]
  (let [d (java.time.LocalDate/parse date-str)]
    (format "INV-%d-%02d%02d" (.getYear d) (.getMonthValue d) (.getDayOfMonth d))))

;; --- Invoice computation ---

(defn compute-services [services]
  (mapv (fn [s]
          (assoc s
                 :amount   (format "%.2f" (double (:amount s)))
                 :line-total (format "%.2f" (* (double (:quantity s)) (double (:amount s))))))
        services))

(defn compute-total [services]
  (reduce + 0.0 (map #(* (double (:quantity %)) (double (:amount %))) services)))

;; --- HTML generation ---

(defn generate-html [data]
  (let [script-dir (str (fs/parent (fs/absolutize *file*)))
        template   (slurp (str script-dir "/templates/invoice.html"))]
    (selmer/render template data)))

;; --- PDF generation ---

(def chrome-candidates
  ["chromium" "chromium-browser" "google-chrome" "google-chrome-stable"])

(defn find-chrome []
  (first (filter #(some-> (fs/which %) str) chrome-candidates)))

(defn generate-pdf [html-path pdf-path]
  (if-let [chrome (find-chrome)]
    (let [abs-html (str (fs/absolutize html-path))
          result   (proc/sh [chrome
                             "--headless"
                             "--disable-gpu"
                             "--no-sandbox"
                             "--no-pdf-header-footer"
                             (str "--print-to-pdf=" pdf-path)
                             (str "file://" abs-html)])]
      (if (zero? (:exit result))
        (println "PDF generated:" pdf-path)
        (do (println "Error generating PDF:")
            (println (:err result))
            (System/exit 1))))
    (do (println "Error: no Chrome/Chromium found on PATH")
        (println "Tried:" (str/join ", " chrome-candidates))
        (System/exit 1))))

;; --- hledger entry ---

(defn format-hledger-entry [{:keys [date client-name invoice-number currency total]}]
  (let [amount-str (format "%s%.2f" currency total)]
    (str "\n"
         date " * " client-name " | Invoice #" invoice-number "\n"
         "    assets:receivable    " amount-str "\n"
         "    revenue:services\n")))

(defn output-hledger [dest entry]
  (if (or (= dest "-") (= dest true))
    (print entry)
    (do (spit dest entry :append true)
        (println "hledger entry appended to:" dest))))

;; --- Main ---

(defn -main [args]
  (let [opts (cli/parse-opts args {:spec cli-spec
                                   :error-fn (fn [{:keys [msg]}]
                                               (println "Error:" msg)
                                               (print-help)
                                               (System/exit 1))})]
    (when (:help opts)
      (print-help)
      (System/exit 0))

    (when-not (:services opts)
      (println "Error: --services is required")
      (print-help)
      (System/exit 1))

    (let [date           (or (:date opts) (today))
          due-date       (or (:due-date opts) (plus-days date 30))
          invoice-number (or (:invoice-number opts) (default-invoice-number date))
          sender         (when (fs/exists? (:me opts))
                           (load-data-file (:me opts)))
          client         (resolve-client opts)
          services-raw   (load-data-file (:services opts))
          services       (compute-services services-raw)
          total          (compute-total services-raw)
          total-str      (format "%.2f" total)
          currency       (:currency opts)
          output-dir     (:output-dir opts)
          _              (fs/create-dirs output-dir)
          output-html    (str (fs/path output-dir
                                       (or (:output opts) (str "invoice-" invoice-number ".html"))))
          template-data  {:sender         (or sender {})
                          :client         client
                          :services       services
                          :invoice-number invoice-number
                          :date           date
                          :due-date       due-date
                          :total          total-str
                          :currency       currency}
          html           (generate-html template-data)]

      ;; Write HTML
      (spit output-html html)
      (println "Invoice generated:" output-html)

      ;; PDF
      (when (:pdf opts)
        (let [pdf-path (str/replace output-html #"\.html$" ".pdf")]
          (generate-pdf output-html pdf-path)))

      ;; hledger
      (when (:hledger opts)
        (let [client-name (or (:company client) (:name client))
              entry       (format-hledger-entry {:date           date
                                                 :client-name    client-name
                                                 :invoice-number invoice-number
                                                 :currency       currency
                                                 :total          total})]
          (output-hledger (:hledger opts) entry))))))

(-main *command-line-args*)
