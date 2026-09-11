(ns housewaretrade.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    HousewareTradeAdvisor never dispatches household goods to a
    counterparty or settles an invoice the Consumer Product Safety
    Governor would reject, `:delivery/dispatch`/`:invoice/settle` NEVER
    auto-commit at any phase, `:order/intake` (no direct capital risk)
    MAY auto-commit when clean, and every decision (commit OR hold)
    leaves exactly one ledger fact.

  PLUS this vertical's own two defining proofs:
    - `childrens-product-certificate-missing` is genuinely TYPE-GATED
      on `:childrens-product?` (a general household good is a true
      NO-OP; a certified children's product dispatches cleanly; an
      UNcertified children's product HARD-holds) -- not a blanket
      certificate requirement.
    - `active-recall-unresolved` is a genuinely POST-HOC, RE-CHECKED
      flag (unlike the pre-shipment certificate above): the SAME
      order/SKU HARD-holds while `:recall-status :open`, and dispatches
      cleanly again once patched to `:recall-status :resolved` -- AND
      the same flag also blocks a LATER `:invoice/settle` if a recall
      newly opens after a clean dispatch, proving it is re-checked at
      BOTH actuation ops rather than a one-time pre-shipment gate."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [housewaretrade.store :as store]
            [housewaretrade.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through safety verify -> approve, leaving a safety
  assessment on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :safety/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order/intake :subject "ho-1"
                   :patch {:id "ho-1" :counterparty "Akita Household Goods Trading Co"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Akita Household Goods Trading Co" (:counterparty (store/household-order db "ho-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest safety-verify-always-needs-approval
  (testing "safety verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :safety/verify :subject "ho-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "ho-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a safety/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :safety/verify :subject "ho-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "ho-2")) "no assessment written"))))

