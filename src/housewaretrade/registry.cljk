(ns housewaretrade.registry
  "Pure-function household-goods-dispatch + household-goods-invoice
  record construction -- an append-only household-goods-wholesale
  book-of-record draft.

  Like the fuel-wholesale / ag-machinery-wholesale siblings' own
  registries, this household-goods-wholesale vertical's Consumer
  Product Safety Governor needs NO registry range-check functions at
  all: its domain checks (credit-uncleared, contract-missing,
  children's-product-certificate-missing, active-recall-unresolved,
  counterparty-sanctions-flag-unresolved) are direct entity boolean/enum
  reads in `housewaretrade.governor`, off dedicated `:credit-cleared?` /
  `:contract-terms` / `:childrens-product-certificate-on-file?` /
  `:recall-status` / `:sanctions-screened?` facts on the
  `household-order` record. So this namespace is RECORD CONSTRUCTION
  ONLY -- no pure range checks to host here.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a household-goods-dispatch or
  household-goods-invoice record -- every operator/jurisdiction assigns
  its own reference format. This namespace does NOT invent one beyond a
  jurisdiction-scoped sequence number; it validates the record's
  required fields, the same honest, non-fabricating discipline
  `housewaretrade.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real distribution-center/ERP/billing system. It builds the
  RECORD an operator would keep, not the act of dispatching real
  household goods from the wholesale distribution center or settling a
  real invoice itself (that is `housewaretrade.operation`'s
  `:delivery/dispatch`/`:invoice/settle`, always human-gated -- see
  README `Actuation`)."
  (:require [kotoba.lang.text :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-dispatch-record
  "Validate + construct the HOUSEHOLD-GOODS-DISPATCH registration DRAFT
  -- the operator's own legal act of dispatching real household goods
  from the wholesale distribution center to a counterparty. Pure
  function -- does not touch any real distribution-center/ERP system; it
  builds the RECORD an operator would keep. `housewaretrade.governor`
  independently re-verifies the counterparty's credit-clearance,
  contract-on-file, Children's Product Certificate (when applicable),
  active-recall status and evidence-completeness ground truth, and
  blocks a double-dispatch of the same household-order, before this is
  ever allowed to commit."
  [household-order-id jurisdiction sequence]
  (when-not (and household-order-id (not= household-order-id ""))
    (throw (ex-info "household-goods-dispatch: household_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "household-goods-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "household-goods-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper jurisdiction) "-DISPATCH-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "household-goods-dispatch-draft"
                "household_order_id" household-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "HouseholdGoodsDispatch" dispatch-number dispatch-number)}))

(defn register-invoice-record
  "Validate + construct the HOUSEHOLD-GOODS-INVOICE registration DRAFT
  -- the operator's own legal act of settling a real household-goods
  invoice (the money side of a wholesale trade, custody/financial
  transfer). Pure function -- does not touch any real billing or
  accounts-receivable system; it builds the RECORD an operator would
  keep. `housewaretrade.governor` independently re-verifies the
  sanctions-screening, active-recall status and evidence-completeness
  ground truth, and blocks a double-invoice of the same household-order,
  before this is ever allowed to commit."
  [household-order-id jurisdiction sequence]
  (when-not (and household-order-id (not= household-order-id ""))
    (throw (ex-info "household-goods-invoice: household_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "household-goods-invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "household-goods-invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper jurisdiction) "-INVOICE-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "household-goods-invoice-draft"
                "household_order_id" household-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "HouseholdGoodsInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
