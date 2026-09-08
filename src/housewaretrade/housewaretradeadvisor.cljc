(ns housewaretrade.housewaretradeadvisor
  "HousewareTradeAdvisor client -- the *contained intelligence node* for
  the household-goods-wholesale actor.

  It normalizes household-order intake, drafts a per-jurisdiction
  counterparty-diligence / sanctions evidence checklist, drafts the
  physical household-goods-dispatch action, and drafts the invoice-
  settlement action. CRITICAL: it is a smart-but-untrusted advisor. It
  returns a *proposal* (with a rationale + the fields it cited), never a
  committed record or a real dispatch/settlement. Every output is
  censored downstream by `housewaretrade.governor` before anything
  touches the SSoT, and `:delivery/dispatch`/`:invoice/settle` proposals
  NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :delivery/dispatch | :invoice/settle | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [kotoba.lang.text :as str]
            [housewaretrade.facts :as facts]
            [housewaretrade.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the order-id, counterparty, jurisdiction, product-
  category, or any physical/commercial value. High confidence, low
  stakes. Also the mechanism a compliance officer uses to record a
  recall's resolution (`:recall-status :resolved`) once CPSC has closed
  it -- see `housewaretrade.governor` namespace docstring."
  [_db {:keys [patch]}]
  {:summary    (str "家庭用品卸売オーダー記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :order/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-safety
  "Per-jurisdiction counterparty-diligence / sanctions evidence
  checklist draft. `:no-spec?` injects the failure mode we must defend
  against: proposing a checklist for a jurisdiction with NO official
  spec-basis in `housewaretrade.facts` -- the Consumer Product Safety
  Governor must reject this (never invent a jurisdiction's
  requirements). This op deliberately does NOT assess the children's-
  product-certificate or active-recall status -- those are
  `housewaretrade.governor`'s own dedicated HARD checks, re-verified
  directly off the order's own `:childrens-product-certificate-on-file?`
  /`:recall-status` facts at `:delivery/dispatch`/`:invoice/settle`
  time."
  [db {:keys [subject no-spec?]}]
  (let [ho (store/household-order db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction ho))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "housewaretrade.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :safety-assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :safety-assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-dispatch
  "Draft the actual HOUSEHOLD-GOODS-DISPATCH action -- dispatching real
  physical household goods from the wholesale distribution center to a
  counterparty. ALWAYS `:stake :delivery/dispatch` -- this is a
  REAL-WORLD act (a warehouse robot stages the carton/pallet and a
  human operator releases the load, or a human operator handles it
  directly), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`housewaretrade.phase`); the governor also always escalates on
  `:delivery/dispatch`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [ho (store/household-order db subject)
        credit-ok? (and ho (true? (:credit-cleared? ho)))
        contract-ok? (and ho (some? (:contract-terms ho))
                          (not= "" (:contract-terms ho)))
        cpc-ok? (and ho (or (not (true? (:childrens-product? ho)))
                             (and (true? (:lead-phthalate-tested? ho))
                                  (true? (:childrens-product-certificate-on-file? ho)))))
        recall-ok? (and ho (not= :open (:recall-status ho)))
        sanctions-ok? (and ho (true? (:sanctions-screened? ho)))]
    {:summary    (str subject " 向け出荷提案"
                      (when ho (str " (counterparty=" (:counterparty ho) ")")))
     :rationale  (if ho
                   (str "credit-cleared?=" credit-ok?
                        " contract-on-file?=" contract-ok?
                        " childrens-product-certificate-ok?=" cpc-ok?
                        " recall-ok?=" recall-ok?
                        " sanctions-screened?=" sanctions-ok?)
                   "household-orderが見つかりません")
     :cites      (if ho [subject] [])
     :effect     :order/mark-dispatched
     :value      {:household-order-id subject}
     :stake      :delivery/dispatch
     :confidence (if (and credit-ok? contract-ok? cpc-ok? recall-ok? sanctions-ok?) 0.9 0.3)}))

(defn- propose-invoice
  "Draft the actual INVOICE-SETTLEMENT action -- settling a real
  household-goods invoice (the money side of a wholesale trade, custody/
  financial transfer). ALWAYS `:stake :invoice/settle` -- this is a
  REAL-WORLD act (real money moves between counterparty and wholesaler),
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set
  (`housewaretrade.phase`); the governor also always escalates on
  `:invoice/settle`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [ho (store/household-order db subject)
        dispatched? (and ho (:dispatched? ho))
        recall-ok? (and ho (not= :open (:recall-status ho)))
        sanctions-ok? (and ho (true? (:sanctions-screened? ho)))]
    {:summary    (str subject " 向け請求提案"
                      (when ho (str " (counterparty=" (:counterparty ho) ")")))
     :rationale  (if ho
                   (str "dispatched?=" dispatched?
                        " recall-ok?=" recall-ok?
                        " sanctions-screened?=" sanctions-ok?)
                   "household-orderが見つかりません")
     :cites      (if ho [subject] [])
     :effect     :order/mark-invoiced
     :value      {:household-order-id subject}
     :stake      :invoice/settle
     :confidence (if (and dispatched? recall-ok? sanctions-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order/intake       (normalize-intake db request)
    :safety/verify      (verify-safety db request)
    :delivery/dispatch  (propose-dispatch db request)
    :invoice/settle     (propose-invoice db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは家庭用品卸売事業者の出荷・請求エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:safety-assessment/set|:order/mark-dispatched|"
       ":order/mark-invoiced) "
       ":stake(:delivery/dispatch か :invoice/settle か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の消費者製品安全・制裁要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "取引先信用審査・契約有無・児童用製品証明書(CPC)・リコール状況・"
       "制裁スクリーニングの状態を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :safety/verify      {:household-order (store/household-order st subject)}
    :delivery/dispatch  {:household-order (store/household-order st subject)}
    :invoice/settle     {:household-order (store/household-order st subject)}
    {:household-order (store/household-order st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Consumer Product Safety
  Governor escalates/holds -- an LLM hiccup can never auto-dispatch
  household goods or auto-settle an invoice."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :housewaretradeadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