(deftest dispatch-without-assessment-is-held
  (testing "delivery/dispatch before any safety verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :delivery/dispatch :subject "ho-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest credit-uncleared-is-held-and-unoverridable
  (testing "a counterparty whose credit has not been cleared -> HOLD, and never reaches request-approval -- the leasing collateral-coverage discipline applied to counterparty credit"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "ho-3")
          res (exec-op actor "t5" {:op :delivery/dispatch :subject "ho-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credit-uncleared} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest contract-missing-is-held-and-unoverridable
  (testing "an order with no contract-terms on file -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "ho-4")
          res (exec-op actor "t6" {:op :delivery/dispatch :subject "ho-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contract-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest counterparty-sanctions-flag-unresolved-is-held-and-unoverridable
  (testing "a counterparty that has not passed OFAC / equivalent sanctions screening -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "ho-5")
          res (exec-op actor "t7" {:op :delivery/dispatch :subject "ho-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest general-household-good-is-a-no-op-for-the-childrens-product-certificate-check
  (testing "ho-1 (general household good, NEITHER evidentiary sub-fact on file) dispatches CLEANLY -- proving genuine type-gating, not a blanket certificate requirement"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "ho-1")
          r1 (exec-op actor "t8" {:op :delivery/dispatch :subject "ho-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval on actuation grounds ONLY -- no HARD hold")
      (let [r2 (approve! actor "t8")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/household-order db "ho-1"))))
        (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record")))))

(deftest childrens-product-with-a-valid-certificate-dispatches-cleanly
  (testing "ho-6 (children's product, BOTH lead/phthalate-tested AND CPC on file) dispatches CLEANLY -- the check is satisfiable, not a trap"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "ho-6")
          r1 (exec-op actor "t9" {:op :delivery/dispatch :subject "ho-6"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval on actuation grounds ONLY -- no HARD hold")
      (let [r2 (approve! actor "t9")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/household-order db "ho-6"))))
        (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record")))))

(deftest childrens-product-certificate-missing-is-held-and-unoverridable
  (testing "ho-7 (children's product, lab-tested but NO Children's Product Certificate actually on file) -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "ho-7")
          res (exec-op actor "t10" {:op :delivery/dispatch :subject "ho-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:childrens-product-certificate-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest active-recall-unresolved-is-held-and-unoverridable-at-dispatch
  (testing "ho-8 (general household good, ACTIVE unresolved recall) -> HOLD at delivery/dispatch, and never reaches request-approval -- independently of the children's-product gate (ho-8 is NOT a children's product)"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "ho-8")
          res (exec-op actor "t11" {:op :delivery/dispatch :subject "ho-8"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:active-recall-unresolved} (-> (store/ledger db) last :basis)))
      (is (not (some #{:childrens-product-certificate-missing} (-> (store/ledger db) last :basis)))
          "ho-8 is not a children's product -- that check must not also fire")
      (is (empty? (store/dispatch-history db))))))

(deftest recall-resolution-allows-dispatch-on-the-same-sku
  (testing "the SAME order/SKU (ho-8): HOLD while :recall-status :open, then dispatches cleanly once patched to :recall-status :resolved via a plain :order/intake patch -- the post-hoc, re-checked lifecycle this vertical's own regulatory mechanic (CPSA §15(b)) calls for"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "ho-8")
          hold-res (exec-op actor "t12a" {:op :delivery/dispatch :subject "ho-8"} operator)]
      (is (= :hold (get-in hold-res [:state :disposition])))
      (is (some #{:active-recall-unresolved} (-> (store/ledger db) last :basis)))
      (testing "compliance officer records the recall's resolution -- ordinary low-stakes upsert, auto-commits"
        (let [patch-res (exec-op actor "t12patch" {:op :order/intake :subject "ho-8"
                                                    :patch {:id "ho-8" :recall-status :resolved}} operator)]
          (is (= :commit (get-in patch-res [:state :disposition])))
          (is (= :resolved (:recall-status (store/household-order db "ho-8"))))))
      (testing "delivery/dispatch on the SAME order now proceeds (escalates on actuation grounds only, then commits on approval)"
        (let [r1 (exec-op actor "t12b" {:op :delivery/dispatch :subject "ho-8"} operator)]
          (is (= :interrupted (:status r1)) "no HARD hold anymore -- only the ordinary actuation escalation")
          (let [r2 (approve! actor "t12b")]
            (is (= :commit (get-in r2 [:state :disposition])))
            (is (true? (:dispatched? (store/household-order db "ho-8"))))
            (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record")))))))

(deftest active-recall-unresolved-also-blocks-a-later-invoice-settle
  (testing "a recall that newly opens AFTER a clean dispatch also blocks :invoice/settle -- proving the flag is re-checked at BOTH actuation ops, the SAME span as counterparty-sanctions, unlike the dispatch-only certificate/credit/contract checks"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "ho-1")
          _ (exec-op actor "t13dispatch" {:op :delivery/dispatch :subject "ho-1"} operator)
          _ (approve! actor "t13dispatch")]
      (is (true? (:dispatched? (store/household-order db "ho-1"))) "dispatched cleanly before any recall existed")
      (let [patch-res (exec-op actor "t13patch" {:op :order/intake :subject "ho-1"
                                                  :patch {:id "ho-1" :recall-status :open}} operator)]
        (is (= :commit (get-in patch-res [:state :disposition]))))
      (let [res (exec-op actor "t13" {:op :invoice/settle :subject "ho-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
        (is (not= :interrupted (:status res)))
        (is (some #{:active-recall-unresolved} (-> (store/ledger db) last :basis)))
        (is (empty? (store/invoice-history db)))))))

(deftest delivery-dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, credit-cleared, contract-on-file, sanctions-screened order still ALWAYS interrupts for human approval -- :delivery/dispatch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t14pre" "ho-1")
          r1 (exec-op actor "t14" {:op :delivery/dispatch :subject "ho-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t14")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/household-order db "ho-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest invoice-settle-always-escalates-then-human-decides
  (testing "a clean, fully-verified, already-dispatched order still ALWAYS interrupts for human approval -- :invoice/settle is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t15pre" "ho-1")
          _ (exec-op actor "t15dispatch" {:op :delivery/dispatch :subject "ho-1"} operator)
          _ (approve! actor "t15dispatch")
          r1 (exec-op actor "t15" {:op :invoice/settle :subject "ho-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, invoice record drafted"
        (let [r2 (approve! actor "t15")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:invoiced? (store/household-order db "ho-1"))))
          (is (= 1 (count (store/invoice-history db))) "one draft invoice record"))))))

(deftest delivery-dispatch-double-dispatch-is-held
  (testing "dispatching the same household-order twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t16pre" "ho-1")
          _ (exec-op actor "t16a" {:op :delivery/dispatch :subject "ho-1"} operator)
          _ (approve! actor "t16a")
          res (exec-op actor "t16" {:op :delivery/dispatch :subject "ho-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest invoice-settle-double-invoice-is-held
  (testing "settling the same household-order's invoice twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t17pre" "ho-1")
          _ (exec-op actor "t17dispatch" {:op :delivery/dispatch :subject "ho-1"} operator)
          _ (approve! actor "t17dispatch")
          _ (exec-op actor "t17a" {:op :invoice/settle :subject "ho-1"} operator)
          _ (approve! actor "t17a")
          res (exec-op actor "t17" {:op :invoice/settle :subject "ho-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-invoiced} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/invoice-history db))) "still only the one earlier invoice"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order/intake :subject "ho-1"
                          :patch {:id "ho-1" :counterparty "Akita Household Goods Trading Co"}} operator)
      (exec-op actor "b" {:op :safety/verify :subject "ho-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
